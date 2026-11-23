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

- **No hay autoregistro**: el ADMIN crea las cuentas. El primer ADMIN se crea al
  arrancar a partir de variables de entorno (`BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`)
  y debe cambiar su contraseña en el primer inicio de sesión.
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
6. *(Fase 2)* Se genera la alerta con IA en segundo plano. Es *best-effort*: si falla,
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
Con las últimas noticias del mercado y el resultado de la simulación, el LLM redacta una
alerta (título y contenido, máximo dos frases) que explica qué pasa en el mercado y qué
implica para la inversión. Se guarda la alerta junto con el modelo usado, los tokens y el costo.
Toda alerta lleva el aviso "Contenido generado por IA; no constituye asesoría financiera".

### CU-06 Noticias (Fase 3)
Un proceso programado descarga noticias de QQQ/NASDAQ (Alpha Vantage `NEWS_SENTIMENT`)
cada N horas, las deduplica por URL y las guarda con su puntaje de sentimiento. Se exponen
como feed paginado.

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
| 400 | `INSUFFICIENT_CAPITAL` | Capital por debajo del mínimo |
| 401 | `INVALID_CREDENTIALS` | Correo o contraseña incorrectos (mensaje genérico) |
| 401 | `TOKEN_EXPIRED` | Token de acceso vencido |
| 403 | `FORBIDDEN` | Rol sin permiso |
| 404 | `USER_NOT_FOUND` / `SIMULATION_NOT_FOUND` | Recurso inexistente |
| 409 | `EMAIL_ALREADY_EXISTS` | Correo duplicado |
| 409 | `LAST_ADMIN` | Se intenta quitar o eliminar al último ADMIN |
| 409 | `LICENSE_USER_LIMIT` | Se superó el máximo de usuarios de la licencia |
| 402 | `LICENSE_EXPIRED` | Licencia vencida (modo solo lectura) |
| 423 | `ACCOUNT_LOCKED` | Demasiados intentos fallidos |
| 502 | `MARKET_DATA_UNAVAILABLE` | Falló el proveedor de mercado |
| 503 | `CALC_ENGINE_UNAVAILABLE` | El motor de cálculo no responde |
