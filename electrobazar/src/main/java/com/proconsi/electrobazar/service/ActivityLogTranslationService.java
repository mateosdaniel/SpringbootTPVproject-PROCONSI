package com.proconsi.electrobazar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to translate activity log descriptions between Spanish and English
 * using pattern matching and fallbacks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogTranslationService {

    private final TranslationService translationService;

    // Spanish -> English
    private static final Map<String, String> ES_TO_EN = new LinkedHashMap<>();
    // English -> Spanish (for legacy logs)
    private static final Map<String, String> EN_TO_ES = new LinkedHashMap<>();

    static {
        // --- ES TO EN ---
        ES_TO_EN.put("Turno cerrado por (.*)\\. Diferencial: (.*) €", "Shift closed by $1. Discrepancy: $2 €");
        ES_TO_EN.put("Retenido para el próximo turno: (.*) €", "Retained for next shift: $1 €");
        ES_TO_EN.put("Nuevo turno abierto por (.*) con (.*) €", "New shift opened by $1 with $2 €");
        ES_TO_EN.put("Nuevo producto añadido: (.*)", "New product added: $1");
        ES_TO_EN.put("Producto actualizado: (.*)", "Product updated: $1");
        ES_TO_EN.put("Producto desactivado: (.*)", "Product deactivated: $1");
        ES_TO_EN.put("Producto eliminado permanentemente: (.*)", "Product permanently deleted: $1");
        ES_TO_EN.put("Disminución manual de stock: -(.*) para (.*)", "Manual stock decrease: -$1 for $2");
        ES_TO_EN.put("Aumento manual de stock: \\+(.*) para (.*)", "Manual stock increase: +$1 for $2");
        ES_TO_EN.put("Ajuste de stock: (.*) \\(Nuevo stock: (.*)\\) para (.*)", "Stock adjustment: $1 (New stock: $2) for $3");
        ES_TO_EN.put("Actualización masiva de IVA: (.*) \\(aplicado a todos los productos coincidentes\\)", "Bulk VAT update: $1 (applied to all matching products)");
        ES_TO_EN.put("Nueva categoría creada: (.*)", "New category created: $1");
        ES_TO_EN.put("Categoría actualizada: (.*)", "Category updated: $1");
        ES_TO_EN.put("Categoría desactivada: (.*)", "Category deactivated: $1");
        ES_TO_EN.put("Categoría eliminada permanentemente: (.*)", "Category permanently deleted: $1");
        ES_TO_EN.put("Nuevo cliente registrado: (.*)", "New customer registered: $1");
        ES_TO_EN.put("Cliente actualizado: (.*)", "Customer updated: $1");
        ES_TO_EN.put("Cliente desactivado: (.*)", "Customer deactivated: $1");
        ES_TO_EN.put("Factura (.*) generada para Venta nº (\\d+)\\. Hash Verifactu: (.*)", "Invoice $1 generated for Sale #$2. Verifactu Hash: $3");
        ES_TO_EN.put("Devolución (.*) procesada para Venta nº (\\d+)\\. Total: -(.*) €. Pago: (.*)", "Return $1 processed for Sale #$2. Total: -$3 €. Payment: $4");
        ES_TO_EN.put("Venta procesada por (.*)\\. Total: (.*) € \\(Cupón: (.*)\\)", "Sale processed by $1. Total: $2 € (Coupon: $3)");
        ES_TO_EN.put("Venta nº (\\d+) anulada por (.*)\\. Motivo: (.*)", "Sale #$1 cancelled by $2. Reason: $3");
        ES_TO_EN.put("Tarifa creada: (.*) \\(Descuento: (.*)%, Color: (.*)\\)", "Tariff created: $1 (Discount: $2%, Color: $3)");
        ES_TO_EN.put("Tarifa actualizada: (.*) \\(Nuevo descuento: (.*)%, Nuevo color: (.*)\\)", "Tariff updated: $1 (New Discount: $2%, New Color: $3)");
        ES_TO_EN.put("Tarifa desactivada: (.*)", "Tariff deactivated: $1");
        ES_TO_EN.put("Precios regenerados para (\\d+) productos en (\\d+) tarifas activas\\.", "Prices regenerated for $1 products across $2 active tariffs.");
        ES_TO_EN.put("Nuevo trabajador registrado: (.*)", "New worker registered: $1");
        ES_TO_EN.put("Trabajador actualizado: (.*)", "Worker updated: $1");
        ES_TO_EN.put("Trabajador desactivado: (.*)", "Worker deactivated: $1");
        ES_TO_EN.put("Trabajador eliminado definitivamente: (.*)", "Worker permanently deleted: $1");
        ES_TO_EN.put("Inicio de sesión exitoso: (.*)", "Login successful: $1");
        ES_TO_EN.put("Intento de inicio de sesión fallido: (.*)", "Failed login attempt: $1");
        ES_TO_EN.put("Contraseña restablecida correctamente vía email: (.*)", "Password successfully reset via email: $1");
        ES_TO_EN.put("Nuevo precio programado para '(.*)': (.*) € a partir de (.*)", "New price scheduled for '$1': $2 € starting $3");
        ES_TO_EN.put("Actualización masiva de precios para (\\d+) productos\\.", "Bulk price update for $1 products.");
        ES_TO_EN.put("Actualización de matriz de precios procesada para (\\d+) entradas\\.", "Bulk price matrix update processed for $1 entries.");
        ES_TO_EN.put("Importación de CSV exitosa: (.*)", "CSV Import successful: $1");
        ES_TO_EN.put("\\[BACKUP\\] Backup automático \\((.*)\\) completado: (.*) y (.*)", "[BACKUP] Automatic backup ($1) completed: $2 and $3");
        ES_TO_EN.put("\\[BACKUP\\] Backup manual ejecutado por (.*)", "[BACKUP] Manual backup executed by $1");
        ES_TO_EN.put("\\[VERIFACTU\\] Modificación de la configuración fiscal de la empresa\\.", "[VERIFACTU] Modification of the company's fiscal configuration.");
        ES_TO_EN.put("\\[VERIFACTU\\] Inicio de sesión de trabajador: (.*)", "[VERIFACTU] Worker login: $1");
        ES_TO_EN.put("\\[VERIFACTU\\] Cierre de sesión de trabajador: (.*)", "[VERIFACTU] Worker logout: $1");
        ES_TO_EN.put("Cierre de sesión de trabajador: (.*)", "Worker logout: $1");
        ES_TO_EN.put("Inicio de sesión de trabajador: (.*)", "Worker login: $1");

        // --- EN TO ES (Legacy Support) ---
        EN_TO_ES.put("Product updated: (.*)", "Producto actualizado: $1");
        EN_TO_ES.put("Prices regenerated for (\\d+) products across (\\d+) active tariffs\\.", "Precios regenerados para $1 productos en $2 tarifas activas.");
        EN_TO_ES.put("Manual stock adjustment for (.*): (.*) -> (.*)", "Ajuste de stock manual para $1: $2 -> $3");
        EN_TO_ES.put("Product created: (.*) \\(SKU: (.*)\\)", "Producto creado: $1 (SKU: $2)");
        EN_TO_ES.put("New category created: (.*)", "Nueva categoría creada: $1");
        EN_TO_ES.put("Category updated: (.*)", "Categoría actualizada: $1");
        EN_TO_ES.put("Sale processed by (.*)\\. Total: (.*) €", "Venta procesada por $1. Total: $2 €");
        EN_TO_ES.put("Venta finalizada: #(\\d+)\\. Total: (.*) €. Método: (.*)", "Venta finalizada: #$1. Total: $2 €. Método: $3");
        EN_TO_ES.put("Login successful: (.*)", "Inicio de sesión exitoso: $1");
        EN_TO_ES.put("Failed login attempt: (.*)", "Intento de inicio de sesión fallido: $1");
        EN_TO_ES.put("Turno cerrado por (.*)\\. Diferencial: (.*) €", "Turno cerrado por $1. Diferencial: $2 €");
        EN_TO_ES.put("\\[VERIFACTU\\] Worker login: (.*)", "[VERIFACTU] Inicio de sesión de trabajador: $1");
        EN_TO_ES.put("Worker login: (.*)", "Inicio de sesión de trabajador: $1");
        EN_TO_ES.put("Worker logout: (.*)", "Cierre de sesión de trabajador: $1");
    }

    public String translateToEnglish(String description) {
        return applyPatterns(description, ES_TO_EN, "EN", true);
    }

    public String translateToSpanish(String description) {
        // For Spanish, we assume it's already Spanish unless it matches a legacy English pattern.
        // This avoids 50+ DeepL calls per refresh for the common Spanish user case.
        return applyPatterns(description, EN_TO_ES, "ES", false);
    }

    private String applyPatterns(String description, Map<String, String> patterns, String lang, boolean useFallback) {
        if (description == null || description.isBlank()) return description;

        try {
            for (Map.Entry<String, String> entry : patterns.entrySet()) {
                String regex = "^" + entry.getKey() + "$";
                Pattern p = Pattern.compile(regex);
                Matcher m = p.matcher(description);
                if (m.matches()) {
                    String template = entry.getValue();
                    String result = template;
                    for (int i = 1; i <= m.groupCount(); i++) {
                        String replacement = m.group(i);
                        result = result.replace("$" + i, replacement);
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("Error applying translation patterns: {}", e.getMessage());
        }

        if (useFallback) {
            return translationService.translate(description, lang);
        }
        
        return description; // Return as is if already in target language (or no pattern found)
    }
}
