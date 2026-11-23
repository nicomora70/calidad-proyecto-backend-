# Despliegue (Railway + Vercel)

## Arquitectura desplegada

```
Frontend (https://<front>.vercel.app)  ──HTTPS + Bearer──▶  api (https://<api>.up.railway.app)
                                                             ├─ red privada ─▶ calc-engine (sin dominio público)
                                                             └─ red privada ─▶ Postgres (Railway)
```

El backend se despliega primero; el frontend solo necesita su URL. Las dos piezas se unen con
dos variables que se cambian en la plataforma, sin tocar código: `CORS_ALLOWED_ORIGINS` (api) y
`VITE_API_URL` (frontend).

## 1. Backend en Railway

1. **New Project → Deploy from GitHub repo**, elegir este repositorio.
2. **Postgres**: `+ New → Database → PostgreSQL`.
3. **Servicio `calc-engine`**: `+ New → GitHub repo`, *Root Directory* = `calc-engine`.
   Variable `PORT=8000`. No generar dominio público: solo lo usa la API por la red privada
   (uvicorn escucha en `::`, IPv4 e IPv6, como exige la red privada de Railway).
4. **Servicio `api`**: `+ New → GitHub repo`, *Root Directory* = `api-java`.
   - *Settings → Healthcheck Path* = `/readyz` (Actuator vive en el puerto interno 8081, que no se publica).
   - *Networking → Generate Domain*. Railway inyecta `PORT` y la API lo usa (`server.port: ${PORT:8080}`).
5. Variables del servicio `api` (pestaña *Variables*; **no** se sube el `.env`):

| Variable | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DATABASE_USER` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `CALC_ENGINE_URL` | `http://${{calc-engine.RAILWAY_PRIVATE_DOMAIN}}:${{calc-engine.PORT}}` |
| `JWT_SECRET` | salida de `openssl rand -base64 48` |
| `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` | admin inicial (contraseña temporal de 8+ caracteres) |
| `CORS_ALLOWED_ORIGINS` | URL exacta del frontend, sin `/` final (varias separadas por coma). Mientras no exista, `http://localhost:3000` |
| `REQUIRE_HTTPS` | `true` (las sondas `/livez` y `/readyz` quedan exentas del redirect) |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `ecs` (logs en JSON) |

Con los valores por defecto la demo no depende de servicios externos: `MARKET_PROVIDER=fixed`,
`NEWS_PROVIDER=fixed`, `AI_PROVIDER=template`, `LICENSE_MODE=disabled` y correo desactivado
(sin `MAIL_HOST`). Para datos reales: `MARKET_PROVIDER=alphavantage` + `ALPHAVANTAGE_API_KEY`.

6. Comprobar: `https://<api>/readyz` responde `UP`, `https://<api>/api/v1/health` responde
   `{"status":"UP"}` y `https://<api>/swagger-ui.html` abre.

> `JWT_SECRET`, las contraseñas y las API keys van **solo** en las variables de la plataforma.
> `.env` es para correr el proyecto en local y está en `.gitignore`.

## 2. Frontend en Vercel

1. **Add New → Project**, importar `calidad-proyecto-frontend` (preset Vite).
2. Variable `VITE_API_URL=https://<api>.up.railway.app` (sin `/` final). Vite la incrusta al compilar:
   si cambia, hay que redesplegar.
3. El repo incluye `vercel.json` con el rewrite a `index.html`, para que recargar una ruta interna no dé 404.
4. Copiar la URL pública del frontend en `CORS_ALLOWED_ORIGINS` del servicio `api`; Railway lo redespliega.

## Contrato con el frontend

Rutas `/api/v1`, autenticación Bearer con refresh, cabeceras expuestas, límites de peticiones (`429`
con `Retry-After`) y catálogo de errores: ver [integracion-frontend.md](integracion-frontend.md).

## Riesgos para la demo

- **Primer arranque**: Flyway crea las tablas y se crea el admin inicial. Inicia sesión y cambia su
  contraseña antes de la sustentación.
- **Arranque en frío**: la API Java tarda entre 30 y 60 s en arrancar. Ábrela unos minutos antes.
- **CORS**: si el navegador muestra un error de CORS, la URL del frontend en `CORS_ALLOWED_ORIGINS`
  no coincide exactamente (protocolo, subdominio o `/` final).
- **Alpha Vantage**: la capa gratuita permite 25 consultas al día. Con `MARKET_PROVIDER=fixed`
  la demo no depende de internet ni de esa cuota.
