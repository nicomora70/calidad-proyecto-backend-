# Plan de pruebas — ISO/IEC/IEEE 29119

Estructura según ISO/IEC/IEEE 29119-3 (documentación de pruebas). Las técnicas de diseño siguen
ISO/IEC/IEEE 29119-4 y el proceso (planificar → monitorear y controlar → completar) sigue 29119-2.

## 1. Identificación
- **Producto:** backend RiTech (`api-java`, `calc-engine`) · **Versión:** 0.2.0
- **Responsable de pruebas:** equipo de desarrollo · **Aprobación:** docente o líder del proyecto

## 2. Alcance
| Dentro del alcance | Fuera del alcance |
|---|---|
| Cálculos financieros, reglas de negocio, API REST, seguridad (autenticación y autorización), persistencia y migraciones, despliegue con Docker | Interfaz gráfica (otro repositorio), disponibilidad de Alpha Vantage, pruebas con dinero real |

## 3. Estrategia: niveles de prueba

| Nivel | Herramienta | Qué verifica | Dónde corre | Criterio de salida |
|---|---|---|---|---|
| **Unitarias** | JUnit 5 + Mockito · pytest | Servicios, reglas y cálculos aislados | Local y CI | 100 % pasan; cobertura ≥ 80 % en `*Service` |
| **Componente (web)** | `@WebMvcTest` + spring-security-test | Validación, códigos HTTP, permisos por rol | Local y CI | 100 % pasan |
| **Integración** | Testcontainers (PostgreSQL 16) | Flyway, validación del esquema, flujos completos con BD real | CI (se omiten sin Docker) | 100 % pasan |
| **Sistema (smoke)** | `docker compose` + curl + jq | Los 3 contenedores juntos y el flujo autenticado de extremo a extremo | CI | 100 % de los pasos |
| **Rendimiento** | k6 | Metas de la característica 2.2 de `modelo-calidad-25010.md` | Fase 6 | p95 ≤ 500 ms |
| **Seguridad** | Revisión OWASP ASVS L2, Trivy, Dependency-Check | Vulnerabilidades | Fase 6 | 0 críticas o altas |

## 4. Técnicas de diseño (29119-4) aplicadas

### 4.1 Partición de equivalencia y valores límite
| Regla | Particiones | Valores límite probados | Prueba |
|---|---|---|---|
| Capital mínimo 100.000 COP | inválido < 100.000 · válido ≥ 100.000 | 99.999,99 ❌ · 50.000 ❌ · 1.000.000 ✅ | `SimulationServiceTest`, `SimulationControllerTest` |
| Capital > 0 (motor) | ≤ 0 · > 0 | 0 ❌ | `test_non_positive_capital_is_rejected`, `test_returns_rejects_invalid_capital` |
| Pérdida limitada al capital | rendimiento ≥ −100 % · < −100 % | −60 % de variación (−120 %) → monto 0 | `test_loss_is_capped_at_capital` |
| Variación (signo) | positiva · cero · negativa | +2 · 0 · −2 | `test_returns.py` |
| Bloqueo por intentos | 1–4 fallos · 5.º fallo | 5.º intento bloquea | `fifthFailedAttemptLocksAccount`, `accountLocksAfterFiveFailedAttempts` |
| Rango de fechas | `from` ≤ `to` · `from` > `to` | mismo día (inclusivo) · día siguiente | `DateRangeTest` |

**Brechas detectadas** (a cubrir, Fase Q):
- Capital exactamente igual a 100.000,00 (límite válido).
- Contraseña de 7, 8, 72 y 73 caracteres.
- Variación exactamente en ±100 en el motor.

### 4.2 Tabla de decisión — autorización por rol
| Endpoint | Anónimo | INVESTOR | IT_MANAGER | ADMIN | Prueba |
|---|---|---|---|---|---|
| `POST /api/simulations` | 401 | 201 | 403 | 201 | `SimulationControllerTest` |
| `GET /api/simulations` | 401 | 403 | 403 | 200 | `SimulationControllerTest`, `InstallationFlowIT` |
| `GET /api/simulations/{id}` | 401 | 200 si es suya, 404 si no | 404 | 200 | `SimulationServiceTest` |
| `GET /api/dashboard/statistics` | 401 | 403 | 403 | 200 | `InstallationFlowIT`, smoke test |
| `/api/users/**` | 401 | 403 | 403 | 200 | `InstallationFlowIT` (parcial) |
| `GET /api/audit` | 401 | 403 | 200 | 200 | `InstallationFlowIT` (solo ADMIN) |

### 4.3 Transición de estados — sesión y cuenta
```
[Creada: contraseña temporal] --cambio de contraseña--> [Activa]
[Activa] --5 fallos--> [Bloqueada 15 min] --vence el tiempo--> [Activa]
[Activa] --desactivar--> [Inactiva] --activar--> [Activa]
[Activa | Inactiva] --eliminar--> [Eliminada (lógico)]
Refresh token: [Vigente] --uso--> [Revocado] --reutilización--> se revocan TODOS los del usuario
```
Pruebas: `AuthServiceTest`, `UserServiceTest`, `InstallationFlowIT.refreshTokenRotationAndReuseDetection`.

## 5. Entorno de pruebas
- CI: GitHub Actions `ubuntu-latest`, Java 21 (Temurin), Python 3.12, Docker.
- Datos: generados por cada prueba; BD efímera (Testcontainers o `docker compose down -v`).
- Mercado: proveedor `fixed` (variación determinista); el motor de cálculo es real en el smoke test.

## 6. Criterios
- **Entrada:** el código compila y el PR tiene una descripción.
- **Suspensión:** el CI falla en una etapa previa (por ejemplo, el build).
- **Salida (completitud):** todos los niveles en verde, umbral de cobertura cumplido y ningún defecto abierto de severidad alta.

## 7. Gestión de defectos
| Severidad | Definición | Ejemplo | Tratamiento |
|---|---|---|---|
| Crítica | Cálculo de dinero incorrecto o brecha de seguridad | Monto final erróneo, endpoint sin autenticación | Bloquea el merge; se corrige de inmediato |
| Alta | Una función principal no se puede usar | No se puede iniciar sesión | Bloquea la entrega de la fase |
| Media | Funciona, pero con un error recuperable | Falta el `code` en un error | Tarea en `task.md` |
| Baja | Cosmética o de documentación | Texto en Swagger | Siguiente iteración |

Cada defecto se registra como *issue* de GitHub con su severidad, los pasos para reproducirlo y la prueba que lo cubrirá.

## 8. Informe de completitud de pruebas (29119-3)

| Fecha | Rama / PR | Unitarias Java | Integración | pytest | Smoke | Cobertura de servicios | Resultado |
|---|---|---|---|---|---|---|---|
| 2026-11-22 | `fase-1-autenticacion` (PR #1) | 27/27 | 5/5 | 14/14 | ✅ | reporte JaCoCo | Aprobado |
| 2026-11-22 | `fase-1-pendientes` (local) | 50/50 | omitidas sin Docker | 14/14 | — | ≥ 80 % ✅ | Pendiente de CI |
