# Revisión OWASP ASVS 4.0.3 — nivel 2

Revisión del backend (`api-java` + `calc-engine`) contra el *Application Security Verification
Standard*, nivel 2 (aplicaciones con datos sensibles). Es una autoevaluación con evidencia en el
código y en las pruebas, no una auditoría externa. Solo se listan los requisitos que aplican a una
API REST sin estado, con tokens Bearer y sin cookies; los de interfaz (HTML, plantillas del navegador)
corresponden al repositorio del frontend.

Estado: ✅ cumple · 🟡 parcial · ⬜ pendiente · N/A no aplica.

| Capítulo | Requisito (resumen) | Evidencia | Estado |
|---|---|---|---|
| **V1** Arquitectura | 1.1.4 Límites de confianza documentados | `docs/arquitectura.md` (red interna, solo la API publicada) | ✅ |
| | 1.4.1 Control de acceso aplicado en el servidor | `SecurityConfig` (denegado por defecto), `@PreAuthorize` | ✅ |
| | 1.14.6 Sin componentes inseguros ni obsoletos | Dependabot, Trivy, Dependency-Check (`security.yml`) | ✅ |
| **V2** Autenticación | 2.1.1 Contraseñas de 8+ caracteres | `UserDtos`, `AuthDtos` (`@Size(min = PASSWORD_MIN)`) | ✅ |
| | 2.2.1 Anti automatización (fuerza bruta) | Bloqueo tras 5 intentos (15 min) + rate limiting de login por IP (`RateLimitFilter`, 429) | ✅ |
| | 2.4.1 Almacenamiento con función lenta y sal | BCrypt cost 12 | ✅ |
| | 2.5.x Recuperación de credenciales | No hay autoregistro ni recuperación por correo: el ADMIN asigna una contraseña temporal de cambio obligatorio | ✅ |
| | 2.2.3 Avisar al usuario de eventos de seguridad | Notificación `SECURITY_EVENT` (`ACCOUNT_LOCKED`, `REFRESH_TOKEN_REUSED`) | ✅ |
| **V3** Sesiones | 3.5.2 Tokens firmados, de vida corta | JWT HS256 de 15 min; emisor y vencimiento validados (`JwtServiceTest`) | ✅ |
| | 3.3.1 Logout invalida la sesión | `POST /api/v1/auth/logout` revoca el refresh token | ✅ |
| | 3.3.3 Cerrar sesiones tras cambio de contraseña o de rol | Refresh tokens revocados (`UserService`); el access token vive como máximo 15 min (riesgo R-12 aceptado) | 🟡 |
| | 3.5.3 Detección de reutilización del refresh token | Rotación + revocación de la familia (`REFRESH_TOKEN_REUSED`) | ✅ |
| **V4** Control de acceso | 4.1.3 Mínimo privilegio | Roles ADMIN, IT_MANAGER, INVESTOR | ✅ |
| | 4.2.1 IDOR: cada inversionista solo ve lo suyo | 404 a simulaciones ajenas (`SimulationService.get`) | ✅ |
| | 4.3.1 Interfaces administrativas protegidas | Actuator en el puerto interno 8081, no publicado | ✅ |
| **V5** Validación | 5.1.3 Validación de entrada en el servidor | Bean Validation en todos los DTO, `VALIDATION_ERROR` | ✅ |
| | 5.3.4 Consultas parametrizadas | JPA y `JdbcTemplate` con parámetros | ✅ |
| | 5.3.x Inyección en CSV (exportes) | `Csv` neutraliza `= + - @` al inicio de celda | ✅ |
| **V7** Errores y registro | 7.1.1 Sin credenciales ni tokens en los logs | Revisión del código; los tokens nunca se registran | ✅ |
| | 7.4.1 Errores genéricos con id de traza | RFC 7807 con `code` y `traceId`, sin trazas internas | ✅ |
| | 7.2.1 Registrar eventos de autenticación | `audit_log` (login, bloqueo, reutilización) | ✅ |
| **V8** Protección de datos | 8.1.1 Sin datos sensibles en caché | `Cache-Control: no-store` (cabeceras por defecto de Spring Security) | ✅ |
| | 8.3.4 Datos personales identificados | Nombre y correo; supresión definitiva pendiente (Ley 1581) | 🟡 |
| **V9** Comunicaciones | 9.1.1 TLS para todo el tráfico del cliente | Proxy TLS (Caddy) + `REQUIRE_HTTPS=true`: redirección a HTTPS y HSTS de 1 año | ✅ |
| | 9.2.x Conexiones internas | Red interna de Docker sin puertos públicos (calc-engine, Postgres) | ✅ |
| **V10** Código malicioso | 10.3.2 Integridad de componentes | Dependencias de Maven Central/PyPI con versión fija; imágenes base escaneadas | ✅ |
| **V11** Lógica de negocio | 11.1.4 Límites contra el abuso | Rate limiting por usuario (300/min) y por IP (120/min); tope diario de noticias y de gasto de IA | ✅ |
| **V12** Archivos | 12.x Subida de archivos | N/A: no hay subida de archivos; el logo del branding es una URL `https://` validada | N/A |
| **V13** API | 13.1.3 URLs sin datos sensibles | Tokens solo en la cabecera `Authorization` | ✅ |
| | 13.2.1 Métodos HTTP restringidos | CORS con métodos explícitos; 405 para el resto | ✅ |
| | 13.2.3 CSRF | N/A: API sin cookies (solo Bearer) | N/A |
| | 13.2.5 `Content-Type` validado | Spring MVC responde 415 a tipos no admitidos | ✅ |
| **V14** Configuración | 14.2.1 Componentes al día | Dependabot semanal; Trivy falla el PR con CRITICAL corregibles | ✅ |
| | 14.3.2 Sin modo debug ni trazas en producción | Sin `devtools`; `API_DOCS_ENABLED=false` oculta Swagger si el cliente lo pide | ✅ |
| | 14.4.x Cabeceras de seguridad | CSP, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, HSTS | ✅ |
| | 14.5.3 CORS con lista blanca | Solo `CORS_ALLOWED_ORIGINS`; otro origen recibe 403 (`InstallationFlowIT`) | ✅ |

## Brechas abiertas
1. **3.3.3**: el access token sigue válido hasta 15 min tras un cambio de rol (aceptado, R-12).
2. **8.3.4**: procedimiento de supresión definitiva de datos personales (Fase Q).
3. El límite por IP usa `X-Forwarded-For`: el puerto 8080 solo debe ser accesible desde el proxy
   (firewall con 80/443 abiertos, como indica `instalacion-cliente.md`); si no, un atacante puede
   falsear su IP. El bloqueo por cuenta (5 intentos) sigue protegiendo en ese caso.
4. **Riesgo aceptado (14.2.1)**: dos CVE críticos de spring-webmvc 6.2.19 (CVE-2026-47884 en `XsltView`
   y CVE-2026-47890 en SSE con vistas) solo tienen corrección en Spring Framework 7 (Spring Boot 4).
   La API no usa ninguna de las dos funciones, así que quedan en `api-java/.trivyignore` con vencimiento
   2027-02-28. Antes de esa fecha, o antes de construir el SSE opcional, hay que migrar a Spring Boot 4.
   Tomcat se fijó en 10.1.62 (`tomcat.version`) para corregir otros 5 CVE críticos.
5. El rate limiting vive en memoria: con varias réplicas de la API cada una cuenta por separado.
   Con una instalación por cliente (una réplica) es suficiente; si se escala, se pasa Bucket4j a Postgres o Redis.
