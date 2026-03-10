-- 1. Añadimos temporalmente la nueva columna (es opcional al inicio)
ALTER TABLE products ADD COLUMN tax_rate_id BIGINT;

-- 2. Migramos los datos haciendo un matching seguro con el valor numérico 
-- (Con ABS() permitimos fallos insignificantes en el redondeo decimal en la capa de persistencia)
UPDATE products p
SET p.tax_rate_id = (
    SELECT t.id FROM tax_rates t 
    WHERE ABS(t.vat_rate - p.iva_rate) < 0.0001 
    LIMIT 1
);

-- 3. Asignación defensiva: Si hubiese algún producto sin IVA (nulo o sin mach), le asignamos el ID 1 "General"
UPDATE products SET tax_rate_id = 1 WHERE tax_rate_id IS NULL;

-- 4. Hacemos que la nueva columna rechace nulos para respetar el modelo de datos riguroso
ALTER TABLE products MODIFY COLUMN tax_rate_id BIGINT NOT NULL;

-- 5. Destruimos la columna vieja ahora que ya hemos transaccionado todo
ALTER TABLE products DROP COLUMN iva_rate;

-- 6. Enlazamos la FK para integridad referencial de ahora en adelante
ALTER TABLE products ADD CONSTRAINT fk_products_tax_rate FOREIGN KEY (tax_rate_id) REFERENCES tax_rates(id);
