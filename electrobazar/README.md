# Electrobazar TPV - Spring Boot API

Sistema de Terminal Punto de Venta (TPV) robusto basado en **Spring Boot**, diseñado bajo una arquitectura orientada a servicios (API-First). Centraliza toda la lógica de negocio, persistencia, generación de documentos (tickets/facturas) e integración con **Verifactu (AEAT)**.

## 🚀 Estado del Proyecto

*   **Versión PC (Web Admin/TPV):** 100% Funcional. Interfaz web completa para gestión de ventas, clientes e inventario.
*   **Versión Android (Mobile):** En desarrollo avanzado (Java/Android Studio). La arquitectura permite la integración inmediata mediante el consumo de los endpoints REST expuestos.

## 🏗️ Arquitectura y Capacidades API

El backend está diseñado para funcionar como una **API centralizada**, facilitando la interoperabilidad entre diferentes clientes (Web, Mobile, Terminales físicos):

*   **Lógica Centralizada:** El motor de cálculo, validaciones fiscales y procesos de negocio residen exclusivamente en el backend.
*   **Gestión Documental:** Generación dinámica de facturas y tickets en PDF.
*   **Cumplimiento Fiscal:** Integración nativa con el sistema Verifactu de la AEAT para la trazabilidad de facturas.
*   **Seguridad:** Implementación de filtrado por tokens para el acceso a endpoints sensibles del TPV.

## ⚙️ Configuración del Entorno

El proyecto soporta múltiples entornos mediante perfiles de Spring:

### 1. Desarrollo Local (`application-local.properties`)
Utilice este archivo para la ejecución en máquina local.
*   **Base de Datos:** Configurada por defecto para MariaDB/MySQL (puerto 3307).
*   **Requisitos:** Disponer de una base de datos denominada `electrobazar`.

### 2. Despliegue Cloud / API Mode (`application.properties`)
Configuración optimizada para despliegue en **Railway** u otros entornos Cloud.
*   **Variables de Entorno:** Utiliza placeholders (`${SPRING_DATASOURCE_URL}`, etc.) para inyectar credenciales de forma segura.
*   **Escalabilidad:** Configurado para manejar pools de conexiones dinámicos y persistencia en la nube.

## 🛠️ Guía de Inicio Rápido

1.  **Clonar el repositorio.**
2.  **Configurar base de datos local** según los parámetros de `application-local.properties`.
3.  **Ejecutar mediante Maven Wrapper:**
    ```bash
    ./mvnw spring-boot:run -Dspring.profiles.active=local
    ```

---
*Desarrollado como solución integral de gestión comercial.*
