-- Delivery rider role. Kept in its own migration because PostgreSQL cannot use a
-- newly added enum value in the same transaction that adds it.
-- DELIVERY does NOT count as STAFF: riders cannot use POS, payments or the cash register.
alter type public.staff_role add value if not exists 'DELIVERY';
