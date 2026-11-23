# Requisitos y matriz de trazabilidad — ISO/IEC/IEEE 29148

Cada requisito es **necesario, sin ambigüedad, verificable y trazable**, como pide 29148 §5.2.
La matriz enlaza requisito → caso de uso (`docs/logica-negocio.md`) → implementación → prueba.

Prioridad: **M** = obligatorio (*must*) · **S** = deseable (*should*). Estado: ✅ verificado por prueba automatizada · 🟡 implementado sin prueba específica · ⬜ pendiente.

## Requisitos funcionales

| ID | Requisito | Pri. | CU | Implementación | Prueba | Estado |
|---|---|---|---|---|---|---|
| RF-01 | El sistema debe calcular `rendimiento = variación × 2` | M | CU-01 | `calc-engine/app/calculations/returns.py` | `test_positive_variation_doubles_gain`, `test_negative_variation_doubles_loss` | ✅ |
| RF-02 | El monto final se redondea a 2 decimales con HALF_EVEN | M | CU-01 | `returns.py` | `test_rounding_half_even_to_cents` | ✅ |
| RF-03 | El monto final nunca es negativo | M | CU-01 | `returns.py` | `test_loss_is_capped_at_capital` | ✅ |
| RF-04 | Rechazar un capital < 100.000 COP con `400 INSUFFICIENT_CAPITAL` | M | CU-01 | `SimulationService.simulate` | `rejectsCapitalBelowMinimum`, smoke test | ✅ |
| RF-05 | La simulación pertenece al usuario autenticado | M | CU-01 | `SimulationController.create` | `investorCreatesSimulationOwnedByTokenSubject` | ✅ |
| RF-06 | Reutilizar la variación de mercado durante el TTL (6 h) | M | CU-01 | `MarketVariationService` | `reusesFreshCachedValue`, `fetchesWhenCacheIsStale` | ✅ |
| RF-07 | Solo una consulta al proveedor cuando vence la caché | S | CU-01 | `MarketFetchLock` | — (requiere prueba concurrente) | 🟡 |
| RF-08 | Historial propio, paginado y filtrable por fechas | M | CU-02 | `MeController.mySimulations` | `InstallationFlowIT.fullInstallationFlow` | ✅ |
| RF-09 | Ver una simulación ajena responde 404 | M | CU-02 | `SimulationService.get` | `ownerAndAdminCanReadSimulationOthersGetNotFound` | ✅ |
| RF-10 | Dashboard con KPIs (promedio, ponderado, volatilidad, drawdown, tasa de acierto) solo para ADMIN | M | CU-03 | `DashboardController`, `statistics.py` | `test_aggregates`, `test_max_drawdown`, `InstallationFlowIT` | ✅ |
| RF-11 | Login con tokens de acceso (15 min) y de renovación (7 días) | M | §4 | `AuthService.login` | `loginSucceedsWithNormalizedEmail`, `JwtServiceTest` | ✅ |
| RF-12 | Rotación del refresh token y detección de reutilización | M | §4 | `AuthService.refresh` | `refreshRotatesToken`, `reusingRevokedTokenRevokesAllSessions` | ✅ |
| RF-13 | Bloqueo de 15 min tras 5 intentos fallidos | M | §4 | `User.registerFailedLogin` | `fifthFailedAttemptLocksAccount`, `accountLocksAfterFiveFailedAttempts` | ✅ |
| RF-14 | Cambio de contraseña obligatorio con contraseña temporal | M | §4 | `PasswordChangeRequiredInterceptor` | `temporaryPasswordBlocksBusinessEndpoints` | ✅ |
| RF-15 | El ADMIN crea, edita, cambia de rol y elimina (lógicamente) usuarios | M | §4 | `UserService` | `UserServiceTest`, `InstallationFlowIT` | ✅ |
| RF-16 | Siempre debe quedar al menos un ADMIN activo | M | §4 | `UserService.ensureNotLastActiveAdmin` | `cannotDeleteLastAdmin`, `lastAdminCannotBeDemoted` | ✅ |
| RF-17 | Correo único sin distinguir mayúsculas | M | §4 | `User.normalizeEmail`, índice `ux_users_email_active` | `duplicateEmailIsRejected`, `InstallationFlowIT` | ✅ |
| RF-18 | El primer ADMIN se crea desde variables de entorno | M | §4 | `BootstrapAdminInitializer` | `InstallationFlowIT` (indirecta), smoke test | ✅ |
| RF-19 | Registro de auditoría consultable por ADMIN e IT_MANAGER | M | CU-09 | `AuditService`, `AuditController` | `AuditServiceTest`, `InstallationFlowIT` | ✅ |
| RF-20 | Alertas con IA por simulación, asíncronas y con límite de gasto | S | CU-05 | `AiAlertService`, `AiAlertController`, `TemplateAiAlertGenerator`, `GeminiAiAlertGenerator` | `AiAlertServiceTest`, `AlertGeneratorsTest`, `AiAlertControllerTest`, `InstallationFlowIT` | ✅ (con noticias desde la Fase 3) |
| RF-21 | Ingesta programada de noticias, deduplicada, con tope diario y sentimiento del día | S | CU-06 | `NewsIngestionService`, `NewsService`, `NewsController`, calc-engine `sentiment.py` | `NewsIngestionServiceTest`, `NewsServiceTest`, `AlphaVantageNewsParseTest`, `NewsControllerTest`, `test_sentiment.py`, `InstallationFlowIT` | ✅ |
| RF-22 | Notificaciones en la app y por correo (outbox con reintentos) según preferencias | S | CU-07 | `NotificationService`, `NotificationDispatcher`, `NotificationTriggers`, `EmailSender`, `NotificationController` | `NotificationServiceTest`, `NotificationDispatcherTest`, `NotificationTriggersTest`, `EmailSenderTest`, `NotificationControllerTest`, `InstallationFlowIT` | ✅ (aviso de licencia, en la Fase 5) |
| RF-23 | Licencia firmada con modo solo lectura al vencer, módulos por plan y tope de usuarios | M | CU-08 | `LicenseVerifier`, `LicenseService`, `LicenseInterceptor`, `LicenseController`, `tools/license` | `LicenseVerifierTest`, `LicenseServiceTest`, `LicenseInterceptorTest`, `LicenseFlowIT` | ✅ |
| RF-24 | Escenarios y proyección (pesimista, base, optimista) | S | CU-04 | `ProjectionService`, calc-engine `projection.py` | `ProjectionServiceTest`, `test_projection.py`, `InstallationFlowIT` | ✅ |
| RF-25 | Exportes CSV de historial y auditoría | S | CU-10 | `ExportService`, `Csv` | `ExportServiceTest`, `InstallationFlowIT`, `LicenseFlowIT` | ✅ |
| RF-26 | Branding por instalación | C | CU-10 | `BrandingService`, `BrandingController` | `BrandingServiceTest`, `BrandingControllerTest`, `InstallationFlowIT` | ✅ |

## Requisitos no funcionales (vinculados a ISO/IEC 25010)

| ID | Requisito | Característica 25010 | Verificación | Estado |
|---|---|---|---|---|
| RNF-01 | Todo error responde RFC 7807 con `code` y `traceId` | Compatibilidad / Capacidad de interacción | `SimulationControllerTest`, `InstallationFlowIT` | ✅ |
| RNF-02 | Contraseñas con BCrypt; refresh tokens guardados solo como hash | Seguridad — confidencialidad | `refreshTokenIsStoredHashed` | ✅ |
| RNF-03 | La API no arranca sin un `JWT_SECRET` de ≥ 32 bytes | Seguridad — autenticidad | `shortSecretPreventsStartup` | ✅ |
| RNF-04 | Reintento y circuit breaker hacia dependencias externas | Fiabilidad — tolerancia a fallos | `ResilientCallTest` | ✅ |
| RNF-05 | Cobertura ≥ 80 % en clases `*Service` | Mantenibilidad — capacidad de ser probado | Regla JaCoCo en `verify` | ✅ |
| RNF-06 | Logs JSON con `traceId` propagado al motor de cálculo | Mantenibilidad — capacidad de ser analizado | `RequestIdFilter`, logs JSON en calc-engine | ✅ |
| RNF-07 | Instalación con `docker compose up` en ≤ 3 pasos | Flexibilidad — instalabilidad | Smoke test de CI | ✅ |
| RNF-08 | p95 ≤ 500 ms en `POST /api/simulations` | Eficiencia de desempeño | k6 (Fase 6) | ⬜ |
| RNF-09 | 0 vulnerabilidades críticas o altas | Seguridad — resistencia | Trivy / Dependency-Check (Fase 6) | ⬜ |
| RNF-10 | Respaldo diario con restauración probada | Fiabilidad — recuperabilidad | `pg-backup` + paso "Backup and restore" del CI | ✅ |

## Gestión de cambios en los requisitos
Un requisito nuevo o modificado se registra primero aquí (con un ID nuevo; los IDs nunca se
reutilizan) y en `task.md`. El PR que lo implementa debe mencionar el ID y agregar la prueba
correspondiente en la columna "Prueba".
