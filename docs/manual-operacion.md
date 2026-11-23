# Manual de operación (IT_MANAGER)

Tareas habituales del responsable técnico de la empresa cliente. La instalación inicial está
en [instalacion-cliente.md](instalacion-cliente.md).

## 1. Salud del sistema

| Qué revisar | Cómo | Esperado |
|---|---|---|
| API | `GET /api/health` | `{"status":"UP"}` |
| Contenedores | `docker compose ps` | `postgres`, `calc-engine`, `api` y `pg-backup` en *running* (*healthy* donde aplique) |
| Logs | `docker compose logs --since 1h api` | JSON con `traceId`; filtrar por `"level":"ERROR"` |
| Espacio en disco | `df -h` y `du -sh backups/` | Menos del 80 % de uso |

Cuando un usuario reporta un error, la respuesta trae la cabecera `X-Request-Id`. Con ese valor
se busca la traza completa en los logs: `docker compose logs api | grep <id>`.

## 2. Licencia

- **Consultar**: `GET /api/license` (IT_MANAGER o ADMIN). Muestra el estado (`ACTIVE`,
  `EXPIRING`, `EXPIRED`, `MISSING`), el plan, los módulos, los usuarios activos y los días restantes.
- **Avisos**: 30 y 7 días antes del vencimiento llega una notificación al IT_MANAGER y al ADMIN.
- **Renovar**: enviar el contenido del archivo `license.json` recibido de RiTech:
  ```bash
  curl -X POST https://api.empresa.com/api/license \
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
  usuario afectado en la auditoría: `GET /api/audit?action=ACCOUNT_LOCKED`.
- Exportar la auditoría para un control o una auditoría externa:
  `GET /api/audit/export?from=2026-01-01&to=2026-12-31` (CSV). La exportación queda auditada.
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
