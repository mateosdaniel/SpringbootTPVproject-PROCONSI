-- Ejecutar DESPUÉS de haber arrancado la aplicación de Spring Boot al menos una vez para que Hibernate cree la columna `image_url`

-- Actualizar todos los productos para que tengan la imagen genérica por defecto si no tienen una.
UPDATE products SET image_url = '/placeholder-product.svg' WHERE image_url IS NULL OR image_url = '';
