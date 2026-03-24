-- Migration script for DeepL translation integration
-- 1. Products table
ALTER TABLE products RENAME COLUMN name TO name_es;
ALTER TABLE products RENAME COLUMN description TO description_es;

-- Add new multilingual columns
ALTER TABLE products ADD COLUMN name_en VARCHAR(150);
ALTER TABLE products ADD COLUMN description_en VARCHAR(255);
ALTER TABLE products ADD COLUMN status_es VARCHAR(100);
ALTER TABLE products ADD COLUMN status_en VARCHAR(100);
ALTER TABLE products ADD COLUMN low_stock_message_es VARCHAR(255);
ALTER TABLE products ADD COLUMN low_stock_message_en VARCHAR(255);

-- 2. Categories table
ALTER TABLE categories RENAME COLUMN name TO name_es;
ALTER TABLE categories RENAME COLUMN description TO description_es;

-- Add new multilingual columns
ALTER TABLE categories ADD COLUMN name_en VARCHAR(100);
ALTER TABLE categories ADD COLUMN description_en VARCHAR(255);
