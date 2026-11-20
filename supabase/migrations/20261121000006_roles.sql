-- New staff roles for the restaurant ERP. Kept in its own migration because
-- PostgreSQL cannot use a newly added enum value in the same transaction.
alter type public.staff_role add value if not exists 'CASHIER';
alter type public.staff_role add value if not exists 'WAITER';
alter type public.staff_role add value if not exists 'ACCOUNTANT';
