# Aseguramiento de calidad — marco normativo ISO

Esta carpeta documenta cómo el backend de RiTech se **alinea** con las normas ISO de calidad
de software. Es un trabajo de alineación y evidencia, **no una certificación**: la certificación
la otorga un organismo acreditado tras una auditoría externa. Los textos de las normas son de
pago, así que aquí se citan las cláusulas y características por nombre, sin reproducir su contenido.

## Normas aplicadas y para qué

| Norma | Ámbito | Documento |
|---|---|---|
| **ISO/IEC 25010:2023** | Modelo de calidad del producto (las 9 características) | [modelo-calidad-25010.md](modelo-calidad-25010.md) |
| **ISO/IEC 25023:2016** · **ISO/IEC 25040:2024** | Métricas de calidad del producto y proceso de evaluación | [modelo-calidad-25010.md](modelo-calidad-25010.md) (métricas y metas) |
| **ISO/IEC/IEEE 29119-2/-3/-4** | Proceso, documentación y técnicas de prueba | [plan-pruebas-29119.md](plan-pruebas-29119.md) |
| **ISO/IEC/IEEE 29148:2018** | Ingeniería de requisitos y trazabilidad | [matriz-trazabilidad-29148.md](matriz-trazabilidad-29148.md) |
| **ISO/IEC 27001:2022** (Anexo A) · **ISO/IEC 27002:2022** | Seguridad de la información | [controles-27001.md](controles-27001.md) |
| OWASP ASVS 4.0.3 (nivel 2) | Verificación de seguridad de la aplicación | [revision-asvs-l2.md](revision-asvs-l2.md) |
| **ISO/IEC 29110** (perfil básico) | Ciclo de vida para equipos pequeños: gestión del proyecto e implementación | [proceso-29110.md](proceso-29110.md) |
| **ISO 31000:2018** | Gestión de riesgos | [riesgos-31000.md](riesgos-31000.md) |
| ISO 9001:2015 · ISO/IEC/IEEE 90003:2018 | Sistema de gestión de calidad aplicado al software | Marco de referencia (ciclo PDCA, sección siguiente) |

> ¿Por qué ISO/IEC 29110 y no ISO/IEC/IEEE 12207? La 12207 describe el ciclo de vida completo
> para organizaciones de cualquier tamaño; la 29110 es su perfil para entidades muy pequeñas
> (hasta 25 personas), que es el caso de este equipo.

## Ciclo de mejora continua (PDCA, ISO 9001 §10)

| Etapa | Cómo se materializa en el proyecto |
|---|---|
| **Planear** | `task.md` (fases y tareas), metas de calidad en `modelo-calidad-25010.md`, riesgos en `riesgos-31000.md` |
| **Hacer** | Desarrollo por ramas y pull requests, con la definición de "hecho" de `task.md` |
| **Verificar** | CI en cada PR (pruebas, cobertura ≥ 80 % en servicios, smoke test con Docker) e informe de pruebas |
| **Actuar** | Los defectos y las metas incumplidas se vuelven tareas nuevas en `task.md` y se revisan al cerrar cada fase |

## Evidencias objetivas disponibles

Todas son verificables en el repositorio o en GitHub Actions:

- Historial de cambios: commits y pull requests en GitHub.
- Ejecuciones de CI con resultados de pruebas y el reporte JaCoCo (artefacto `jacoco-report`).
- Migraciones versionadas: `api-java/src/main/resources/db/migration`.
- Auditoría en tiempo de ejecución: tabla `audit_log` y `GET /api/v1/audit`.
- Documentación de requisitos, arquitectura y datos: `docs/`.
