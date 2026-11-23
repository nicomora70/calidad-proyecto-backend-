# Integración con el frontend

Contrato entre este backend y el repositorio del frontend.

## Contrato OpenAPI
- Versión de la API: **v1**. Todas las rutas empiezan por `/api/v1/` (antes `/api/`).
  Un cambio incompatible abrirá `/api/v2` y `/api/v1` se mantendrá mientras haya clientes que lo usen.
- Contrato en vivo: `GET /v3/api-docs` (JSON) o `GET /v3/api-docs.yaml`; Swagger UI en `/swagger-ui.html`.
- Contrato versionado: cada etiqueta `vX.Y.Z` adjunta `openapi.yaml` a su *GitHub Release*
  (workflow `release.yml`). En cada PR, el CI lo deja como artefacto `openapi`.
  El frontend puede generar sus tipos con `npx openapi-typescript openapi.yaml -o src/api/schema.d.ts`.

## Configuración
| Variable (backend) | Para qué |
|---|---|
| `CORS_ALLOWED_ORIGINS` | Dominio(s) exacto(s) del frontend, separados por coma (p. ej. `https://app.empresa.com`). Cualquier otro origen recibe 403 |
| `REQUIRE_HTTPS=true` | En producción: redirige HTTP a HTTPS y envía HSTS |
| `API_DOCS_ENABLED` | `false` oculta Swagger y `/v3/api-docs` en producción |

## Autenticación
1. `POST /api/v1/auth/login` → `accessToken` (15 min) + `refreshToken` (7 días) + `user`.
2. Cada llamada: `Authorization: Bearer <accessToken>`. No hay cookies, así que no se necesita CSRF.
3. Ante `401` con `code = TOKEN_EXPIRED`: `POST /api/v1/auth/refresh` con el refresh token (rota: hay que guardar el nuevo).
   `INVALID_REFRESH_TOKEN` → volver al login.
4. Si `user.mustChangePassword` es `true`, la única pantalla posible es la de cambio de contraseña
   (`403 PASSWORD_CHANGE_REQUIRED` en el resto).

## Cabeceras útiles (expuestas por CORS)
| Cabecera | Uso en el frontend |
|---|---|
| `X-Request-Id` | Se puede enviar uno propio; vuelve en la respuesta y en el `traceId` de los errores (soporte) |
| `Retry-After` | Segundos que hay que esperar tras un `429 RATE_LIMITED` |
| `X-RateLimit-Limit` / `X-RateLimit-Remaining` | Cupo por minuto y lo que queda |
| `Content-Disposition` | Nombre del archivo en los exportes CSV |

## Límites de peticiones
| Grupo | Clave | Por minuto (por defecto) |
|---|---|---|
| `login` y `refresh` | IP del cliente | 20 |
| Resto de llamadas anónimas | IP del cliente | 120 |
| Llamadas autenticadas | Usuario | 300 |

Al superarlos, la API responde `429` con `code = RATE_LIMITED`. El frontend debe mostrar
un aviso y reintentar tras `Retry-After`, nunca en bucle.

## Errores
Todos los errores son `application/problem+json` con `code` estable y `traceId`;
el catálogo completo está en [logica-negocio.md §6](logica-negocio.md#6-catálogo-de-errores-rfc-7807).
