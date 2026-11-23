# RiTech — Backend

Software de apoyo a decisiones de inversión. Las inversiones de RiTech siguen el
**NASDAQ 100 con apalancamiento x2**: si el índice sube 2 %, la inversión gana 4 %; si
baja 2 %, pierde 4 %. Se vende como **instalación por cliente** (una instancia Docker
por empresa).

- **api** — Java 21 · Spring Boot 3.5 · Spring Security (JWT) · JPA · Flyway (API pública, reglas de negocio, persistencia)
- **calc-engine** — Python 3.12 · FastAPI (toda la matemática financiera, interno)
- **postgres** — PostgreSQL 16

El frontend vive en otro repositorio y consume esta API.

📄 [Lógica de negocio](docs/logica-negocio.md) · [Arquitectura y API](docs/arquitectura.md) · [Modelo de datos](docs/modelo-datos.md) · [Hoja de ruta (task.md)](task.md)

## Levantar con Docker

Solo se necesita Docker (con Compose v2).

```bash
cp .env.example .env
# En .env, como mínimo:
#   JWT_SECRET=<salida de: openssl rand -base64 48>
#   BOOTSTRAP_ADMIN_EMAIL=admin@empresa.com
#   BOOTSTRAP_ADMIN_PASSWORD=<contraseña temporal de 8+ caracteres>
docker compose up -d --build
```

| URL | Qué es |
|---|---|
| http://localhost:8080/api/health | Estado de la API (público) |
| http://localhost:8080/swagger-ui.html | Documentación interactiva (botón **Authorize** para el token) |
| http://localhost:8080/actuator/health | Salud, incluida la BD (público) |

Por defecto, `MARKET_PROVIDER=fixed` usa una variación constante (`FIXED_VARIATION_PCT`)
para poder probar sin clave. Para datos reales, se pone `MARKET_PROVIDER=alphavantage` y
`ALPHAVANTAGE_API_KEY` (gratis en <https://www.alphavantage.co/support/#api-key>).

### Primer uso (con curl y jq)

```bash
API=http://localhost:8080
json() { curl -s -X POST "$API$1" -H 'Content-Type: application/json' ${3:+-H "Authorization: Bearer $3"} -d "$2"; }

# 1. El admin inicial entra con la contraseña temporal y la cambia
TMP=$(json /api/auth/login '{"email":"admin@empresa.com","password":"<temporal>"}' | jq -r .accessToken)
ADMIN=$(json /api/auth/change-password '{"currentPassword":"<temporal>","newPassword":"<definitiva>"}' "$TMP" | jq -r .accessToken)

# 2. El admin crea un inversionista (con su propia contraseña temporal)
json /api/users '{"fullName":"Ana","email":"ana@empresa.com","role":"INVESTOR","temporaryPassword":"temporal-123"}' "$ADMIN"

# 3. El inversionista entra, cambia su contraseña y simula
TMP=$(json /api/auth/login '{"email":"ana@empresa.com","password":"temporal-123"}' | jq -r .accessToken)
INV=$(json /api/auth/change-password '{"currentPassword":"temporal-123","newPassword":"ana-segura-2026"}' "$TMP" | jq -r .accessToken)
json /api/simulations '{"initialCapital": 1000000}' "$INV"

curl -s "$API/api/me/simulations" -H "Authorization: Bearer $INV"
curl -s "$API/api/dashboard/statistics" -H "Authorization: Bearer $ADMIN"
```

## Desarrollo local (sin Docker)

```bash
# calc-engine
cd calc-engine
python -m venv .venv && . .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
pytest
uvicorn app.main:app --port 8000

# api (necesita un Postgres local con la BD "ritech" y JWT_SECRET definido)
cd api-java
./mvnw verify            # Windows: mvnw.cmd verify
./mvnw spring-boot:run
```

`./mvnw verify` corre las pruebas unitarias y las de integración (`*IT`, con Testcontainers).
Las de integración se omiten solas si no hay Docker disponible; en CI siempre corren.

`verify` también falla si:
- el código no está formateado → `./mvnw spotless:apply` lo corrige;
- alguna clase `*Service` tiene menos del 80 % de líneas cubiertas por pruebas unitarias.
  El reporte queda en `api-java/target/site/jacoco/index.html`.

En Python: `ruff check --fix . && ruff format .` antes de subir cambios.

Para levantar solo la base de datos: `docker compose up -d postgres`. Ojo: en
`docker-compose.yml` Postgres no publica el puerto 5432; para conectarse desde el
equipo hay que agregar `ports: ["5432:5432"]`.

## Estado

- **Fase 0** ✅ base del proyecto
- **Fase 1** ✅ autenticación JWT, usuarios, roles, historial, auditoría, resiliencia, logs en
  JSON con `traceId` y controles de calidad en CI
- **Fase 2** ✅ alertas con IA asíncronas (`AI_PROVIDER=template` o `gemini`) con límite de gasto
- **Fase 3** ✅ noticias programadas (`NEWS_PROVIDER=fixed` o `alphavantage`), sentimiento del día y titulares en la IA
- **Fase 4** ✅ notificaciones en la app y por correo (SMTP opcional), con preferencias, outbox y alertas de seguridad
- Siguientes: producto comercial (licencia, proyecciones, respaldos) y endurecimiento (ver [task.md](task.md))
