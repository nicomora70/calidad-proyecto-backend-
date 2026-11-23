# Herramienta de licencias (uso interno de RiTech)

Emite las licencias firmadas (Ed25519, formato `ritech-license-v1`) que se entregan a cada
cliente. Solo necesita un JDK 17+ y se ejecuta sin compilar.

```bash
# 1. Una sola vez: par de claves del proveedor (FUERA del repositorio)
java tools/license/LicenseTool.java keygen ../ritech-license-keys
#    -> private-key.pem (secreta) y public-key.pem
#    Copiar public-key.pem a api-java/src/main/resources/license/public-key.pem

# 2. Por cliente: emitir la licencia
java tools/license/LicenseTool.java issue \
     --key ../ritech-license-keys/private-key.pem \
     --company "Empresa Cliente S.A.S." --plan PROFESSIONAL --max-users 25 \
     --expires 2027-11-30 --id RT-2026-001 --out license.json
```

- Planes: `BASIC`, `PROFESSIONAL`, `ENTERPRISE` (módulos en `docs/manual-operacion.md`).
- La firma cubre los bytes exactos del `payload`. Cualquier cambio (plan, usuarios, fecha) la
  invalida y la API responde `400 LICENSE_INVALID`.
- **`private-key.pem` nunca se sube al repositorio** (está en `.gitignore`) ni se entrega a un
  cliente. Quien tiene la clave privada puede emitir licencias.
- Si la clave privada se filtra: generar un par nuevo, publicar una versión con la nueva
  `public-key.pem` y volver a emitir las licencias vigentes.

## Alcance de la protección

La licencia es un control comercial, no un mecanismo anticopia: el software corre en el servidor
del cliente. Sirve para que el cliente no pueda editar el plan, los usuarios ni la fecha sin que
se note. La API vuelve a verificar la firma de la licencia guardada en cada arranque.
