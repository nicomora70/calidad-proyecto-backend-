# Aviso de privacidad y supresión de datos — Ley 1581 de 2012

Plantilla que cada empresa cliente adapta y publica, porque el **responsable del tratamiento**
es la empresa que instala RiTech (instalación por cliente). El proveedor del software no recibe
ni trata los datos.

## 1. Aviso de privacidad (plantilla)

> **[Empresa]**, NIT **[...]**, con domicilio en **[...]**, es responsable del tratamiento de los
> datos personales registrados en la plataforma RiTech, conforme a la Ley 1581 de 2012 y el
> Decreto 1377 de 2013.
>
> **Datos tratados:** nombre, correo electrónico, simulaciones de inversión realizadas y, por
> seguridad, la dirección IP y el navegador de las acciones registradas en la auditoría.
>
> **Finalidad:** permitir el acceso a la plataforma, ejecutar y guardar las simulaciones,
> enviar notificaciones y cumplir los controles de seguridad.
>
> **Derechos del titular:** conocer, actualizar, rectificar y suprimir sus datos, revocar la
> autorización y presentar quejas ante la Superintendencia de Industria y Comercio.
>
> **Canal:** **[correo del área de datos personales]**. Respuesta en máximo 15 días hábiles.

## 2. Qué se guarda y dónde

| Dato | Tabla | Tras la supresión |
|---|---|---|
| Nombre y correo | `users` | Reemplazados por "Titular suprimido" y `deleted-<id>@anonymized.invalid` |
| Contraseña (hash BCrypt) | `users` | Reemplazada por un hash aleatorio (nadie puede entrar) |
| Sesiones | `refresh_tokens` | Revocadas |
| Bandeja y preferencias | `notifications`, `notification_preferences` | Eliminadas |
| Simulaciones | `simulations`, `ai_alerts` | Se conservan **sin identidad**: el registro ya no lleva a ninguna persona |
| Auditoría (IP, navegador) | `audit_log` | Se conserva como registro de seguridad (deber legal de demostrar el cumplimiento; 27001 A.8.15) |
| Respaldos | `./backups` | Rotan a los `BACKUP_RETENTION_DAYS` (14) días; después de ese plazo el dato desaparece también de ellos |

## 3. Procedimiento de supresión (habeas data)

1. El titular pide la supresión por el canal del aviso. El responsable verifica su identidad.
2. El ADMIN obtiene el `id` del usuario: en la lista `GET /api/v1/users` (incluye los inactivos)
   o, si ya fue eliminado (la lista no muestra eliminados), directamente en la base de datos:
   `docker compose exec postgres psql -U ritech -c "select id from users where lower(email) = lower('correo@titular')"`.
3. El ADMIN ejecuta `POST /api/v1/users/{id}/anonymize` (204). Es **irreversible**, funciona
   también con usuarios ya eliminados y repetirlo no tiene efecto.
4. Queda el evento `USER_ANONYMIZED` en la auditoría: `GET /api/v1/audit?action=USER_ANONYMIZED`.
5. Se responde al titular dentro de los 15 días hábiles, indicando que los respaldos se
   depuran solos en el plazo de retención.

No se puede suprimir al último ADMIN activo (`409 LAST_ADMIN`): primero hay que nombrar otro.
