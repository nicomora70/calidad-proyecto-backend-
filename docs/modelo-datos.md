# Modelo de datos (PostgreSQL)

El esquema se gestiona con Flyway en `api-java/src/main/resources/db/migration`.
**Nunca se edita una migración ya aplicada**: cada cambio es un archivo nuevo `V<n>__descripcion.sql`.

## Tablas actuales (V1 + V2)

```
users (1) ──< (N) simulations
users (1) ──< (N) refresh_tokens
audit_log ─ actor_id → users.id (lógico, sin FK, para que la auditoría sobreviva)
market_variations   (caché de la variación diaria por símbolo y fecha)
```

> V2 elimina las simulaciones de la Fase 0 que no tenían dueño (el endpoint era público) y
> vuelve `simulations.user_id` obligatorio.

### users
| Columna | Tipo | Notas |
|---|---|---|
| id | UUID PK | |
| full_name | VARCHAR(120) | |
| email | VARCHAR(150) | único (sin distinguir mayúsculas) entre usuarios no eliminados |
| role | VARCHAR(20) | `ADMIN` · `IT_MANAGER` · `INVESTOR` |
| password_hash | VARCHAR(100) | BCrypt |
| active | BOOLEAN | |
| failed_attempts | INTEGER | intentos fallidos consecutivos (V2) |
| locked_until | TIMESTAMPTZ NULL | bloqueo temporal por intentos (V2) |
| must_change_password | BOOLEAN | contraseña temporal pendiente de cambio (V2) |
| last_login_at | TIMESTAMPTZ NULL | (V2) |
| created_at / updated_at | TIMESTAMPTZ | |
| deleted_at | TIMESTAMPTZ NULL | borrado lógico |

### refresh_tokens (V2)
`user_id` FK → users · `token_hash` (SHA-256, único; el token nunca se guarda en claro) ·
`expires_at` · `revoked_at` · `created_at`.

### simulations (inmutable)
| Columna | Tipo | Notas |
|---|---|---|
| id | UUID PK | |
| user_id | UUID FK → users | `NOT NULL` desde V2 |
| initial_capital | NUMERIC(18,2) | > 0 |
| currency | VARCHAR(3) | COP |
| symbol / market_date | VARCHAR(10) / DATE | qué dato de mercado se usó |
| market_variation_pct | NUMERIC(10,4) | |
| leverage | NUMERIC(6,2) | 2 |
| return_pct | NUMERIC(10,4) | |
| final_amount | NUMERIC(18,2) | ≥ 0 |
| profit | NUMERIC(18,2) | |
| created_at | TIMESTAMPTZ | |

### market_variations
`(symbol, market_date)` es único · `change_pct NUMERIC(10,4)` · `source` (`ALPHA_VANTAGE`/`FIXED`) · `fetched_at`.

### audit_log (solo inserciones)
`actor_id`, `action` (p. ej. `SIMULATION_CREATED`), `entity_type`, `entity_id`, `details TEXT`,
`ip_address` y `user_agent` (V2), `created_at`.

## Tablas planeadas

| Migración | Tabla | Propósito |
|---|---|---|
| V3 (Fase 2) | `ai_alerts` | simulation_id, title, content, model, prompt_tokens, completion_tokens, cost_usd, status |
| V4 (Fase 3) | `news_articles` | url (única), title, summary, source, published_at, sentiment_score, sentiment_label |
| V5 (Fase 4) | `notifications` | user_id, type, channel, title, body, read_at, sent_at, status |
| V5 (Fase 4) | `notification_preferences` | user_id, email_enabled, in_app_enabled, variation_threshold_pct |
| V6 (Fase 5) | `license` | company_name, max_users, modules, expires_at, signature, loaded_at |
| V6 (Fase 5) | `settings` | clave/valor por instalación (marca, logo, textos legales) |
