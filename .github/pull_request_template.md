## Qué cambia

<!-- Resumen breve y fase o issue relacionado (Closes #n) -->

## Trazabilidad (ISO/IEC/IEEE 29148)
- Requisitos: <!-- RF-xx / RNF-xx; si se agrega o cambia uno, actualizar docs/calidad/matriz-trazabilidad-29148.md -->
- Pruebas: <!-- clases o funciones de prueba nuevas o modificadas; particiones y valores límite (29119-4) -->

## Definición de "hecho" (task.md)
- [ ] Código en inglés; documentación afectada actualizada en español
- [ ] Pruebas nuevas y CI en verde (incluido el smoke test con Docker)
- [ ] Endpoints nuevos en Swagger, con su `code` de error en `docs/logica-negocio.md`
- [ ] Cambios de esquema en una migración Flyway nueva
- [ ] Sin secretos en el repositorio
- [ ] Riesgos (31000) y evaluación 25010 revisados si el cambio los afecta
