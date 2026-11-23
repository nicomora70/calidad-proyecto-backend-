# Proceso de desarrollo — ISO/IEC 29110 (perfil básico para entidades muy pequeñas)

El perfil básico define dos procesos: **Gestión del proyecto (PM)** e **Implementación del
software (SI)**. La tabla muestra cómo se cumple cada actividad con las prácticas y artefactos del repositorio.

## PM — Gestión del proyecto

| Actividad | Práctica en el proyecto | Producto de trabajo |
|---|---|---|
| PM.1 Planificación | Fases con alcance, tareas y prioridades | `task.md` |
| PM.1 Planificación (riesgos) | Registro de riesgos con tratamiento | `docs/calidad/riesgos-31000.md` |
| PM.2 Ejecución del plan | Una rama y un PR por bloque de trabajo | Pull requests en GitHub |
| PM.3 Evaluación y control | Revisión del avance con las casillas de `task.md`; CI como control de calidad | `task.md`, GitHub Actions |
| PM.4 Cierre | Informe de pruebas y evaluación 25010 al terminar cada fase | `plan-pruebas-29119.md` §8, `modelo-calidad-25010.md` §3 |
| Control de versiones | Git con historial completo y mensajes descriptivos | Repositorio |
| Gestión de cambios de requisitos | IDs nuevos en la matriz y su tarea en `task.md` | `matriz-trazabilidad-29148.md` |

## SI — Implementación del software

| Actividad | Práctica en el proyecto | Producto de trabajo |
|---|---|---|
| SI.1 Inicio | Revisión del plan y del entorno (Docker, Java 21, Python 3.12) | `README.md` |
| SI.2 Análisis de requisitos | Casos de uso, reglas, roles y catálogo de errores | `docs/logica-negocio.md`, matriz de trazabilidad |
| SI.3 Arquitectura y diseño | Servicios, contratos y decisiones (ADR) | `docs/arquitectura.md`, `docs/modelo-datos.md` |
| SI.4 Construcción | Código en inglés, estilo verificado (Spotless y Ruff), pruebas unitarias junto al código | `api-java/`, `calc-engine/` |
| SI.5 Integración y pruebas | Testcontainers, smoke test y umbral de cobertura en CI | `plan-pruebas-29119.md`, `.github/workflows/ci.yml` |
| SI.6 Entrega del producto | Imágenes Docker, `.env.example` y guía de instalación | `docker-compose.yml`, `README.md` |

## Verificación y validación
- **Verificación** (¿se construyó bien?): CI en cada PR y revisión del PR por otra persona.
- **Validación** (¿se construyó lo correcto?): demostración de cada fase al docente o cliente
  frente a los casos de uso de `logica-negocio.md`, registrando sus observaciones como tareas.

## Roles (adaptados a un equipo pequeño)
| Rol 29110 | Responsabilidad |
|---|---|
| Líder del proyecto | Prioriza `task.md`, aprueba el cierre de cada fase |
| Analista / Diseñador | Mantiene `docs/` y la matriz de trazabilidad |
| Programador | Implementa y escribe las pruebas |
| Responsable de pruebas | Mantiene el plan y el informe de pruebas; revisa los PR |
| Cliente | Valida las demostraciones (docente o empresa piloto) |
