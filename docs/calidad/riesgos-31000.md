# Registro de riesgos — ISO 31000:2018

Proceso: identificar → analizar → evaluar → tratar → seguir y revisar. Se revisa al cerrar cada fase.

**Escala:** probabilidad (P) e impacto (I) de 1 a 5. Nivel = P × I: 1–6 bajo · 8–12 medio · 15–25 alto.

| ID | Riesgo | Causa | P | I | Nivel | Tratamiento | Control implementado | Riesgo residual |
|---|---|---|---|---|---|---|---|---|
| R-01 | Cálculo financiero incorrecto | Uso de `float`, mal redondeo o regla mal implementada | 2 | 5 | 10 | Mitigar | `Decimal`/`BigDecimal`, HALF_EVEN, pruebas de valores límite | Bajo (3) |
| R-02 | Acceso no autorizado a datos de inversión | Endpoints sin protección o robo de token | 3 | 5 | 15 | Mitigar | JWT de 15 min, roles, rotación y detección de reutilización, auditoría | Medio (5–8) |
| R-03 | Ataque de fuerza bruta a las cuentas | Login sin límite | 4 | 4 | 16 | Mitigar | Bloqueo tras 5 intentos; rate limiting de login por IP (20/min, `429 RATE_LIMITED`) | Bajo (4) |
| R-04 | Caída o límite de Alpha Vantage (25 llamadas/día) | Dependencia externa gratuita | 4 | 3 | 12 | Mitigar | Caché de 6 h, advisory lock, reintento y circuit breaker; plan de proveedor de pago | Bajo (6) |
| R-05 | Motor de cálculo no disponible | Fallo del contenedor Python | 2 | 4 | 8 | Mitigar | Healthcheck, `restart: unless-stopped`, reintento y error `503` claro | Bajo (4) |
| R-06 | Pérdida de la base de datos del cliente | Sin respaldos | 2 | 5 | 10 | Mitigar | Volumen persistente; `pg_dump` diario con retención y restauración probada en CI | Bajo (4), si el cliente copia los respaldos fuera del servidor |
| R-07 | Filtración de secretos (`JWT_SECRET`, claves de API) | Subir `.env` o claves por defecto | 2 | 5 | 10 | Mitigar | `.env` ignorado por git, la API no arranca sin secreto fuerte | Bajo (4) |
| R-08 | Vulnerabilidad en dependencias | Librerías desactualizadas | 3 | 4 | 12 | Mitigar | Spring Boot en el último parche; Dependabot, Trivy en cada PR y Dependency-Check semanal | Bajo (6) |
| R-09 | El usuario toma la IA o las proyecciones como asesoría financiera | Texto de IA persuasivo | 3 | 4 | 12 | Mitigar / Transferir | Aviso legal fijo en cada alerta y en los términos de uso | Medio |
| R-10 | Incumplimiento de la Ley 1581 (datos personales) | Sin aviso de privacidad ni supresión | 2 | 4 | 8 | Mitigar | Datos mínimos y borrado lógico; procedimiento de supresión pendiente | Medio |
| R-11 | Deuda técnica que frena las fases siguientes | Prisa, sin estándares | 3 | 3 | 9 | Mitigar | CI obligatorio, cobertura ≥ 80 %, linters, definición de "hecho" | Bajo (4) |
| R-12 | Ventana de 15 min con permisos viejos tras cambiar un rol | JWT sin estado | 2 | 3 | 6 | Aceptar | Refresh tokens revocados al instante; limitación documentada en `task.md` | Bajo (6) |

## Seguimiento
| Fecha | Cambios |
|---|---|
| 2026-11-22 | Registro inicial al cierre de la Fase 1 |
