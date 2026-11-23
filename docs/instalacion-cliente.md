# Instalación en el servidor del cliente

Guía para instalar, actualizar y respaldar RiTech en la infraestructura del cliente
(una instalación por empresa). La operación diaria está en
[manual-operacion.md](manual-operacion.md).

## 1. Requisitos del servidor

| Recurso | Mínimo | Recomendado |
|---|---|---|
| CPU | 2 vCPU | 4 vCPU |
| RAM | 4 GB | 8 GB |
| Disco | 20 GB SSD | 50 GB SSD (respaldos incluidos) |
| Sistema | Linux x86-64 con Docker Engine 24+ y Docker Compose v2 | Ubuntu Server 24.04 LTS |
| Red | Salida HTTPS (Alpha Vantage, Gemini y SMTP si se usan) | Dominio propio con DNS apuntando al servidor |

Solo hay que abrir los puertos 80 y 443, que usa el proxy con TLS. La API (8080) queda detrás
del proxy, y Postgres y el motor de cálculo no publican ningún puerto.

## 2. Instalación

1. Copiar el proyecto al servidor (o usar las imágenes versionadas de `ghcr.io`, §5).
2. Configurar el entorno:
   ```bash
   cp .env.example .env
   openssl rand -base64 48        # valor de JWT_SECRET
   ```
   Completar en `.env` como mínimo: `POSTGRES_PASSWORD`, `JWT_SECRET`, `BOOTSTRAP_ADMIN_EMAIL`,
   `BOOTSTRAP_ADMIN_PASSWORD` y `CORS_ALLOWED_ORIGINS` (dominio del frontend).
3. Licencia: copiar el archivo entregado por RiTech a `license/license.json` y poner
   `LICENSE_MODE=enforced`. Se instala sola en el primer arranque.
4. Arrancar:
   ```bash
   docker compose up -d --build --wait
   curl -s http://localhost:8080/api/v1/health      # {"status":"UP",...}
   ```
5. Primer ingreso: el ADMIN inicial entra con `BOOTSTRAP_ADMIN_EMAIL` y la contraseña temporal,
   y el sistema le exige cambiarla. Después crea los demás usuarios, entre ellos un IT_MANAGER.

## 3. TLS con reverse proxy (Caddy)

Caddy obtiene y renueva el certificado de Let's Encrypt automáticamente. Ejemplo de `Caddyfile`:

```
api.empresa.com {
    reverse_proxy localhost:8080
}
```

Con el proxy activo se pone `REQUIRE_HTTPS=true` en `.env`: la API redirige cualquier petición
HTTP a HTTPS y envía HSTS. Las cabeceras de seguridad (CSP, `X-Content-Type-Options`,
`X-Frame-Options`, `Referrer-Policy`) ya las envía la API, así que el proxy no tiene que repetirlas.
`CORS_ALLOWED_ORIGINS` debe tener solo el dominio del frontend (p. ej. `https://app.empresa.com`).

La API ya confía en `X-Forwarded-*` (`server.forward-headers-strategy=framework`), así que la
auditoría registra la IP real del cliente. Con Nginx funciona igual: `proxy_pass` hacia
`127.0.0.1:8080`, más `proxy_set_header X-Forwarded-For` y `X-Forwarded-Proto`.

## 4. Respaldos y restauración

El servicio `pg-backup` hace un `pg_dump` (formato custom, comprimido) al arrancar y luego
cada `BACKUP_INTERVAL_HOURS` (24 h). Borra los archivos con más de `BACKUP_RETENTION_DAYS`
días (14) y los guarda en `BACKUP_DIR` (`./backups`).

- **RPO**: como máximo 24 h de datos (25010, recuperabilidad).
- Copiar `./backups` fuera del servidor (otro disco, almacenamiento en la nube) es
  responsabilidad del cliente: un respaldo que vive solo en el mismo disco no protege contra
  su pérdida.

```bash
# Respaldo inmediato
docker compose exec pg-backup sh /scripts/backup.sh

# Restauración de prueba en una base aparte (no toca la base en uso)
docker compose exec pg-backup sh /scripts/restore.sh /backups/ritech-<fecha>.dump ritech_restore_check

# Restauración real (RTO objetivo ≤ 2 h)
docker compose stop api
docker compose exec pg-backup sh /scripts/restore.sh /backups/ritech-<fecha>.dump ritech --yes
docker compose start api
```

En cada ejecución, el CI hace un respaldo, lo restaura en una base aparte y compara los datos.
Así la restauración queda probada de forma continua.

## 5. Actualización de versión

1. Revisar las notas de la versión y hacer un respaldo inmediato (§4).
2. `git pull` (o cambiar la etiqueta de imagen, p. ej. `ghcr.io/<owner>/ritech-api:0.7.0`).
3. `docker compose up -d --build --wait`. Flyway aplica las migraciones nuevas al arrancar.
4. Verificar `/api/v1/health` y el estado de la licencia (`GET /api/v1/license`).

Si algo falla, se vuelve a la etiqueta anterior y se restaura el respaldo del paso 1. Las
migraciones no tienen paso de reversión automática.

## 6. Variables principales

Todas están documentadas en [`.env.example`](../.env.example): base de datos, seguridad,
mercado, IA, noticias, correo (SMTP), licencia (`LICENSE_MODE`, `LICENSE_DIR`) y respaldos
(`BACKUP_DIR`, `BACKUP_INTERVAL_HOURS`, `BACKUP_RETENTION_DAYS`).
