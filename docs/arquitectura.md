# Arquitectura

```
                 ┌──────────────── instalación del cliente (docker compose) ───────────────┐
  Frontend       │                                                                         │
 (otro repo) ──HTTPS──▶  api  (Java 21 · Spring Boot 3.5)  ──JDBC──▶  postgres 16           │
                 │        │  puerto 8080 (único expuesto)                                    │
                 │        ├──HTTP interno──▶  calc-engine (Python 3.12 · FastAPI)           │
                 │        │                   cálculos puros, sin BD, sin puerto público    │
                 │        └──HTTPS──▶ Alpha Vantage (mercado y noticias) · LLM · SMTP        │
                 └─────────────────────────────────────────────────────────────────────────┘
```

## Responsabilidades

| Servicio | Hace | No hace |
|---|---|---|
| **api** (Java) | API pública, seguridad (JWT), roles, reglas de negocio, persistencia, integraciones externas, tareas programadas, licencia, auditoría | Matemática financiera |
| **calc-engine** (Python) | Rendimiento x2, estadísticas, escenarios y Monte Carlo, todo con `Decimal` | Guardar datos, autenticar, llamar a terceros |
| **postgres** | Única fuente de verdad; esquema versionado con Flyway | — |

¿Por qué separar la matemática en Python? Por el ecosistema numérico (NumPy y SciPy para
escenarios y Monte Carlo) y porque se puede probar de forma aislada: funciones puras,
deterministas y sin estado.

## Stack

- **Java**: Spring Boot 3.5.16, Spring Web, Spring Data JPA (Hibernate), Flyway, Bean Validation,
  springdoc-openapi (Swagger), Actuator, RestClient, Spring Security con OAuth2 Resource Server
  (JWT HS256), Resilience4j, Testcontainers, JaCoCo y Spotless. Próximas fases: Spring Mail y
  `@Scheduled`.
- **Python**: FastAPI, Pydantic v2, Uvicorn, pytest y Ruff. A partir de la Fase 2: NumPy.
- **Infraestructura**: Docker multi-stage con usuario no root, healthchecks y GitHub Actions
  (pruebas y smoke test con `docker compose`).

## Contrato api → calc-engine

Los campos van en `snake_case` y los decimales como string para no perder precisión.

`POST /calculations/returns`
```json
{ "initial_capital": "1000000", "market_variation_pct": "1.5" }
→ { "initial_capital": "1000000.00", "market_variation_pct": "1.5", "leverage": "2",
    "return_pct": "3.0000", "final_amount": "1030000.00", "profit": "30000.00", "capped_loss": false }
```

`POST /calculations/statistics`
```json
{ "samples": [ { "initial_capital": "100000", "return_pct": "4" } ] }
→ { "count": 1, "total_capital": "...", "total_profit": "...", "mean_return_pct": "...",
    "weighted_return_pct": "...", "stdev_return_pct": "...", "best_return_pct": "...",
    "worst_return_pct": "...", "max_drawdown_pct": "...", "win_rate_pct": "..." }
```

Timeout de 5 s. Ante un fallo, la API responde `503 CALC_ENGINE_UNAVAILABLE`.

## Resiliencia y trazabilidad

- **Reintentos y circuit breaker** (`common/ResilientCall`, Resilience4j) en las llamadas al
  calc-engine y a Alpha Vantage: 3 intentos con backoff exponencial (200 ms y 400 ms) solo
  ante fallos de red o 5xx. Si falla el 50 % de las últimas 10 llamadas (mínimo 5), el circuito
  se abre 30 s y las peticiones fallan de inmediato con `503`/`502`, sin cargar al servicio caído.
- **Trace id por petición** (`common/RequestIdFilter`): se reutiliza el `X-Request-Id` del
  proxy o del frontend si es válido (8–64 caracteres `[A-Za-z0-9._-]`); si no, se genera un UUID.
  Va en la cabecera de respuesta, en el campo `traceId` de todo error RFC 7807, en los logs y en
  las llamadas al calc-engine.
- **Logs**: en Docker los dos servicios escriben JSON (formato ECS en Java), una línea por evento
  con `traceId`, listos para Loki, ELK o CloudWatch. En desarrollo local, Java usa texto plano
  con `[traceId]` en cada línea.

## API pública (para el equipo de frontend)

### Autenticación

```
POST /api/auth/login            {email, password}           → TokenResponse
POST /api/auth/refresh          {refreshToken}              → TokenResponse (el anterior queda revocado)
POST /api/auth/logout           {refreshToken}              → 204
POST /api/auth/change-password  {currentPassword, newPassword}  (con Bearer) → TokenResponse

TokenResponse = { accessToken, tokenType: "Bearer", expiresIn (s), refreshToken,
                  refreshTokenExpiresAt, user: { id, fullName, email, role, active,
                  mustChangePassword, lastLoginAt, createdAt } }
```

Flujo recomendado en el frontend:
1. Login. Si `user.mustChangePassword` es `true`, mostrar la pantalla de cambio de contraseña y
   reemplazar los tokens por los que devuelve `change-password`.
2. Enviar `Authorization: Bearer <accessToken>` en cada petición.
3. Ante un `401 TOKEN_EXPIRED`, llamar a `/api/auth/refresh` **una sola vez** y reintentar; si
   responde `401 INVALID_REFRESH_TOKEN`, volver al login.
4. Guardar el refresh token fuera del alcance de scripts de terceros y usar siempre el último
   recibido, porque cada uno sirve una sola vez.

### Endpoints por rol

| Endpoint | Rol |
|---|---|
| `GET /api/me`, `GET /api/me/simulations`, `GET /api/me/alerts` | cualquiera autenticado |
| `POST /api/simulations` | INVESTOR, ADMIN |
| `GET /api/simulations/{id}`, `GET /api/simulations/{id}/alert` | dueño o ADMIN |
| `GET /api/simulations`, `GET /api/dashboard/statistics` | ADMIN |
| `/api/users/**` | ADMIN |
| `GET /api/audit` | ADMIN, IT_MANAGER |
| `GET /api/news`, `GET /api/news/sentiment` | cualquiera autenticado |
| `POST /api/news/refresh` | ADMIN |

### Convenciones

- Documentación en vivo: `http://<host>:8080/swagger-ui.html` (botón **Authorize** para pegar el token) · JSON en `/v3/api-docs`.
- Prefijo `/api`, JSON en `camelCase`, errores RFC 7807 con `code` (ver `logica-negocio.md`).
- Paginación de Spring: `?page=0&size=20&sort=createdAt,desc` → `{ content, page: { size, number, totalElements, totalPages } }`.
- CORS: solo los orígenes de `CORS_ALLOWED_ORIGINS`.

## Organización del código Java (por funcionalidad)

```
co.ritech
├── config       propiedades (RitechProperties), CORS, Clock
├── common       BusinessException, ApiExceptionHandler (RFC 7807)
├── health       /api/health
├── market       proveedor de mercado (Alpha Vantage / fijo) + caché
├── engine       cliente del calc-engine
├── simulation   entidad, servicio, controlador
├── dashboard    estadísticas globales
├── audit        registro de auditoría
├── user         usuarios, roles, /api/me, admin inicial
├── auth         login, refresh tokens, logout, cambio de contraseña
├── security     configuración de Spring Security, JWT, CurrentUser
├── license      (Fase 5) validación de la licencia
├── ai / news / notification   (Fases 2–4)
```

Cada módulo expone un servicio; los controladores no tocan repositorios directamente,
salvo en lecturas simples.

## Decisiones (ADR resumidos)

1. **Instalación por cliente en lugar de SaaS multiempresa**: aislamiento total de los datos,
   más fácil de vender a empresas reguladas y sin lógica multiempresa en el código.
2. **Flyway con `ddl-auto=validate`**: el esquema se versiona y Hibernate solo verifica que
   coincida con las entidades.
3. **Proveedor de mercado intercambiable** (`MarketDataProvider`): `fixed` para demos y
   pruebas, `alphavantage` en producción; después se pueden agregar proveedores de pago sin
   tocar la lógica.
4. **Simulaciones inmutables**: registro financiero; no se editan ni se borran.
5. **Reloj inyectado (`Clock`)**: las pruebas son deterministas.
