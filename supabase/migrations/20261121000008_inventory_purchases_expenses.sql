-- =====================================================================
-- Swad Sangam ERP — inventory, recipes, suppliers, purchases,
-- customer credit (khata) and expenses. Every money movement posts a
-- balanced journal entry automatically.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Units
-- ---------------------------------------------------------------------
create or replace function private.convert_qty(p_qty numeric, p_from text, p_to text) returns numeric
language plpgsql immutable as $$
begin
  if p_from = p_to then return p_qty; end if;
  if p_from = 'g'  and p_to = 'kg' then return p_qty / 1000; end if;
  if p_from = 'kg' and p_to = 'g'  then return p_qty * 1000; end if;
  if p_from = 'ml' and p_to = 'l'  then return p_qty / 1000; end if;
  if p_from = 'l'  and p_to = 'ml' then return p_qty * 1000; end if;
  raise exception 'UNIT_MISMATCH: cannot convert % to %', p_from, p_to using errcode = '22023';
end $$;

-- ---------------------------------------------------------------------
-- Stock items, movements (weighted-average valuation), recipes
-- ---------------------------------------------------------------------
create table public.stock_items (
  id          bigint generated always as identity primary key,
  name        text not null unique,
  category    text not null default 'INGREDIENT' check (category in ('INGREDIENT','PACKAGING','BEVERAGE','CLEANING','OTHER')),
  unit        text not null check (unit in ('kg','g','l','ml','pc')),
  min_level   numeric(14,3) not null default 0,
  qty         numeric(14,3) not null default 0,
  avg_cost    numeric(14,4) not null default 0,     -- paise per unit
  is_active   boolean not null default true,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now()
);
create trigger trg_stock_items_touch before update on public.stock_items for each row execute function private.touch_updated_at();

create table public.stock_movements (
  id          bigint generated always as identity primary key,
  item_id     bigint not null references public.stock_items(id),
  type        text not null check (type in ('OPENING','RECEIPT','CONSUMPTION','WASTAGE','COUNT','RETURN')),
  qty         numeric(14,3) not null,                -- + in, − out (in the item's unit)
  unit_cost   numeric(14,4) not null default 0,
  value_paise bigint not null default 0,             -- signed
  ref_type    text,
  ref_id      text,
  batch_no    text,
  expiry_date date,
  note        text,
  created_by  uuid,
  created_at  timestamptz not null default now()
);
create index stock_movements_item_idx on public.stock_movements(item_id, created_at);
create unique index stock_consumption_once on public.stock_movements(item_id, ref_type, ref_id) where type = 'CONSUMPTION';
create trigger trg_stock_movements_immutable before update or delete on public.stock_movements
  for each row execute function private.block_change();

create table public.recipe_lines (
  id            bigint generated always as identity primary key,
  menu_item_id  text not null references public.menu_items(id) on delete cascade,
  stock_item_id bigint not null references public.stock_items(id),
  qty           numeric(14,4) not null check (qty > 0),
  unit          text not null check (unit in ('kg','g','l','ml','pc')),
  unique (menu_item_id, stock_item_id)
);

/** Applies one movement, updating quantity and weighted-average cost. Returns signed value in paise. */
create or replace function private.move_stock(p_item bigint, p_type text, p_qty numeric, p_unit_cost numeric,
    p_ref_type text, p_ref_id text, p_note text default null, p_batch text default null, p_expiry date default null)
returns bigint language plpgsql volatile security definer set search_path = '' as $$
declare s public.stock_items; v_cost numeric; v_value bigint;
begin
  select * into s from public.stock_items where id = p_item for update;
  if not found then raise exception 'STOCK_ITEM_NOT_FOUND'; end if;
  if p_qty > 0 and p_type in ('OPENING','RECEIPT') then
    v_cost := coalesce(p_unit_cost, s.avg_cost);
    v_value := round(p_qty * v_cost);
    update public.stock_items set
      avg_cost = case when s.qty + p_qty > 0 and s.qty > 0 then (s.qty * s.avg_cost + p_qty * v_cost) / (s.qty + p_qty) else v_cost end,
      qty = s.qty + p_qty
     where id = p_item;
  else
    v_cost := s.avg_cost;
    v_value := round(p_qty * v_cost);
    update public.stock_items set qty = s.qty + p_qty where id = p_item;
  end if;
  insert into public.stock_movements(item_id, type, qty, unit_cost, value_paise, ref_type, ref_id, batch_no, expiry_date, note, created_by)
  values (p_item, p_type, p_qty, v_cost, v_value, p_ref_type, p_ref_id, p_batch, p_expiry, p_note, auth.uid());
  return v_value;
end $$;

/** Opening stock, wastage/spoilage and physical stock counts. */
create or replace function public.stock_adjust(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_type text := p->>'type'; v_item bigint := (p->>'itemId')::bigint; s public.stock_items;
        v_qty numeric; v_value bigint; v_ref text := gen_random_uuid()::text;
begin
  perform private.require_role('OWNER','MANAGER','STAFF','KITCHEN','ACCOUNTANT');
  select * into s from public.stock_items where id = v_item;
  if not found then raise exception 'STOCK_ITEM_NOT_FOUND'; end if;
  if v_type = 'OPENING' then
    perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
    v_qty := (p->>'qty')::numeric;
    if v_qty is null or v_qty <= 0 then raise exception 'INVALID_QTY' using errcode = '22023'; end if;
    v_value := private.move_stock(v_item, 'OPENING', v_qty, (p->>'unitCostPaise')::numeric, 'OPENING', v_ref, p->>'note');
    perform private.post_journal(null, 'Opening stock: ' || s.name, 'STOCK', v_ref,
      jsonb_build_array(jsonb_build_object('account','1200','debit',v_value), jsonb_build_object('account','3000','credit',v_value)));
  elsif v_type = 'WASTAGE' then
    v_qty := (p->>'qty')::numeric;
    if v_qty is null or v_qty <= 0 then raise exception 'INVALID_QTY' using errcode = '22023'; end if;
    v_value := -private.move_stock(v_item, 'WASTAGE', -v_qty, null, 'WASTAGE', v_ref, coalesce(p->>'note','Wastage'));
    perform private.post_journal(null, 'Wastage: ' || s.name || ' ' || v_qty || ' ' || s.unit, 'STOCK', v_ref,
      jsonb_build_array(jsonb_build_object('account','5850','debit',v_value), jsonb_build_object('account','1200','credit',v_value)));
  elsif v_type = 'COUNT' then
    v_qty := (p->>'countedQty')::numeric - s.qty;
    if v_qty = 0 then return jsonb_build_object('ok', true, 'difference', 0); end if;
    v_value := private.move_stock(v_item, 'COUNT', v_qty, null, 'COUNT', v_ref, coalesce(p->>'note','Physical count'));
    perform private.post_journal(null, 'Stock count difference: ' || s.name, 'STOCK', v_ref,
      case when v_value < 0
        then jsonb_build_array(jsonb_build_object('account','5860','debit',-v_value), jsonb_build_object('account','1200','credit',-v_value))
        else jsonb_build_array(jsonb_build_object('account','1200','debit',v_value), jsonb_build_object('account','5860','credit',v_value)) end);
  else
    raise exception 'INVALID_TYPE' using errcode = '22023';
  end if;
  return jsonb_build_object('ok', true, 'difference', v_qty);
end $$;

/** Deducts recipe ingredients for a completed order — exactly once per order. */
create or replace function private.consume_stock(p_order_id uuid) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare r record; v_total bigint := 0; v_val bigint;
begin
  if exists (select 1 from public.stock_movements where type = 'CONSUMPTION' and ref_type = 'ORDER' and ref_id = p_order_id::text) then
    return;
  end if;
  for r in
    select rl.stock_item_id, sum(private.convert_qty(rl.qty, rl.unit, si.unit) * oi.quantity) as q
      from public.order_items oi
      join public.recipe_lines rl on rl.menu_item_id = oi.item_id
      join public.stock_items si on si.id = rl.stock_item_id
     where oi.order_id = p_order_id
     group by rl.stock_item_id
  loop
    v_val := -private.move_stock(r.stock_item_id, 'CONSUMPTION', -r.q, null, 'ORDER', p_order_id::text, null);
    v_total := v_total + v_val;
  end loop;
  if v_total > 0 then
    perform private.post_journal(null, 'Food cost for order ' || (select order_number from public.orders where id = p_order_id),
      'COGS', p_order_id::text,
      jsonb_build_array(jsonb_build_object('account','5000','debit',v_total), jsonb_build_object('account','1200','credit',v_total)));
  end if;
end $$;

-- ---------------------------------------------------------------------
-- Suppliers, purchase invoices, supplier payments
-- ---------------------------------------------------------------------
create table public.suppliers (
  id         bigint generated always as identity primary key,
  name       text not null,
  phone      text,
  gstin      text,
  address    text,
  notes      text,
  is_active  boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create trigger trg_suppliers_touch before update on public.suppliers for each row execute function private.touch_updated_at();

create table public.purchase_invoices (
  id               bigint generated always as identity primary key,
  supplier_id      bigint not null references public.suppliers(id),
  invoice_no       text not null,
  invoice_date     date not null,
  due_date         date,
  subtotal_paise   bigint not null,
  tax_paise        bigint not null default 0,
  total_paise      bigint not null,
  notes            text,
  journal_entry_id bigint references public.journal_entries(id),
  created_by       uuid,
  created_at       timestamptz not null default now(),
  unique (supplier_id, invoice_no)
);

create table public.purchase_lines (
  id            bigint generated always as identity primary key,
  invoice_id    bigint not null references public.purchase_invoices(id),
  stock_item_id bigint references public.stock_items(id),
  account_code  text,                 -- for non-stock lines (e.g. 5100 packaging, 5500 repairs)
  description   text not null,
  qty           numeric(14,3) not null check (qty > 0),
  unit          text,
  rate_paise    numeric(14,4) not null check (rate_paise >= 0),
  tax_paise     bigint not null default 0,
  amount_paise  bigint not null,
  batch_no      text,
  expiry_date   date
);

create table public.supplier_payments (
  id          bigint generated always as identity primary key,
  supplier_id bigint not null references public.suppliers(id),
  amount_paise bigint not null check (amount_paise > 0),
  tender      public.tender not null check (tender <> 'CREDIT'),
  reference   text,
  paid_on     date not null,
  notes       text,
  client_ref  uuid unique,
  journal_entry_id bigint references public.journal_entries(id),
  created_by  uuid,
  created_at  timestamptz not null default now()
);

create or replace function public.record_supplier_payment(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; v_amt bigint := (p->>'amountPaise')::bigint; v_t public.tender := (p->>'tender')::public.tender;
        v_sup bigint := (p->>'supplierId')::bigint; v_je bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  if p ? 'clientRef' then
    select id into v_id from public.supplier_payments where client_ref = (p->>'clientRef')::uuid;
    if v_id is not null then return jsonb_build_object('id', v_id, 'duplicate', true); end if;
  end if;
  if v_amt is null or v_amt <= 0 then raise exception 'INVALID_AMOUNT' using errcode = '22023'; end if;
  insert into public.supplier_payments(supplier_id, amount_paise, tender, reference, paid_on, notes, client_ref, created_by)
  values (v_sup, v_amt, v_t, p->>'reference', coalesce((p->>'paidOn')::date, (now() at time zone 'Asia/Kolkata')::date),
          p->>'notes', (p->>'clientRef')::uuid, auth.uid())
  returning id into v_id;
  v_je := private.post_journal(coalesce((p->>'paidOn')::date, null),
    'Payment to ' || (select name from public.suppliers where id = v_sup), 'SUPPLIER_PAYMENT', v_id::text,
    jsonb_build_array(jsonb_build_object('account','2000','debit',v_amt,'partyType','SUPPLIER','partyId',v_sup),
                      jsonb_build_object('account', private.tender_account(v_t), 'credit', v_amt)));
  update public.supplier_payments set journal_entry_id = v_je where id = v_id;
  return jsonb_build_object('id', v_id);
end $$;

/**
 * Records a supplier invoice: stock lines raise inventory (weighted average), other lines go to
 * their expense account, input GST is claimed only when gst_input_credit is on.
 */
create or replace function public.record_purchase(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare s public.restaurant_settings; v_id bigint; v_sup bigint := (p->>'supplierId')::bigint; l jsonb;
        si public.stock_items; v_qty_item numeric; v_tax bigint; v_amt bigint; v_cost_total bigint;
        v_sub bigint := 0; v_taxsum bigint := 0; v_lines jsonb := '[]'::jsonb; v_inv bigint := 0; v_je bigint;
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  select * into s from public.restaurant_settings where id = 1;
  if not exists (select 1 from public.suppliers where id = v_sup) then raise exception 'SUPPLIER_NOT_FOUND'; end if;
  if jsonb_array_length(coalesce(p->'lines','[]'::jsonb)) = 0 then raise exception 'NO_LINES' using errcode = '22023'; end if;

  insert into public.purchase_invoices(supplier_id, invoice_no, invoice_date, due_date, subtotal_paise, tax_paise, total_paise, notes, created_by)
  values (v_sup, coalesce(nullif(trim(p->>'invoiceNo'),''), private.next_number('PUR', 4)),
          coalesce((p->>'invoiceDate')::date, (now() at time zone 'Asia/Kolkata')::date), (p->>'dueDate')::date, 0, 0, 0, p->>'notes', auth.uid())
  returning id into v_id;

  for l in select * from jsonb_array_elements(p->'lines') loop
    v_amt := round((l->>'qty')::numeric * (l->>'ratePaise')::numeric);
    v_tax := coalesce((l->>'taxPaise')::bigint, 0);
    v_sub := v_sub + v_amt; v_taxsum := v_taxsum + v_tax;
    v_cost_total := v_amt + case when s.gst_input_credit then 0 else v_tax end;
    if l ? 'stockItemId' and l->>'stockItemId' is not null then
      select * into si from public.stock_items where id = (l->>'stockItemId')::bigint;
      if not found then raise exception 'STOCK_ITEM_NOT_FOUND'; end if;
      v_qty_item := private.convert_qty((l->>'qty')::numeric, coalesce(l->>'unit', si.unit), si.unit);
      perform private.move_stock(si.id, 'RECEIPT', v_qty_item, v_cost_total::numeric / v_qty_item, 'PURCHASE', v_id::text,
                                 null, l->>'batchNo', (l->>'expiryDate')::date);
      v_inv := v_inv + v_cost_total;
    else
      v_lines := v_lines || jsonb_build_array(jsonb_build_object('account', coalesce(l->>'accountCode','5010'), 'debit', v_cost_total));
    end if;
    insert into public.purchase_lines(invoice_id, stock_item_id, account_code, description, qty, unit, rate_paise, tax_paise, amount_paise, batch_no, expiry_date)
    values (v_id, (l->>'stockItemId')::bigint, l->>'accountCode', coalesce(l->>'description', si.name, 'Item'),
            (l->>'qty')::numeric, coalesce(l->>'unit', si.unit), (l->>'ratePaise')::numeric, v_tax, v_amt + v_tax,
            l->>'batchNo', (l->>'expiryDate')::date);
    si := null;
  end loop;

  v_lines := v_lines || jsonb_build_array(
    jsonb_build_object('account','1200','debit', v_inv),
    jsonb_build_object('account','1300','debit', case when s.gst_input_credit then v_taxsum else 0 end),
    jsonb_build_object('account','2000','credit', v_sub + v_taxsum, 'partyType','SUPPLIER','partyId', v_sup));
  v_je := private.post_journal((select invoice_date from public.purchase_invoices where id = v_id),
            'Purchase invoice ' || (select invoice_no from public.purchase_invoices where id = v_id) || ' — ' ||
            (select name from public.suppliers where id = v_sup), 'PURCHASE', v_id::text, v_lines);
  update public.purchase_invoices set subtotal_paise = v_sub, tax_paise = v_taxsum, total_paise = v_sub + v_taxsum,
         journal_entry_id = v_je where id = v_id;

  if coalesce((p->>'paidNowPaise')::bigint, 0) > 0 then
    perform public.record_supplier_payment(jsonb_build_object('supplierId', v_sup, 'amountPaise', (p->>'paidNowPaise')::bigint,
      'tender', coalesce(p->>'paidTender','CASH'), 'reference', 'Invoice ' || (select invoice_no from public.purchase_invoices where id = v_id),
      'paidOn', (select invoice_date from public.purchase_invoices where id = v_id)));
  end if;
  return jsonb_build_object('id', v_id, 'totalPaise', v_sub + v_taxsum);
end $$;

-- ---------------------------------------------------------------------
-- Customers (khata / credit ledger) and receipts
-- ---------------------------------------------------------------------
create table public.customers (
  id                 bigint generated always as identity primary key,
  name               text not null,
  phone              text unique,
  credit_limit_paise bigint not null default 0,   -- 0 = no credit allowed
  notes              text,
  is_active          boolean not null default true,
  created_at         timestamptz not null default now(),
  updated_at         timestamptz not null default now()
);
create trigger trg_customers_touch before update on public.customers for each row execute function private.touch_updated_at();

create or replace function private.party_balance(p_account text, p_type public.party_type, p_id bigint) returns bigint
language sql stable security definer set search_path = '' as $$
  select coalesce(sum(l.debit_paise - l.credit_paise), 0)::bigint
    from public.journal_lines l where l.account_id = private.acct(p_account) and l.party_type = p_type and l.party_id = p_id
$$;

create table public.customer_receipts (
  id           bigint generated always as identity primary key,
  customer_id  bigint not null references public.customers(id),
  amount_paise bigint not null check (amount_paise > 0),
  tender       public.tender not null check (tender <> 'CREDIT'),
  reference    text,
  received_on  date not null,
  client_ref   uuid unique,
  journal_entry_id bigint references public.journal_entries(id),
  created_by   uuid,
  created_at   timestamptz not null default now()
);

create or replace function public.record_customer_receipt(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare v_id bigint; v_c bigint := (p->>'customerId')::bigint; v_amt bigint := (p->>'amountPaise')::bigint;
        v_t public.tender := (p->>'tender')::public.tender; v_je bigint; v_date date;
begin
  perform private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
  if private.my_role()::text = 'WAITER' then raise exception 'NOT_AUTHORIZED' using errcode = '42501'; end if;
  if p ? 'clientRef' then
    select id into v_id from public.customer_receipts where client_ref = (p->>'clientRef')::uuid;
    if v_id is not null then return jsonb_build_object('id', v_id, 'duplicate', true); end if;
  end if;
  if v_amt is null or v_amt <= 0 then raise exception 'INVALID_AMOUNT' using errcode = '22023'; end if;
  v_date := coalesce((p->>'receivedOn')::date, (now() at time zone 'Asia/Kolkata')::date);
  insert into public.customer_receipts(customer_id, amount_paise, tender, reference, received_on, client_ref, created_by)
  values (v_c, v_amt, v_t, p->>'reference', v_date, (p->>'clientRef')::uuid, auth.uid()) returning id into v_id;
  v_je := private.post_journal(v_date, 'Payment received from ' || (select name from public.customers where id = v_c),
    'CUSTOMER_RECEIPT', v_id::text,
    jsonb_build_array(jsonb_build_object('account', private.tender_account(v_t), 'debit', v_amt),
                      jsonb_build_object('account','1100','credit',v_amt,'partyType','CUSTOMER','partyId',v_c)));
  update public.customer_receipts set journal_entry_id = v_je where id = v_id;
  return jsonb_build_object('id', v_id, 'balancePaise', private.party_balance('1100','CUSTOMER',v_c));
end $$;

-- ---------------------------------------------------------------------
-- Expenses (with approval) and recurring expense reminders
-- ---------------------------------------------------------------------
create table public.expenses (
  id            bigint generated always as identity primary key,
  expense_no    text not null unique,
  expense_date  date not null,
  account_id    int not null references public.accounts(id),
  amount_paise  bigint not null check (amount_paise > 0),
  tender        public.tender not null,              -- CREDIT = owed to supplier
  supplier_id   bigint references public.suppliers(id),
  vendor_name   text,
  notes         text,
  receipt_path  text,                                 -- file in the private 'receipts' bucket
  status        text not null default 'PENDING' check (status in ('PENDING','APPROVED','REJECTED')),
  created_by    uuid,
  approved_by   uuid,
  journal_entry_id bigint references public.journal_entries(id),
  recurring_id  bigint,
  created_at    timestamptz not null default now(),
  check (tender <> 'CREDIT' or supplier_id is not null)
);

create table public.recurring_expenses (
  id            bigint generated always as identity primary key,
  name          text not null,
  account_id    int not null references public.accounts(id),
  amount_paise  bigint not null check (amount_paise > 0),
  day_of_month  int not null check (day_of_month between 1 and 28),
  tender        public.tender not null default 'CASH' check (tender <> 'CREDIT'),
  is_active     boolean not null default true,
  last_posted_month date,
  created_at    timestamptz not null default now()
);

create or replace function private.post_expense(p_id bigint) returns void
language plpgsql volatile security definer set search_path = '' as $$
declare e public.expenses; v_je bigint; a public.accounts;
begin
  select * into e from public.expenses where id = p_id;
  select * into a from public.accounts where id = e.account_id;
  v_je := private.post_journal(e.expense_date, 'Expense ' || e.expense_no || ': ' || a.name ||
            coalesce(' — ' || e.vendor_name, ''), 'EXPENSE', e.id::text,
    jsonb_build_array(
      jsonb_build_object('account', a.code, 'debit', e.amount_paise),
      case when e.tender = 'CREDIT'
        then jsonb_build_object('account','2000','credit',e.amount_paise,'partyType','SUPPLIER','partyId',e.supplier_id)
        else jsonb_build_object('account', private.tender_account(e.tender), 'credit', e.amount_paise) end));
  update public.expenses set journal_entry_id = v_je where id = p_id;
end $$;

create or replace function public.record_expense(p jsonb) returns jsonb
language plpgsql volatile security definer set search_path = '' as $$
declare r public.staff_role; v_id bigint; v_acc int; v_status text;
begin
  r := private.require_role('OWNER','MANAGER','STAFF','ACCOUNTANT');
  select id into v_acc from public.accounts where code = p->>'accountCode' and type = 'EXPENSE' and is_active;
  if v_acc is null then raise exception 'INVALID_EXPENSE_ACCOUNT' using errcode = '22023'; end if;
  v_status := case when r::text in ('OWNER','MANAGER','ACCOUNTANT') then 'APPROVED' else 'PENDING' end;
  insert into public.expenses(expense_no, expense_date, account_id, amount_paise, tender, supplier_id, vendor_name, notes,
                              receipt_path, status, created_by, approved_by, recurring_id)
  values (private.next_number('EXP', 4), coalesce((p->>'date')::date, (now() at time zone 'Asia/Kolkata')::date), v_acc,
          (p->>'amountPaise')::bigint, (p->>'tender')::public.tender, (p->>'supplierId')::bigint, p->>'vendorName',
          p->>'notes', p->>'receiptPath', v_status, auth.uid(), case when v_status = 'APPROVED' then auth.uid() end,
          (p->>'recurringId')::bigint)
  returning id into v_id;
  if v_status = 'APPROVED' then perform private.post_expense(v_id); end if;
  if p ? 'recurringId' then
    update public.recurring_expenses set last_posted_month = date_trunc('month', (now() at time zone 'Asia/Kolkata'))::date
     where id = (p->>'recurringId')::bigint;
  end if;
  return jsonb_build_object('id', v_id, 'status', v_status);
end $$;

create or replace function public.decide_expense(p_id bigint, p_approve boolean) returns void
language plpgsql volatile security definer set search_path = '' as $$
begin
  perform private.require_role('OWNER','MANAGER','ACCOUNTANT');
  update public.expenses set status = case when p_approve then 'APPROVED' else 'REJECTED' end, approved_by = auth.uid()
   where id = p_id and status = 'PENDING';
  if not found then raise exception 'NOT_PENDING' using errcode = '22023'; end if;
  if p_approve then perform private.post_expense(p_id); end if;
end $$;
