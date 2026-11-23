# task.md — Hoja de ruta del backend RiTech

> **Convenciones:** el código va en **inglés** (clases, endpoints, tablas, variables); la
> documentación y este archivo, en **español**. Este repositorio es **solo backend**: el
> frontend vive en otro repositorio y consume el contrato OpenAPI.
>
> Documentación relacionada: [Lógica de negocio](docs/logica-negocio.md) · [Arquitectura](docs/arquitectura.md) · [Modelo de datos](docs/modelo-datos.md) · [Calidad ISO](docs/calidad/README.md)
>
> Leyenda: `[x]` hecho · `[ ]` pendiente · **(P)** = prioridad alta dentro de la fase

---

## Fase 0 — Base del proyecto ✅

- [x] Estructura del repositorio: `api-java/`, `calc-engine/`, `docs/`
- [x] `docker-compose.yml` con `postgres`, `calc-engine` (sin puerto público) y `api`, más healthchecks
- [x] Dockerfiles multi-stage con usuario no root
- [x] `.env.example`, `.gitignore`, `.gitattributes`
- [x] Spring Boot 3.5 / Java 21 con Maven Wrapper
- [x] Flyway `V1__initial_schema.sql` (users, simulations, market_variations, audit_log)
- [x] Errores estándar RFC 7807 con `code` (`ApiExceptionHandler`)
- [x] `GET /api/health`, Actuator `/actuator/health`, Swagger en `/swagger-ui.html`
- [x] calc-engine: `POST /calculations/returns` y `POST /calculations/statistics` con `Decimal` y HALF_EVEN
- [x] Proveedor de mercado intercambiable (`fixed` / `alphavantage`) con caché por TTL
- [x] `POST /api/simulations`, `GET /api/simulations` (paginado), `GET /api/dashboard/statistics`
- [x] Auditoría de `SIMULATION_CREATED`
- [x] Pruebas: pytest (14) y JUnit/MockMvc
- [x] GitHub Actions: pruebas de ambos servicios y smoke test con `docker compose`

## Fase 1 — Núcleo vendible (MVP) ✅

### Seguridad y autenticación ✅
- [x] `spring-boot-starter-security` y `spring-boot-starter-oauth2-resource-server` (Spring Boot 3.5.16)
- [x] `POST /api/auth/login` → `accessToken` (JWT HS256 de 15 min) y `refreshToken` opaco (7 días, guardado como hash SHA-256)
- [x] `POST /api/auth/refresh` con rotación: el token usado se revoca y, si se reutiliza uno revocado, se cierran todas las sesiones del usuario
- [x] `POST /api/auth/logout` (revoca el refresh token)
- [x] `POST /api/auth/change-password`: obligatorio con contraseña temporal (`403 PASSWORD_CHANGE_REQUIRED`); cierra las demás sesiones
- [x] Firma con `JWT_SECRET` por instalación (mínimo 32 bytes; la API no arranca sin él)
- [x] Bloqueo tras 5 intentos fallidos durante 15 minutos (`423 ACCOUNT_LOCKED`), guardado aunque la petición falle
- [x] Autorización por rol con `@PreAuthorize`, según la matriz de `logica-negocio.md`
- [x] `userId` sale del JWT, no del cuerpo de `POST /api/simulations`
- [x] Migración V2: `refresh_tokens`, columnas de bloqueo y `simulations.user_id NOT NULL`
- [x] Errores 401/403 de la cadena de seguridad también en RFC 7807 (`UNAUTHORIZED`, `TOKEN_EXPIRED`, `FORBIDDEN`)
- [x] Un token vencido enviado a un endpoint público (p. ej. `/api/auth/refresh`) se ignora en vez de dar 401
- [x] CORS gestionado por Spring Security, solo para `CORS_ALLOWED_ORIGINS`

### Usuarios ✅
- [x] Entidad `User` y `UserRepository`, contraseñas con BCrypt (cost 12); correo normalizado en minúsculas
- [x] ADMIN inicial desde `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` si no hay usuarios (con contraseña temporal)
- [x] `POST /api/users` (ADMIN): crea el usuario con contraseña temporal
- [x] `GET /api/users` (ADMIN): paginado, con filtros `role` y `active`
- [x] `GET /api/users/{id}`, `PATCH /api/users/{id}` (nombre y estado activo; al desactivar se revocan sus sesiones)
- [x] `PATCH /api/users/{id}/role` con la regla `LAST_ADMIN` (con bloqueo de filas contra condiciones de carrera)
- [x] `DELETE /api/users/{id}` como borrado lógico (las simulaciones se conservan)
- [x] `GET /api/me`: perfil del usuario autenticado
- [x] Auditoría: LOGIN_SUCCESS, LOGIN_FAILED, LOGIN_BLOCKED, ACCOUNT_LOCKED, LOGOUT, PASSWORD_CHANGED, REFRESH_TOKEN_REUSED, USER_CREATED, USER_ACTIVATED, USER_DEACTIVATED, USER_DELETED, ROLE_CHANGED, BOOTSTRAP_ADMIN_CREATED

### Simulaciones e historial
- [x] `GET /api/simulations/{id}` (dueño o ADMIN; a los demás les responde 404 para no revelar ids)
- [x] `GET /api/me/simulations` con filtros `from` y `to` (fechas inclusivas en `America/Bogota`)
- [x] `GET /api/simulations` solo para ADMIN, con filtros `userId`, `from` y `to`
- [x] Dashboard con filtros por fecha y usuario, solo para ADMIN
- [x] Advisory lock de PostgreSQL para que, cuando la caché vence, solo una petición llame a Alpha Vantage
- [x] Resilience4j: 3 intentos con backoff (200 ms, 400 ms) y circuit breaker (se abre con 50 % de fallos y espera 30 s) hacia el calc-engine y Alpha Vantage. Solo se reintentan fallos de red y 5xx; los 4xx y las respuestas de límite de Alpha Vantage no, para no gastar la cuota

### Auditoría ✅
- [x] `GET /api/audit` (IT_MANAGER/ADMIN), paginado y con filtros por acción, actor y fecha
- [x] IP (respetando `X-Forwarded-For` del proxy) y user-agent en columnas propias

### Calidad
- [x] Pruebas de integración con **Testcontainers** (`InstallationFlowIT`: Postgres real, Flyway y validación del esquema) en CI
- [x] Pruebas unitarias de auth, JWT, usuarios, simulaciones, auditoría, resiliencia y seguridad de endpoints (51)
- [x] Smoke test en CI con el flujo completo autenticado sobre `docker compose`
- [x] Reporte de cobertura JaCoCo (unitarias + integración combinadas) publicado como artefacto en CI
- [x] Umbral: cada clase `*Service` necesita ≥ 80 % de líneas cubiertas solo con pruebas unitarias; si no, `verify` falla (igual en local y en CI)
- [x] Linters: Spotless con palantir-java-format en `verify` (`./mvnw spotless:apply` corrige) y Ruff (`ruff check` + `ruff format --check`) en CI
- [x] Logs en JSON (ECS) en Docker con `traceId` por petición: se respeta el `X-Request-Id` entrante, se devuelve en la respuesta y en los errores (`traceId`) y se propaga al calc-engine, que también registra en JSON

> **Limitación conocida:** un access token sigue siendo válido hasta su vencimiento (15 min) aunque se
> cambie el rol o se desactive al usuario; lo que se revoca de inmediato es el refresh token. Si un
> cliente necesita revocación instantánea, se puede verificar el estado del usuario en cada petición
> (con una consulta extra a la BD).

## Fase Q — Aseguramiento de calidad ISO (transversal a todas las fases)

> Alineación (no certificación) con las normas ISO de calidad. Documentos en [`docs/calidad/`](docs/calidad/README.md).

### Documentación base ✅
- [x] Marco normativo y ciclo PDCA (ISO 9001 / ISO/IEC/IEEE 90003): `docs/calidad/README.md`
- [x] Modelo de calidad con las 9 características de **ISO/IEC 25010:2023**, métricas de ISO/IEC 25023, metas y estado, evaluado según ISO/IEC 25040: `modelo-calidad-25010.md`
- [x] Plan de pruebas **ISO/IEC/IEEE 29119** (niveles, técnicas de 29119-4, criterios, defectos e informe): `plan-pruebas-29119.md`
- [x] Requisitos RF/RNF y matriz de trazabilidad **ISO/IEC/IEEE 29148**: `matriz-trazabilidad-29148.md`
- [x] Declaración de aplicabilidad de controles **ISO/IEC 27001:2022 Anexo A** con Ley 1581 de 2012: `controles-27001.md`
- [x] Mapeo del proceso **ISO/IEC 29110** (gestión del proyecto e implementación del software): `proceso-29110.md`
- [x] Registro de riesgos **ISO 31000**: `riesgos-31000.md`

### Brechas por cerrar
- [ ] **(P)** Pruebas de valores límite faltantes (29119-4): capital = 100.000,00 aceptado; contraseña de 7, 8, 72 y 73 caracteres; variación de ±100 en el motor
- [ ] Prueba concurrente del advisory lock de mercado (RF-07)
- [ ] Completar la tabla de decisión de roles: IT_MANAGER en `/api/audit` y en `/api/users/**`
- [ ] **(P)** Protección de la rama `main`: PR obligatorio y los checks de CI requeridos (27001 A.8.4 y A.8.32)
- [ ] Plantillas de *issue* (defecto con severidad) y de PR (requisito y prueba) en `.github/`
- [ ] Procedimiento de supresión definitiva de datos personales y aviso de privacidad (Ley 1581, 27001 A.5.34)
- [ ] Metas de desempeño medidas con k6 (25010 §2.2), junto con la Fase 6
- [ ] Respaldo y restauración probados (25010 recuperabilidad; 27001 A.8.13), junto con la Fase 5
- [ ] Actualizar al cierre de cada fase: evaluación 25010 (§3), informe de pruebas (§8), matriz de trazabilidad y registro de riesgos

## Fase 2 — IA integrada ✅

- [x] Interfaz `AiAlertGenerator` con implementaciones intercambiables elegidas por `AI_PROVIDER`: `template` (reglas, sin llamadas externas; por defecto) y `gemini`
- [x] Prompt versionado (`prompts/alert-v1.txt`) con los datos de la simulación; respuesta en JSON validada (`title` y `content`). Las noticias se agregan en la Fase 3
- [x] Generación **asíncrona** después del commit (`@TransactionalEventListener` + `@Async`); nunca bloquea ni revierte la simulación
- [x] Migración V3 `ai_alerts`: modelo, versión del prompt, tokens, costo, estado (`PENDING`, `DONE`, `FAILED`) y motivo del fallo
- [x] `GET /api/simulations/{id}/alert` (202 mientras está `PENDING`) y `GET /api/me/alerts`
- [x] Timeouts, reintentos con backoff (`ResilientCall`) y límite de gasto mensual configurable (`AI_MONTHLY_BUDGET_USD`)
- [x] Aviso legal fijo: "Contenido generado por IA; no constituye asesoría financiera"
- [x] Pruebas con un proveedor falso; nada de llamadas reales en CI
- [ ] (Opcional) Asistente de preguntas sobre el historial propio: `POST /api/ai/ask`

## Fase 3 — Noticias ✅

- [x] Interfaz `NewsProvider` con Alpha Vantage `NEWS_SENTIMENT` (ticker QQQ como proxy del NASDAQ 100) y un proveedor `fixed` para demo y CI (`NEWS_PROVIDER`)
- [x] Tarea `@Scheduled` (`NEWS_REFRESH_CRON`, cada 6 h por defecto) y carga al arrancar; tope diario de llamadas (`NEWS_MAX_DAILY_CALLS`) para no agotar la cuota compartida con el mercado
- [x] Migración V4 `news_articles`, deduplicada por URL
- [x] `GET /api/news` paginado, con filtros por fecha y sentimiento; `POST /api/news/refresh` (ADMIN)
- [x] Indicador de sentimiento del día, agregado por calc-engine (`POST /calculations/sentiment`) y expuesto en `GET /api/news/sentiment`
- [x] La IA toma los titulares de la BD (prompt `alert-v2`) en vez de llamar a la API cada vez

## Fase 4 — Notificaciones ✅

- [x] Migración V5 `notifications` y `notification_preferences`
- [x] Bandeja en la app: `GET /api/me/notifications` (`?unread=true`), `GET /api/me/notifications/unread-count`, `PATCH /api/me/notifications/{id}/read`, `POST /api/me/notifications/read-all`
- [x] Correo con Spring Mail y plantilla Thymeleaf (`templates/mail/notification.html`), con el SMTP del cliente en `.env` (`MAIL_HOST`…); sin `MAIL_HOST` solo hay bandeja
- [x] Preferencias: `GET/PUT /api/me/notification-preferences` (canales y umbral de variación entre 0,10 % y 100 %)
- [x] Disparadores: alerta IA lista, variación diaria ≥ umbral (una vez por fecha de mercado) y eventos de seguridad a IT_MANAGER/ADMIN (`ACCOUNT_LOCKED`, `REFRESH_TOKEN_REUSED`). El tipo `LICENSE_EXPIRING` queda listo para la Fase 5
- [x] Envío con reintentos (backoff exponencial, `max-attempts`) y estado (`PENDING`, `SENT`, `FAILED`); patrón outbox en BD
- [ ] (Opcional) Notificaciones en tiempo real con SSE: `GET /api/me/notifications/stream`

## Fase 5 — Producto comercial (instalación por cliente) ✅

- [x] **Licencia**: JSON firmado con Ed25519 (`ritech-license-v1`: empresa, plan, `maxUsers`, `issuedAt`, `expiresAt`); la firma se verifica otra vez en cada arranque
- [x] Herramienta interna `tools/license/LicenseTool.java` (keygen e issue, solo JDK); la clave privada vive fuera del repositorio
- [x] `LICENSE_MODE=enforced`: modo solo lectura si la licencia falta o venció (`402 LICENSE_EXPIRED`); `disabled` para desarrollo, demo y CI
- [x] `GET /api/license` (IT_MANAGER/ADMIN) y `POST /api/license` (IT_MANAGER/ADMIN; el ADMIN también, para el primer arranque sin IT_MANAGER)
- [x] Módulos por plan (Básico, Profesional, Empresarial) aplicados por `LicenseInterceptor` (`403 MODULE_NOT_LICENSED`) y en los procesos en segundo plano; tope de usuarios (`409 LICENSE_USER_LIMIT`); aviso a 30 y 7 días
- [x] Escenarios y proyección (Empresarial): `POST /api/projections` → calc-engine `POST /calculations/projection` (P10/P50/P90 y Monte Carlo con NumPy)
- [x] Configuración por instalación: `GET /api/settings/branding` (público) y `PUT` (ADMIN)
- [x] Exportar a CSV (UTF-8 con BOM, protegido contra inyección de fórmulas) el historial propio, el global (ADMIN) y la auditoría
- [x] Respaldos: servicio `pg-backup` (`pg_dump` diario y retención configurable) y `restore.sh`; el CI prueba la restauración en cada ejecución
- [x] Imágenes versionadas en `ghcr.io` con `.github/workflows/release.yml` (tag `vX.Y.Z`)
- [x] `docs/instalacion-cliente.md`: requisitos, instalación, TLS con Caddy, respaldos y actualización (la guía de Railway se agrega aparte, con la rama `despliegue`)
- [x] `docs/manual-operacion.md` para el IT_MANAGER del cliente
- [x] `docs/terminos-de-uso.md` con el aviso de que el software no constituye asesoría financiera

## Fase 6 — Endurecimiento y entrega al frontend

- [ ] Rate limiting por IP y usuario (Bucket4j), con más rigor en `/api/auth/login`
- [ ] Cabeceras de seguridad, CORS solo para el dominio del frontend y HTTPS obligatorio detrás del proxy
- [ ] Revisión OWASP ASVS nivel 2 y escaneo de dependencias (Dependabot, OWASP Dependency-Check, Trivy sobre las imágenes)
- [ ] Contrato OpenAPI versionado (`/v3/api-docs` exportado a `openapi.yaml` en cada release) para el repo del frontend
- [ ] Versionado de la API (`/api/v1`) antes de la primera venta
- [ ] Métricas en Prometheus (`/actuator/prometheus`) y un dashboard básico opcional
- [ ] Pruebas de carga con k6 sobre `POST /api/simulations`

---

## Definición de "hecho" (para cada tarea)
1. El código está en inglés y la documentación afectada, actualizada en español.
2. Tiene pruebas y el CI está en verde (incluido el smoke test con Docker).
3. Los endpoints nuevos están documentados en Swagger, con su `code` de error en `logica-negocio.md`.
4. Los cambios de esquema van en una migración Flyway nueva.
5. No hay secretos en el repositorio.
6. Si agrega o cambia un requisito, la [matriz de trazabilidad](docs/calidad/matriz-trazabilidad-29148.md) queda actualizada (ISO/IEC/IEEE 29148) y las pruebas nuevas usan particiones y valores límite (ISO/IEC/IEEE 29119-4).
