# RiTech — Backend

Software de apoyo a decisiones de inversión. Las inversiones de RiTech siguen el
**NASDAQ 100 con apalancamiento x2**: si el índice sube 2 %, la inversión gana 4 %; si
baja 2 %, pierde 4 %. Se vende como **instalación por cliente** (una instancia Docker
por empresa).

- **api** — Java 21 · Spring Boot 3.5 · JPA · Flyway (API pública, reglas de negocio, persistencia)
- **calc-engine** — Python 3.12 · FastAPI (toda la matemática financiera, interno)
- **postgres** — PostgreSQL 16

El frontend vive en otro repositorio y consume esta API.

📄 [Lógica de negocio](docs/logica-negocio.md) · [Arquitectura](docs/arquitectura.md) · [Modelo de datos](docs/modelo-datos.md) · [Hoja de ruta (task.md)](task.md)

## Levantar con Docker

Solo se necesita Docker (con Compose v2).

```bash
cp .env.example .env        # ajustar claves y valores
docker compose up -d --build
```

| URL | Qué es |
|---|---|
| http://localhost:8080/api/health | Estado de la API |
| http://localhost:8080/swagger-ui.html | Documentación interactiva |
| http://localhost:8080/actuator/health | Salud (incluye la BD) |

Por defecto, `MARKET_PROVIDER=fixed` usa una variación constante (`FIXED_VARIATION_PCT`)
para poder probar sin clave. Para datos reales, se pone `MARKET_PROVIDER=alphavantage` y
`ALPHAVANTAGE_API_KEY` (gratis en <https://www.alphavantage.co/support/#api-key>).

### Probar una simulación

```bash
curl -X POST http://localhost:8080/api/simulations \
  -H "Content-Type: application/json" \
  -d '{"initialCapital": 1000000}'

curl "http://localhost:8080/api/simulations?page=0&size=10"
curl http://localhost:8080/api/dashboard/statistics
```

## Desarrollo local (sin Docker)

```bash
# calc-engine
cd calc-engine
python -m venv .venv && . .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements-dev.txt
pytest
uvicorn app.main:app --port 8000

# api (necesita un Postgres local con la BD "ritech")
cd api-java
./mvnw verify            # Windows: mvnw.cmd verify
./mvnw spring-boot:run
```

Para levantar solo la base de datos: `docker compose up -d postgres`. Ojo: en
`docker-compose.yml` Postgres no publica el puerto 5432; para conectarse desde el
equipo hay que agregar `ports: ["5432:5432"]`.

## Estado

Fase 0 completa: esqueleto funcional con simulación de extremo a extremo. La
autenticación JWT y la gestión de usuarios corresponden a la **Fase 1** (ver
[task.md](task.md)); **mientras tanto los endpoints son públicos**, así que no se debe
exponer esta versión fuera de un entorno de desarrollo.
