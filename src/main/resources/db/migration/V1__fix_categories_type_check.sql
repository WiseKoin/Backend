-- V1__fix_categories_type_check.sql
ALTER TABLE categories DROP CONSTRAINT categories_type_check;
ALTER TABLE categories ADD CONSTRAINT categories_type_check CHECK (type IN ('INCOME', 'EXPENSE', 'LEND', 'BORROW'));
