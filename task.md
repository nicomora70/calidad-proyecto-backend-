# task.md — Hoja de ruta del backend RiTech

> **Convenciones:** el código va en **inglés** (clases, endpoints, tablas, variables); la
> documentación y este archivo, en **español**. Este repositorio es **solo backend**: el
> frontend vive en otro repositorio y consume el contrato OpenAPI.
>
> Documentación relacionada: [Lógica de negocio](docs/logica-negocio.md) · [Arquitectura](docs/arquitectura.md) · [Modelo de datos](docs/modelo-datos.md)
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
- [x] Pruebas: pytest (14) y JUnit/MockMvc (7)
- [x] GitHub Actions: pruebas de ambos servicios y smoke test con `docker compose`

## Fase 1 — Núcleo vendible (MVP)

### Seguridad y autenticación **(P)**
- [ ] Agregar `spring-boot-starter-security` y `spring-boot-starter-oauth2-resource-server`
- [ ] `POST /api/auth/login` → `accessToken` (JWT de 15 min) y `refreshToken` (7 días, guardado como hash)
- [ ] `POST /api/auth/refresh` con rotación de refresh tokens y revocación del anterior
- [ ] `POST /api/auth/logout` (revoca el refresh token)
- [ ] `POST /api/auth/change-password`; obligatorio si `must_change_password = true`
- [ ] Firma de los JWT con clave por instalación (`JWT_SECRET` o par RSA en un volumen)
- [ ] Bloqueo tras 5 intentos fallidos durante 15 minutos (`423 ACCOUNT_LOCKED`)
- [ ] Autorización por rol con `@PreAuthorize`, según la matriz de `logica-negocio.md`
- [ ] Sacar `userId` del cuerpo de `POST /api/simulations`: se toma del JWT
- [ ] Migración V2: `refresh_tokens`, columnas de bloqueo y `simulations.user_id NOT NULL`

### Usuarios **(P)**
- [ ] Entidad `User` y `UserRepository`, contraseñas con BCrypt
- [ ] Crear el ADMIN inicial desde `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` si no hay usuarios
- [ ] `POST /api/users` (ADMIN): crear usuario
- [ ] `GET /api/users` (ADMIN): paginado, con filtros por rol y estado
- [ ] `GET /api/users/{id}`, `PATCH /api/users/{id}` (nombre y estado activo)
- [ ] `PATCH /api/users/{id}/role` con la regla `LAST_ADMIN`
- [ ] `DELETE /api/users/{id}` como borrado lógico
- [ ] `GET /api/me`: perfil del usuario autenticado
- [ ] Auditoría de LOGIN_SUCCESS, LOGIN_FAILED, USER_CREATED, USER_DELETED y ROLE_CHANGED

### Simulaciones e historial
- [ ] `GET /api/simulations/{id}` (dueño o ADMIN)
- [ ] `GET /api/me/simulations` con filtros `from` y `to`
- [ ] El ADMIN puede filtrar por `userId`; el INVESTOR solo ve las suyas
- [ ] Dashboard con filtros por fecha y usuario, solo para ADMIN
- [ ] Resilience4j: reintento y circuit breaker hacia el calc-engine y Alpha Vantage
- [ ] Bloqueo con `SELECT ... FOR UPDATE` o restricción única para evitar dobles llamadas a Alpha Vantage cuando la caché vence

### Auditoría
- [ ] `GET /api/audit` (IT_MANAGER/ADMIN), paginado y con filtros por acción, actor y fecha
- [ ] Incluir IP y user-agent en `details`

### Calidad
- [ ] Pruebas de integración con **Testcontainers** (Postgres real y migraciones)
- [ ] Prueba de contrato api ↔ calc-engine (cliente contra un contenedor real)
- [ ] Cobertura con JaCoCo ≥ 80 % en servicios, ejecutada en CI
- [ ] Linters: Spotless (Java) y Ruff (Python) en CI
- [ ] Logs en JSON con `traceId` por petición

## Fase 2 — IA integrada

- [ ] Interfaz `AiAlertGenerator` con implementaciones intercambiables (Claude, Gemini y otras), elegida por `AI_PROVIDER`
- [ ] Prompt versionado con noticias recientes y datos de la simulación; respuesta en JSON validada (`title` y `content`)
- [ ] Generación **asíncrona** después de guardar la simulación (`@Async` o cola en BD); nunca bloquea ni revierte la simulación
- [ ] Migración V3 `ai_alerts`: modelo, tokens, costo y estado (`PENDING`, `DONE`, `FAILED`)
- [ ] `GET /api/simulations/{id}/alert` y `GET /api/me/alerts`
- [ ] Timeouts, reintentos con backoff y límite de gasto mensual configurable
- [ ] Aviso legal fijo: "Contenido generado por IA; no constituye asesoría financiera"
- [ ] Pruebas con un proveedor falso; nada de llamadas reales en CI
- [ ] (Opcional) Asistente de preguntas sobre el historial propio: `POST /api/ai/ask`

## Fase 3 — Noticias

- [ ] Interfaz `NewsProvider` con implementación de Alpha Vantage `NEWS_SENTIMENT` (tickers QQQ y NASDAQ)
- [ ] Tarea `@Scheduled` cada N horas (`NEWS_REFRESH_CRON`) que respeta la cuota diaria
- [ ] Migración V4 `news_articles`, deduplicada por URL
- [ ] `GET /api/news` paginado, con filtros por fecha y sentimiento
- [ ] Indicador de sentimiento del día, agregado por calc-engine (`POST /calculations/sentiment`)
- [ ] La IA (Fase 2) toma las noticias de la BD en vez de llamar a la API cada vez

## Fase 4 — Notificaciones

- [ ] Migración V5 `notifications` y `notification_preferences`
- [ ] Bandeja en la app: `GET /api/me/notifications`, `PATCH /api/me/notifications/{id}/read`, `POST /api/me/notifications/read-all`
- [ ] Correo con Spring Mail y plantillas (Thymeleaf), configurado por SMTP del cliente en `.env`
- [ ] Preferencias: `GET/PUT /api/me/notification-preferences` (canales y umbral de variación)
- [ ] Disparadores: alerta IA lista, variación diaria mayor al umbral, licencia a 30 días de vencer
- [ ] Envío con reintentos y estado (`PENDING`, `SENT`, `FAILED`); patrón outbox en BD
- [ ] (Opcional) Notificaciones en tiempo real con SSE: `GET /api/me/notifications/stream`

## Fase 5 — Producto comercial (instalación por cliente)

- [ ] **Licencia**: archivo JSON firmado con Ed25519 (empresa, `maxUsers`, `modules`, `expiresAt`)
- [ ] Herramienta interna (fuera de este repo o en `tools/`) para emitir licencias con la clave privada
- [ ] Validación al arrancar y modo solo lectura si está vencida (`402 LICENSE_EXPIRED`)
- [ ] `GET /api/license` (IT_MANAGER/ADMIN) y `POST /api/license` (IT_MANAGER) para renovar
- [ ] Módulos por paquete (Básico, Profesional, Empresarial) que habilitan o deshabilitan endpoints
- [ ] Escenarios y proyección (Empresarial): `POST /api/projections` → calc-engine `POST /calculations/projection` (percentiles y Monte Carlo con NumPy)
- [ ] Configuración por instalación: `GET /api/settings/branding` (nombre, logo, colores) para el frontend
- [ ] Exportar a CSV o Excel el historial y la auditoría
- [ ] Respaldos: servicio `pg-backup` en compose con `pg_dump` diario y retención configurable; script de restauración
- [ ] Publicar imágenes versionadas (`ghcr.io/.../ritech-api:x.y.z`) con un workflow de release por tag
- [ ] `docs/despliegue.md`: requisitos del servidor, instalación, actualización, respaldos y TLS con reverse proxy (Caddy o Nginx)
- [ ] `docs/manual-operacion.md` para el IT_MANAGER del cliente
- [ ] Términos de uso y aviso de que el software no constituye asesoría financiera

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
