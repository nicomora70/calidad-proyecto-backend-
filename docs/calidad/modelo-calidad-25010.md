# Modelo de calidad del producto — ISO/IEC 25010:2023

Evaluación del backend con las **9 características** de la versión 2023, cada una con métricas
según ISO/IEC 25023, su meta y la evidencia que la sostiene. El proceso de evaluación sigue
ISO/IEC 25040: establecer requisitos → especificar → diseñar → ejecutar → concluir.

Leyenda del estado: ✅ cumple con evidencia · 🟡 parcial · ⬜ pendiente · N/A no aplica al backend.

## 1. Requisitos de evaluación (25040, etapa 1)

- **Propósito:** verificar que el backend es apto para venderse como instalación por cliente.
- **Producto evaluado:** `api-java` + `calc-engine` + PostgreSQL, en la rama principal.
- **Prioridad de características** (según el riesgo del dominio financiero):
  1. Adecuación funcional (cálculos de dinero) · 2. Seguridad · 3. Fiabilidad ·
  4. Mantenibilidad · 5. Flexibilidad · 6. Eficiencia de desempeño · 7. Compatibilidad ·
  8. Protección · 9. Capacidad de interacción.

## 2. Características, métricas y metas

### 2.1 Adecuación funcional
| Subcaracterística | Métrica (25023) | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Completitud funcional | Funciones implementadas / funciones especificadas de la fase | 100 % de la fase cerrada | `task.md`, [matriz de trazabilidad](matriz-trazabilidad-29148.md) | ✅ (Fase 1 cerrada) |
| Corrección funcional | Casos de prueba de cálculo que pasan / total | 100 % | `calc-engine/tests/test_returns.py`, `test_statistics.py` | ✅ |
| Corrección funcional | Precisión monetaria | 2 decimales, HALF_EVEN, sin `float` | `Decimal` en Python, `BigDecimal` y `NUMERIC(18,2)` | ✅ |
| Pertinencia funcional | Reglas de negocio con prueba / reglas de negocio | 100 % | `docs/logica-negocio.md` §2 vs. pruebas | 🟡 |

### 2.2 Eficiencia de desempeño
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Comportamiento temporal | p95 de `POST /api/simulations` (con caché de mercado) | ≤ 500 ms con 20 usuarios concurrentes | Prueba de carga k6 (Fase 6) | ⬜ |
| Utilización de recursos | Memoria de la API | ≤ 75 % del límite del contenedor | `-XX:MaxRAMPercentage=75` | 🟡 |
| Capacidad | Llamadas a Alpha Vantage por ventana de caché | 1 por TTL (6 h), aunque haya concurrencia | Caché + advisory lock (`MarketFetchLock`) | ✅ |

### 2.3 Compatibilidad
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Interoperabilidad | Endpoints descritos en OpenAPI / endpoints | 100 % | `/v3/api-docs`, Swagger UI | ✅ |
| Interoperabilidad | Errores con formato estándar | 100 % en RFC 7807 con `code` | `ApiExceptionHandler`, `ProblemResponses` | ✅ |
| Coexistencia | Servicios aislados en la red interna | El motor de cálculo sin puerto público | `docker-compose.yml` (`expose`, no `ports`) | ✅ |

### 2.4 Capacidad de interacción (antes "usabilidad")
N/A para la interfaz gráfica, que vive en el repositorio del frontend. Aplica a la **API como
producto para desarrolladores**:
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Autodescriptividad | Endpoints con resumen en Swagger | 100 % | Anotaciones `@Operation` | 🟡 |
| Protección contra errores del usuario | Validaciones de entrada con mensaje por campo | 100 % de los DTOs | Bean Validation + `VALIDATION_ERROR` | ✅ |
| Asistencia al usuario | Errores con código estable y documentado | 100 % | Catálogo en `logica-negocio.md` §6 | ✅ |

### 2.5 Fiabilidad
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Ausencia de fallos | Pruebas automatizadas que pasan en CI | 100 % en cada PR | GitHub Actions | ✅ |
| Disponibilidad | Healthchecks de los 3 servicios | Presentes y usados por `depends_on` | `docker-compose.yml`, Actuator | ✅ |
| Tolerancia a fallos | Dependencias externas con reintento y circuit breaker | 2/2 (calc-engine, Alpha Vantage) | `ResilientCall` + `ResilientCallTest` | ✅ |
| Tolerancia a fallos | Fallos de la IA que revierten la simulación | 0 (las alertas son *best-effort*) | Diseño Fase 2 | ⬜ |
| Recuperabilidad | Respaldo diario y restauración probada | RPO ≤ 24 h, RTO ≤ 2 h | `pg-backup` (Fase 5) | ⬜ |

### 2.6 Seguridad
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Confidencialidad | Endpoints de negocio que exigen autenticación | 100 % (salvo health, login, refresh, logout, docs) | `SecurityConfig`, `InstallationFlowIT` | ✅ |
| Confidencialidad | Secretos guardados en claro | 0 (BCrypt para contraseñas, SHA-256 para refresh tokens) | `AuthService`, `RefreshToken` | ✅ |
| Integridad | Simulaciones modificables después de creadas | 0 (entidad sin setters, sin endpoint de edición) | `Simulation` | ✅ |
| No repudio / Responsabilidad | Acciones sensibles auditadas / acciones sensibles | 100 % de la lista de `task.md` | `audit_log`, `GET /api/audit` | ✅ |
| Autenticidad | Tokens firmados y validados (firma, emisor, vencimiento) | 100 % | `JwtServiceTest` | ✅ |
| Resistencia | Bloqueo por fuerza bruta | 5 intentos → 15 min | `AuthServiceTest`, `InstallationFlowIT` | ✅ |
| Resistencia | Vulnerabilidades críticas o altas en dependencias e imágenes | 0 | Dependabot / Trivy (Fase 6) | ⬜ |

### 2.7 Mantenibilidad
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Modularidad | Módulos por funcionalidad con dependencias explícitas | Sí | `docs/arquitectura.md` | ✅ |
| Capacidad de ser probado | Cobertura de líneas en clases `*Service` | ≥ 80 % (hace fallar el build) | Regla JaCoCo `check-services` | ✅ |
| Capacidad de ser probado | Dependencias inyectables (incluido el reloj) | 100 % | `Clock` inyectado | ✅ |
| Capacidad de ser analizado | Logs estructurados con `traceId` por petición | Sí | `RequestIdFilter`, logs ECS | ✅ |
| Capacidad de ser modificado | Estilo de código verificado automáticamente | Spotless y Ruff en CI | `spotless:check` en `verify`, Ruff en CI | ✅ |
| Reusabilidad | Proveedores intercambiables detrás de interfaces | Mercado, IA y noticias | `MarketDataProvider` | 🟡 |

### 2.8 Flexibilidad (antes "portabilidad")
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Instalabilidad | Pasos para instalar en un cliente nuevo | ≤ 3 (`cp .env`, editar, `docker compose up`) | `README.md`, smoke test | ✅ |
| Adaptabilidad | Parámetros por instalación sin recompilar | 100 % por variables de entorno | `.env.example`, `RitechProperties` | ✅ |
| Reemplazabilidad | Esquema versionado y reproducible | 100 % con Flyway | `V1`, `V2` | ✅ |
| Escalabilidad | API sin estado (escalable horizontalmente) | Sí, con sesiones JWT | `SessionCreationPolicy.STATELESS` | ✅ |

### 2.9 Protección (*safety*, nueva en la versión 2023)
| Subcaracterística | Métrica | Meta | Evidencia | Estado |
|---|---|---|---|---|
| Restricción operativa | Modo solo lectura si la licencia vence | Sí | Fase 5 | ⬜ |
| Identificación de riesgos / advertencia | Las salidas de IA y las proyecciones llevan aviso de "no es asesoría financiera" | 100 % | Fase 2 y Fase 5 | ⬜ |
| A prueba de fallos | Pérdida máxima limitada al capital (el monto nunca es negativo) | Sí | `test_loss_is_capped_at_capital` | ✅ |

## 3. Conclusión de la evaluación (25040, etapa 5)

Se actualiza al cierre de cada fase con los datos del último CI de `main`.

| Fecha | Versión | ✅ | 🟡 | ⬜ | Observaciones |
|---|---|---|---|---|---|
| 2026-11-22 | 0.2.0 (Fase 1) | 28 | 4 | 6 | Funcionalidad crítica, seguridad y mantenibilidad cubiertas; faltan desempeño medido, recuperabilidad y el endurecimiento de la Fase 6 |
