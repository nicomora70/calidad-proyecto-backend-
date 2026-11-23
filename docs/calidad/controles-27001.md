# Controles de seguridad de la información — ISO/IEC 27001:2022 (Anexo A) / ISO/IEC 27002:2022

Declaración de aplicabilidad (SoA) **del producto de software**. Solo se listan los controles que
dependen del backend. Los controles organizacionales y físicos del cliente (personal, oficinas,
proveedores) son responsabilidad de cada empresa que instala el producto, porque el modelo es de
instalación por cliente.

Marco legal en Colombia: **Ley 1581 de 2012** y **Decreto 1377 de 2013** (protección de datos
personales). El sistema trata nombre, correo y datos financieros de simulaciones.

Estado: ✅ implementado · 🟡 parcial · ⬜ pendiente · ➖ responsabilidad del cliente.

| Control (Anexo A) | Aplicación en el producto | Evidencia | Estado |
|---|---|---|---|
| **5.15** Control de acceso | Acceso denegado por defecto; solo 6 rutas públicas | `SecurityConfig` (`anyRequest().authenticated()`) | ✅ |
| **5.16** Gestión de identidades | Sin autoregistro; el ADMIN gestiona el ciclo de vida de las cuentas | `UserService`, `BootstrapAdminInitializer` | ✅ |
| **5.17** Información de autenticación | Contraseña temporal de cambio obligatorio; mínimo 8 caracteres; BCrypt (cost 12) | `PasswordChangeRequiredInterceptor`, `UserDtos` | ✅ |
| **5.18** Derechos de acceso | Roles ADMIN, IT_MANAGER e INVESTOR; un cambio de rol revoca sesiones; regla LAST_ADMIN | `@PreAuthorize`, `UserService.changeRole` | ✅ |
| **5.34** Privacidad y protección de datos personales | Datos mínimos (nombre y correo); borrado lógico; aviso de privacidad y derecho de supresión | `POST /api/v1/users/{id}/anonymize`, [privacidad.md](../privacidad.md) (aviso y procedimiento) | ✅ |
| **5.37** Procedimientos operativos documentados | Instalación, variables y primer uso documentados | `README.md`, `.env.example`, `docs/instalacion-cliente.md`, `docs/manual-operacion.md` | ✅ |
| **8.2** Derechos de acceso privilegiado | Funciones de administración exclusivas de ADMIN, todas auditadas | `UserController`, `audit_log` | ✅ |
| **8.3** Restricción de acceso a la información | Cada inversionista solo ve sus simulaciones (404 a las ajenas) | `SimulationService.get` | ✅ |
| **8.4** Acceso al código fuente | Repositorio privado o controlado y cambios vía PR | Protección de `main`: PR obligatorio, 5 checks de CI requeridos y actualizados, sin *force push* ni borrado, también para administradores | ✅ |
| **8.5** Autenticación segura | JWT de vida corta, rotación del refresh token, bloqueo por fuerza bruta, mensaje de error genérico, tiempo constante con correo inexistente | `AuthService` | ✅ |
| **8.6** Gestión de la capacidad | Rate limiting por IP y usuario; carga medida con k6; métricas en Prometheus | `RateLimitFilter`, `ops/k6/simulations.js`, `/actuator/prometheus` (puerto interno) | ✅ |
| **8.8** Gestión de vulnerabilidades técnicas | Escaneo de dependencias e imágenes | Dependabot semanal, Trivy en cada PR, OWASP Dependency-Check semanal (`security.yml`) | ✅ |
| **8.9** Gestión de la configuración | Configuración por variables de entorno, versionada en `.env.example`; esquema con Flyway | `docker-compose.yml`, migraciones | ✅ |
| **8.12** Prevención de fuga de datos | Secretos fuera del repositorio (`.env` en `.gitignore`); los errores no exponen trazas internas | `.gitignore`, `ApiExceptionHandler` | ✅ |
| **8.13** Respaldo de la información | `pg_dump` diario con retención; restauración probada en cada CI | `pg-backup`, `ops/backup/*.sh`, `ci.yml` | ✅ (la copia fuera del servidor corre por cuenta del cliente) |
| **8.15** Registro (*logging*) | Auditoría de solo inserción (quién, qué, cuándo, IP, navegador) y logs JSON con `traceId` | `AuditService`, `RequestIdFilter` | ✅ |
| **8.16** Actividades de seguimiento | Detección de reutilización de tokens y de bloqueos de cuenta (eventos auditados) | `REFRESH_TOKEN_REUSED`, `ACCOUNT_LOCKED`; notificación `SECURITY_EVENT` a IT_MANAGER y ADMIN (`NotificationTriggers`) | ✅ |
| **8.17** Sincronización de relojes | Todas las marcas de tiempo en UTC (`TIMESTAMPTZ`, `Clock` UTC) | `application.yml`, `WebConfig` | ✅ |
| **8.20** Seguridad de las redes | El motor de cálculo y Postgres sin puertos públicos; red interna de Docker | `docker-compose.yml` | ✅ |
| **8.24** Uso de criptografía | HMAC-SHA256 en los JWT, BCrypt, SHA-256 en los refresh tokens, HTTPS en el proxy (`REQUIRE_HTTPS`: redirección y HSTS) | `SecurityConfig`, Caddy en `docs/instalacion-cliente.md`; licencias firmadas con Ed25519 | ✅ (TLS en el proxy del cliente) |
| **8.25** Ciclo de vida de desarrollo seguro | Ramas, PR, CI obligatorio y definición de "hecho" | `task.md`, `.github/workflows/ci.yml` | ✅ |
| **8.26** Requisitos de seguridad de las aplicaciones | Requisitos RNF-02, RNF-03 y RNF-09 | `matriz-trazabilidad-29148.md` | ✅ |
| **8.28** Codificación segura | Consultas parametrizadas (JPA y `JdbcTemplate`), validación de entrada, CORS restringido, contenedores sin root, CSRF no aplica (sin cookies) | Código | ✅ |
| **8.29** Pruebas de seguridad en desarrollo y aceptación | Pruebas de permisos por rol, de bloqueo, de tokens, de cabeceras, CORS y rate limiting; revisión OWASP ASVS L2 | `SimulationControllerTest`, `InstallationFlowIT`, `RateLimitFilterTest`, [revision-asvs-l2.md](revision-asvs-l2.md) | ✅ |
| **8.31** Separación de entornos | Desarrollo con proveedor `fixed`; producción con `alphavantage` y secretos propios | `.env.example` | ✅ |
| **8.32** Gestión de cambios | Todo cambio entra por PR con CI en verde; migraciones nunca se editan | Flujo de GitHub | ✅ |
| 5.19–5.22 Proveedores · 6.x Personas · 7.x Físicos | — | — | ➖ |

## Brechas (se convierten en tareas de la Fase Q en `task.md`)
1. ~~Protección de la rama `main`~~ (cerrado en la Fase Q).
2. ~~Procedimiento de supresión definitiva de datos personales~~ (cerrado en la Fase Q).
3. ~~Alertas de seguridad al IT_MANAGER~~ (cerrado en la Fase 4).
4. ~~Respaldos, TLS y manual de operación~~ (cerrado en la Fase 5).
5. ~~Escaneo de vulnerabilidades y revisión ASVS~~ (cerrado en la Fase 6).
