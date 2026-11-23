# Lógica de negocio — RiTech

## 1. Propuesta de valor

RiTech es un software de **apoyo a decisiones de inversión** para empresas cuyas
inversiones siguen el índice **NASDAQ 100 con apalancamiento x2**. Responde a una
pregunta concreta: *"si invierto X hoy, ¿cuánto gano o pierdo según el comportamiento
del mercado?"*, y la acompaña de historial, indicadores de riesgo, alertas con IA,
noticias y notificaciones.

**Modelo comercial:** **instalación por cliente.** Cada empresa recibe su propia
instancia Docker (on-premise o en su nube), con su propia base de datos, sus usuarios
y una **licencia** que fija la vigencia y el número máximo de usuarios. Ningún dato
se comparte entre clientes.

| Paquete | Incluye |
|---|---|
| **Básico** | Simulaciones, historial, dashboard, hasta 10 usuarios |
| **Profesional** | + Alertas con IA, noticias, notificaciones por correo, hasta 50 usuarios |
| **Empresarial** | + Escenarios/proyecciones, auditoría exportable, usuarios ilimitados, soporte |

El paquete se controla desde la licencia (módulos habilitados), no desde el código.

## 2. Reglas fijas del negocio (RiTech)

| Regla | Valor | Dónde vive |
|---|---|---|
| Activo de referencia | NASDAQ 100, usando el ETF **QQQ** como proxy | `ritech.market.symbol` |
| Apalancamiento | **x2** simétrico (gana el doble y pierde el doble) | `calc-engine` (`LEVERAGE`) |
| Moneda | **COP** | `ritech.simulation.currency` |
| Capital mínimo | **$100.000 COP** | `ritech.simulation.min-capital` |
| Precisión | Dinero a 2 decimales, porcentajes a 4, redondeo **HALF_EVEN** (bancario) | `calc-engine` |
| Pérdida máxima | El monto final nunca es negativo: como mucho se pierde el capital | `calc-engine` |

## 3. Fórmulas (motor de cálculo en Python)

```
rendimiento_pct = variacion_nasdaq_pct × 2
monto_final     = max(0, capital × (1 + rendimiento_pct / 100))
ganancia        = monto_final − capital
```

Ejemplos:

| Capital | Variación NASDAQ | Rendimiento | Monto final | Ganancia |
|---|---|---|---|---|
| 1.000.000 | +2 % | +4 % | 1.040.000 | +40.000 |
| 1.000.000 | −2 % | −4 % | 960.000 | −40.000 |
| 100.000 | −60 % | −120 % | **0** (tope) | −100.000 |

Estadísticas del dashboard (también en Python):

- **Rendimiento promedio**: media simple de `rendimiento_pct`.
- **Rendimiento ponderado**: ganancia total / capital total × 100 (pesa más lo que más se invirtió).
- **Volatilidad**: desviación estándar muestral de los rendimientos.
- **Máximo drawdown**: la mayor caída desde un pico de la curva de capital compuesta.
- **Tasa de acierto**: % de simulaciones con rendimiento positivo.
- Mejor y peor rendimiento, número de simulaciones, capital total y ganancia total.

## 4. Roles y permisos

| Acción | INVESTOR | IT_MANAGER | ADMIN |
|---|:-:|:-:|:-:|
| Iniciar sesión / cambiar su contraseña | ✅ | ✅ | ✅ |
| Simular y ver **su propio** historial y alertas | ✅ | — | ✅ |
| Ver noticias y configurar sus notificaciones | ✅ | ✅ | ✅ |
| Dashboard global de estadísticas | — | — | ✅ |
| Crear, desactivar, eliminar usuarios y cambiar roles | — | — | ✅ |
| Ver auditoría, salud del sistema y estado de la licencia | — | ✅ | ✅ |
| Cargar o renovar la licencia | — | ✅ | — |

Reglas:

- **No hay autoregistro**: el ADMIN crea las cuentas con una **contraseña temporal** y se la
  entrega al usuario, que debe cambiarla en su primer inicio de sesión. El primer ADMIN se crea al
  arrancar a partir de variables de entorno (`BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`)
  y sigue la misma regla.
- **Sesiones**: el access token (JWT) dura 15 minutos y el refresh token 7 días. Cada refresh
  emite un par nuevo y revoca el anterior; si alguien reutiliza un refresh token ya revocado
  (señal de robo), se cierran todas las sesiones de ese usuario. Cambiar la contraseña, cambiar
  el rol, desactivar o eliminar al usuario revoca sus refresh tokens.
- Un ADMIN no puede quitarse a sí mismo el rol ni eliminarse si es el último ADMIN activo.
- **Borrado lógico**: eliminar un usuario llena `deleted_at` y lo desactiva; sus
  simulaciones se conservan porque son historial financiero.
- El correo es único entre usuarios no eliminados (sin distinguir mayúsculas).
- Contraseñas: mínimo 8 caracteres, hash **BCrypt**; 5 intentos fallidos bloquean la cuenta 15 minutos.

## 5. Casos de uso

### CU-01 Simular inversión
1. El inversionista envía `initialCapital`.
2. **Java** valida que capital ≥ mínimo; si no → `400 INSUFFICIENT_CAPITAL`.
3. **Java** obtiene la variación vigente del NASDAQ 100: si hay una en caché con menos
   de `MARKET_CACHE_TTL` (6 h por defecto) la reutiliza; si no, consulta Alpha Vantage y
   la guarda. Si el proveedor falla → `502 MARKET_DATA_UNAVAILABLE`.
4. **Python** calcula rendimiento, monto final y ganancia. Si no responde → `503 CALC_ENGINE_UNAVAILABLE`.
5. **Java** guarda la simulación (inmutable) y registra la auditoría `SIMULATION_CREATED`.
6. Se genera la alerta con IA en segundo plano (CU-05). Es *best-effort*: si falla,
   la simulación sigue siendo válida.
7. *(Fase 4)* Si el rendimiento supera el umbral del usuario, se le notifica.

> Por qué una caché: la capa gratuita de Alpha Vantage permite 25 llamadas al día, y
> todas las simulaciones de una misma ventana deben usar la misma cifra de mercado para
> poder compararse.

### CU-02 Consultar historial
Paginado (`page`, `size`, orden por fecha descendente), con filtros por rango de fechas.
El inversionista solo ve lo suyo; el ADMIN puede ver el de cualquiera.

### CU-03 Dashboard (ADMIN)
Java lee las simulaciones (filtradas por rango de fechas o usuario) y Python calcula las
estadísticas de la sección 3.

### CU-04 Escenarios y proyección (paquete Empresarial)
Dado un capital y un horizonte de N días hábiles, Python calcula tres escenarios
(pesimista = percentil 10, base = mediana, optimista = percentil 90) a partir de las
variaciones históricas reales guardadas, más una simulación Monte Carlo. Siempre se
presenta como estimación, no como promesa de rentabilidad.

### CU-05 Alerta con IA (Fase 2)
1. Al guardar la simulación se crea su alerta en estado `PENDING`, en la misma transacción.
2. Después del commit, un hilo en segundo plano la genera con el proveedor de `AI_PROVIDER`:
   - `template` (por defecto): texto armado con reglas a partir de la simulación, sin llamadas externas.
   - `gemini`: el LLM recibe el prompt versionado (`alert-v1`) y debe responder en JSON
     `{title, content}`. Si el JSON no es válido, la alerta queda `FAILED`.
3. Se guardan el modelo, la versión del prompt, los tokens y el costo (`AI_INPUT_PRICE_PER_MTOK`,
   `AI_OUTPUT_PRICE_PER_MTOK`). Si el gasto del mes alcanza `AI_MONTHLY_BUDGET_USD`, los
   proveedores de pago no se llaman y la alerta queda `FAILED` con motivo `BUDGET_EXCEEDED`.
4. Un fallo del proveedor (red, 5xx tras 3 intentos, 4xx, respuesta inválida) solo marca la
   alerta como `FAILED`; la simulación nunca se revierte.
5. `GET /api/simulations/{id}/alert` responde 202 mientras está `PENDING` y 200 con `DONE` o
   `FAILED`. Aplica la misma regla de dueño que la simulación.

Toda alerta lleva el aviso "Contenido generado por IA; no constituye asesoría financiera".
Desde la Fase 3 el prompt (`alert-v2`) incluye los titulares recientes guardados (CU-06).

### CU-06 Noticias (Fase 3)
1. Al arrancar (`NEWS_REFRESH_ON_STARTUP`) y según `NEWS_REFRESH_CRON` (cada 6 h por defecto,
   en la zona horaria de la instalación), la API descarga el feed de `NEWS_PROVIDER`:
   - `fixed`: tres artículos de ejemplo, para demo y CI.
   - `alphavantage`: `NEWS_SENTIMENT` de QQQ (comparte la clave y la cuota con el mercado).
2. Las URL ya guardadas se descartan, de modo que cada artículo se guarda una sola vez. Los
   artículos sin URL, sin título o con fecha inválida se omiten sin perder el resto del lote.
3. Con un proveedor de cuota limitada se permiten como máximo `NEWS_MAX_DAILY_CALLS` descargas
   por día (4 por defecto, de las 25 gratuitas). Al superarlo → `429 NEWS_QUOTA_EXCEEDED`.
   Una falla programada solo queda en el log; un refresco manual fallido → `502 NEWS_UNAVAILABLE`.
4. `GET /api/news`: feed paginado (más reciente primero), con filtros `from`/`to` (fecha de
   publicación, inclusivas) y `sentiment` (etiqueta, sin distinguir mayúsculas).
5. `GET /api/news/sentiment?date=`: **Python** promedia los puntajes del día y devuelve la
   etiqueta y la distribución (alcista, neutral, bajista). Escala de Alpha Vantage:
   ≤ −0,35 Bearish · ≤ −0,15 Somewhat-Bearish · < 0,15 Neutral · < 0,35 Somewhat-Bullish · resto Bullish.
6. La alerta con IA (CU-05) recibe los últimos 5 titulares de las 48 h previas con su etiqueta.

### CU-07 Notificaciones (Fase 4)
Canales: en la app (bandeja) y correo. Disparadores: alerta IA nueva, variación del día
mayor al umbral configurado por el usuario, licencia por vencer (a IT_MANAGER/ADMIN).
Cada usuario elige canales y umbrales.

### CU-08 Licencia
Al arrancar, la API valida la firma (Ed25519) del archivo de licencia: empresa, fecha de
vencimiento, máximo de usuarios y módulos habilitados.
- Licencia vigente → todo funciona según los módulos.
- 30 días antes del vencimiento → aviso a IT_MANAGER/ADMIN.
- Vencida → **modo solo lectura**: se puede consultar, pero no simular ni crear usuarios
  (`402 LICENSE_EXPIRED`).
- Al crear un usuario por encima del máximo → `409 LICENSE_USER_LIMIT`.

### CU-09 Auditoría
Se registra: inicio de sesión (con éxito y fallido), creación, eliminación y cambio de rol
de usuarios, simulaciones, carga de licencia y cambios de configuración. El registro solo
admite inserciones y puede exportarse a CSV (paquete Empresarial).

## 6. Catálogo de errores (RFC 7807)

Todas las respuestas de error son `application/problem+json` con un campo `code` estable,
que el frontend usa para traducir el mensaje.

| HTTP | code | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo inválido (incluye `errors` por campo) |
| 400 | `BAD_REQUEST` | Petición mal formada (JSON inválido, UUID inválido en la URL…) |
| 400 | `INSUFFICIENT_CAPITAL` | Capital por debajo del mínimo |
| 400 | `INVALID_DATE_RANGE` | `from` es posterior a `to` |
| 400 | `INVALID_CURRENT_PASSWORD` | La contraseña actual no coincide al cambiarla |
| 400 | `PASSWORD_REUSED` | La nueva contraseña es igual a la actual |
| 401 | `UNAUTHORIZED` | Falta el token o es inválido |
| 401 | `INVALID_CREDENTIALS` | Correo o contraseña incorrectos (mensaje genérico) |
| 401 | `TOKEN_EXPIRED` | Token de acceso vencido: hay que llamar a `/api/auth/refresh` |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh token desconocido, vencido o revocado: hay que volver a iniciar sesión |
| 403 | `FORBIDDEN` | Rol sin permiso |
| 403 | `PASSWORD_CHANGE_REQUIRED` | Contraseña temporal: solo se permite `/api/auth/change-password`, `/api/auth/logout`, `/api/auth/refresh` y `/api/me` |
| 404 | `USER_NOT_FOUND` / `SIMULATION_NOT_FOUND` | Recurso inexistente |
| 404 | `AI_ALERT_NOT_FOUND` | La simulación no tiene alerta (creada antes de la Fase 2) |
| 409 | `EMAIL_ALREADY_EXISTS` | Correo duplicado |
| 409 | `LAST_ADMIN` | Se intenta quitar o eliminar al último ADMIN |
| 409 | `LICENSE_USER_LIMIT` | Se superó el máximo de usuarios de la licencia |
| 402 | `LICENSE_EXPIRED` | Licencia vencida (modo solo lectura) |
| 423 | `ACCOUNT_LOCKED` | Demasiados intentos fallidos |
| 429 | `NEWS_QUOTA_EXCEEDED` | Se alcanzó el tope diario de descargas de noticias |
| 502 | `MARKET_DATA_UNAVAILABLE` | Falló el proveedor de mercado |
| 502 | `NEWS_UNAVAILABLE` | Falló el proveedor de noticias en un refresco manual |
| 503 | `CALC_ENGINE_UNAVAILABLE` | El motor de cálculo no responde |
