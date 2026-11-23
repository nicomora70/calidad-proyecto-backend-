# Manual de operación (IT_MANAGER)

Tareas habituales del responsable técnico de la empresa cliente. La instalación inicial está
en [instalacion-cliente.md](instalacion-cliente.md).

## 1. Salud del sistema

| Qué revisar | Cómo | Esperado |
|---|---|---|
| API | `GET /api/v1/health` | `{"status":"UP"}` |
| Contenedores | `docker compose ps` | `postgres`, `calc-engine`, `api` y `pg-backup` en *running* (*healthy* donde aplique) |
| Logs | `docker compose logs --since 1h api` | JSON con `traceId`; filtrar por `"level":"ERROR"` |
| Espacio en disco | `df -h` y `du -sh backups/` | Menos del 80 % de uso |
| Salud detallada (BD, disco) | `docker compose exec api curl -s localhost:8081/actuator/health` | `"status":"UP"` |
| Métricas | `docker compose exec api curl -s localhost:8081/actuator/prometheus` | Formato Prometheus (`http_server_requests_seconds_*`, `jvm_*`, `hikaricp_*`) |

El puerto de gestión 8081 no se publica. Para llevar las métricas a un Prometheus propio, se
agrega ese servicio a la red `internal` del `docker-compose.yml` con el destino `api:8081`
y la ruta `/actuator/prometheus`.

Cuando un usuario reporta un error, la respuesta trae la cabecera `X-Request-Id`. Con ese valor
se busca la traza completa en los logs: `docker compose logs api | grep <id>`.

## 2. Licencia

- **Consultar**: `GET /api/v1/license` (IT_MANAGER o ADMIN). Muestra el estado (`ACTIVE`,
  `EXPIRING`, `EXPIRED`, `MISSING`), el plan, los módulos, los usuarios activos y los días restantes.
- **Avisos**: 30 y 7 días antes del vencimiento llega una notificación al IT_MANAGER y al ADMIN.
- **Renovar**: enviar el contenido del archivo `license.json` recibido de RiTech:
  ```bash
  curl -X POST https://api.empresa.com/api/v1/license \
       -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
       --data @license.json
  ```
  La renovación aplica de inmediato, sin reiniciar. También funciona con la licencia vencida.
- **Vencida**: el sistema pasa a **solo lectura**. Se puede consultar el historial, pero
  cualquier escritura responde `402 LICENSE_EXPIRED` (simular, crear usuarios, etc.). Iniciar
  sesión y renovar siguen disponibles.
- **Límite de usuarios**: al alcanzar `maxUsers` usuarios activos, crear otro responde
  `409 LICENSE_USER_LIMIT`. Desactivar o eliminar usuarios libera cupos.

| Plan | Módulos |
|---|---|
| Básico | Simulaciones, historial, dashboard |
| Profesional | Básico + alertas con IA, noticias, notificaciones, exportes CSV |
| Empresarial | Profesional + escenarios y proyección |

Si se usa un módulo que no está en el plan, la API responde `403 MODULE_NOT_LICENSED`.

## 3. Respaldos

- Revisar a diario que exista un respaldo reciente: `ls -lt backups/ | head`.
- **Una vez al mes**, hacer una restauración de prueba en una base aparte
  ([instalacion-cliente.md §4](instalacion-cliente.md#4-respaldos-y-restauración)) y anotar
  fecha y resultado.
- Llevar una copia de `backups/` fuera del servidor.

## 4. Seguridad

- Las notificaciones de tipo **SECURITY_EVENT** avisan de **cuentas bloqueadas** (5 intentos
  fallidos) y de **posible robo de sesión** (reutilización de un refresh token). Revisar el
  usuario afectado en la auditoría: `GET /api/v1/audit?action=ACCOUNT_LOCKED`.
- Exportar la auditoría para un control o una auditoría externa:
  `GET /api/v1/audit/export?from=2026-01-01&to=2026-12-31` (CSV). La exportación queda auditada.
- **Límite de peticiones**: por encima de `RATE_LIMIT_*_PER_MINUTE` (`.env.example`) la API responde
  `429 RATE_LIMITED`. Si muchos usuarios entran desde la misma IP pública (NAT de la oficina) y
  reciben 429 al iniciar sesión, se sube `RATE_LIMIT_AUTH_PER_MINUTE`. El límite usa la IP de
  `X-Forwarded-For`, así que el puerto 8080 solo debe ser accesible desde el proxy.
- En producción: `REQUIRE_HTTPS=true` y, si no se quiere publicar el contrato, `API_DOCS_ENABLED=false`.
- `JWT_SECRET` se rota cambiándolo en `.env` y reiniciando la API. Todas las sesiones se
  cierran y los usuarios vuelven a iniciar sesión.

## 5. Correo y notificaciones

- Sin `MAIL_HOST` solo funciona la bandeja de la app. Con SMTP configurado, los correos que
  fallan se reintentan hasta 5 veces con espera creciente y luego quedan en `FAILED`
  (columna `last_error` de `notifications`).
- Una caída del SMTP **no** marca la API como caída. Los correos pendientes salen cuando el
  servidor de correo vuelve.

## 6. Problemas frecuentes

| Síntoma | Causa probable | Acción |
|---|---|---|
| `502 MARKET_DATA_UNAVAILABLE` | Cuota de Alpha Vantage agotada o clave inválida | Esperar al día siguiente o revisar `ALPHAVANTAGE_API_KEY` |
| `503 CALC_ENGINE_UNAVAILABLE` | El motor de cálculo no responde | `docker compose restart calc-engine` |
| `422 INSUFFICIENT_MARKET_HISTORY` en proyecciones | Menos de 5 días de mercado guardados | Esperar a que se acumule historial (una consulta por día) |
| Alertas IA en `FAILED` con `BUDGET_EXCEEDED` | Se alcanzó el tope mensual de la IA | Subir `AI_MONTHLY_BUDGET_USD` o esperar al mes siguiente |
| Todo responde `402` | Licencia vencida o ausente | Renovar (§2) |
