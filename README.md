# upload-api

API de orquestación de __uploads multipart directos a almacenamiento S3-compatible__ (MinIO en desarrollo) para archivos de video grandes. Spring Boot 3.5 · Java 21 · PostgreSQL · S3.

El API **nunca ve los bytes**: genera URLs presignadas para que el cliente suba cada parte directamente a MinIO/S3 y luego usa los metadatos (ETags) para completar el objeto.

> ⚠️ **Identidad temporal**: hasta que se implante JWT, el "usuario" se lee de un header (`X-User-Id`). En el perfil `local` hay un `default-user-id` para no tener que enviarlo siempre. **No usar en producción sin JWT.**

---

## Características

- Uploads multipart con **presigned URLs** por parte → los bytes no pasan por Spring.
- Idempotencia por `Idempotency-Key` en `POST /v1/uploads` y en `complete`.
- Concurrencia controlada con **compare-and-set** sobre el estado (CAS en la BD).
- Soporte de reanudación: el `GET` devuelve las partes ya almacenadas **con sus ETags**, así que se reanuda sin estado local.
- Cualquier tipo de archivo (allowlist configurable) conservando el nombre original para la descarga.
- Listado paginado y descarga por URL firmada: los bytes tampoco pasan por Spring al bajar.
- `ddl-auto` configurable; JPA lista para migraciones (`validate`).
- Errores uniformes `{code, message, path, traceId, timestamp}` con `ErrorCode` de negocio.
- OpenAPI/Swagger, Actuator, tracing (traceId/spanId), logging estructurado.

## Arquitectura

Hexagonal (clean): el dominio no depende de Spring, JPA ni del SDK de S3.

```
interfaces/  →  application/  →  domain/  ←  infrastructure/
 (REST/DTO)    (servicios)      (modelo)      (S3, JPA, security)
```

Flujo de un upload:

```
Cliente      API (Spring)          PostgreSQL      MinIO/S3
  │──POST uploads──────────▶│  ─guardar sesión─▶│
  │◀─201 {uploadId,partSize}│
  │──POST parts─────────────▶│                 │──createMultipartUpload───────▶│
  │◀─presigned URLs (PUT)────│
  │──PUT parte 1/2/3─────────│                 │◀─────────────────────────── a MinIO ─┐
  │──POST parts/n/ack────────▶│  ─CAS+upsert──▶│
  │──POST complete──────────▶│  ─CAS COMPLETING▶│──completeMultipartUpload──▶│
  │◀─200 {status: COMPLETED}─│  ─CAS COMPLETED─│
```

## Requisitos

- JDK 21 (verificado con 21.0.2)
- Maven 3.8+
- Docker Desktop (para infra local y tests de integración)

## Levantar y ejecutar

### 1) Infraestructura local (PostgreSQL + MinIO)

```bash
docker compose up -d --build
docker compose ps
```

- PostgreSQL 16 en `localhost:5432` (`upload`/`upload`, db `upload`)
- MinIO S3 API en `localhost:9000`, consola web en `http://localhost:9001` (`minioadmin`/`minioadmin`)
- El job `minio-init` crea el bucket `video-uploads` automáticamente.

### 2) Compilar

```bash
mvn clean package -DskipTests
```

### 3) Arrancar el API (perfil `local`)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Puntos de entrada:

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:8080/api/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api/v3/api-docs |
| Health | http://localhost:8080/api/actuator/health |
| API base | http://localhost:8080/api/v1/uploads |

## Endpoints

Todos bajo el contexto `/api` y con `X-User-Id` (en `local` es opcional).

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/v1/uploads` | Crea sesión (201). Repite con misma `Idempotency-Key` → 200. |
| `POST` | `/v1/uploads/{id}/parts` | Presigned URLs (PUT) para un conjunto de partes. |
| `GET` | `/v1/uploads/{id}` | Estado y partes subidas. |
| `POST` | `/v1/uploads/{id}/parts/{n}/ack` | Registra parte subida (etag + tamaño). |
| `POST` | `/v1/uploads/{id}/complete` | Completa el objeto (lista de partes + ETags). Idempotente → 200. |
| `DELETE` | `/v1/uploads/{id}` | Cancela y aborta el multipart en storage. Idempotente. |
| `GET` | `/v1/uploads?status=&page=&size=` | Listado paginado del usuario, más reciente primero. `status` acepta varios: `?status=CREATED,UPLOADING`. |
| `GET` | `/v1/uploads/{id}/download` | URL firmada de descarga. Sirve el objeto con su **nombre original** (`Content-Disposition`). Requiere `COMPLETED`/`PROCESSING`/`READY`. |

Estados: `CREATED → UPLOADING → COMPLETING → COMPLETED → PROCESSING → READY` y terminales `FAILED`, `CANCELLED`, `EXPIRED`.

### Nombres de archivo y tipos

El nombre que manda el cliente se guarda **tal cual** y es el que recibe el usuario al descargar. Para la clave de almacenamiento se sanea (`ObjectKey`): separadores de ruta, acentos y caracteres de control fuera, longitud acotada, extensión conservada.

```
fileName  "Informe Anual Ñandú 2026.pdf"      ← se guarda y se descarga así
objectKey "uploads/{uploadId}/informe-anual-nandu-2026.pdf"
```

`app.upload.allowed-content-types` acepta `*` para admitir cualquier tipo. En el perfil `local` está en `*`; en producción se restringe sin tocar código.

### Qué pasa si una parte falla

Tres capas, de dentro hacia fuera:

1. **Por parte**: el cliente reintenta con backoff exponencial. Un 403 del almacenamiento significa firma caducada, no transferencia fallida: se pide una URL nueva y **no consume intento**.
2. **Por sesión**: si una parte agota sus intentos, la subida se marca fallida. Las partes ya `ack` siguen en Postgres con su ETag y el multipart sigue abierto en S3: **no se pierde nada**.
3. **Reanudar**: `GET /v1/uploads/{id}` devuelve las partes ya almacenadas con sus ETags, así que el cliente pide presigned URLs solo de lo que falta y completa con la lista entera. Reintentar **nunca** abre una sesión nueva.

Las presigned URLs se piden **por lotes justo antes de usarlas**, no todas al empezar: con TTL de 15 minutos, firmar 500 partes de golpe garantiza que la cola de una subida lenta llegue con firmas muertas.

## Probando la API

### Opción A: script automático (recomendado)

```powershell
.\scripts\test-api.ps1
```

Sube un archivo de 25 MiB (3 partes de 10 MiB) de punta a punta: crea → presigna → hace PUT de cada parte a MinIO → ack → complete → verifica → y además valida errores (extensión no permitida, usuario ajeno → 403).

Variables opcionales:

```powershell
.\scripts\test-api.ps1 -BaseUrl http://localhost:8080/api `
  -UserId 00000000-0000-0000-0000-000000000001 -FileSize 26214400
```

### Opción B: `api.http` (interactivo, IDE)

Abre `api.http` con el **HTTP Client de IntelliJ IDEA** o **REST Client de VS Code**. Los requests están encadenados (autocaptura de `uploadId` y URLs). Los bloques `PUT` suben los archivos `testdata/part1.bin`, `part2.bin` y `part3.bin` (10 MiB, 10 MiB y 5 MiB; ya generados) directamente a MinIO y capturan su `ETag`. Ejecuta los bloques en orden: 1 → 1b → 2 → 3 → 4 → 5 → 6; los bloques 7-9 son casos de cancelación y de error.

### Opción C: curl

```bash
# 1) Crear sesión (25 MiB → 3 partes de 10 MiB)
curl -s -X POST http://localhost:8080/api/v1/uploads \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"fileName":"clip.mp4","contentType":"video/mp4","size":26214400}'

# Guarda {uploadId, partSize:10485760, totalParts:3}
# 2) Pedir presigned URLs de todas las partes
curl -s -X POST http://localhost:8080/api/v1/uploads/<uploadId>/parts \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"partNumbers":[1,2,3]}'

# 3) Subir cada parte directamente a MinIO con la URL presignada (PUT + body binario)
curl -s -T part1.bin "<uploadUrl de la parte 1>" \
  -o /dev/null -w "%{http_code}\n"          # → 200, anota el ETag del response

# 4) Avisar al API que la parte 1 ya está (por cada parte)
curl -s -X POST http://localhost:8080/api/v1/uploads/<uploadId>/parts/1/ack \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"etag":"<etag parte 1>","size":10485760}'

# 5) Completar
curl -s -X POST http://localhost:8080/api/v1/uploads/<uploadId>/complete \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"parts":[
         {"partNumber":1,"etag":"<etag parte 1>"},
         {"partNumber":2,"etag":"<etag parte 2>"},
         {"partNumber":3,"etag":"<etag parte 3>"}]}'
# → {"status":"COMPLETED",...}
```

El objeto queda en MinIO en `video-uploads/videos/{uploadId}/original.mp4`.

## Tests automáticos

```bash
mvn test
```

- **Unit** (53): dominio, saneado de nombres, guards, crear/presign/complete/cancel/descargar.
- **Integración** (7, Testcontainers PostgreSQL + MinIO): flujo real E2E. **Requieren Docker**; si Docker no está activo se skipean (`disabledWithoutDocker`).

## Consola web (`web/`)

SPA de React + TypeScript que ejecuta el flujo real contra este API y lo muestra mientras ocurre.
No es una demo simulada: corta el archivo con `File.slice`, hace los `PUT` a las URLs firmadas,
manda los `ack` y el `complete`, y consulta el estado en bucle.

```bash
cd web
npm install
cp .env.example .env.local   # ajusta la URL del API si no es localhost:8080
npm run dev                  # http://localhost:5173
npm test                     # Vitest sobre el orquestador de subida
```

La consola está montada como un **rack de sala de máquinas**: paneles atornillados a dos
raíles de 19", lámparas tally, medidores de programa y una bahía de patcheo. La regla del
diseño es que cada elemento físico está accionado por un dato real — una lámpara o un
medidor que no mide nada no existe.

| Panel | Para qué sirve |
|---|---|
| Transporte | Único panel de aluminio, para que el ojo aterrice ahí. Contador de siete segmentos dibujado, timecode, caudal, y las lámparas `ON AIR` / `FAULT` / `READY` / `PLAYBACK`. |
| Puente de medidores | Un canal por parte, con balística de medidor real: sube de golpe, baja despacio, y deja marca de pico. Pasadas 48 partes los canales se vuelven pelos para que 500 sigan siendo un solo objeto legible. |
| Bahía de patcheo | El enrutamiento como lo que es. El cable grueso del suelo es el archivo: sale del jack del navegador y entra en el del almacenamiento sin tocar el del API. |
| Cadena de señal | `CREATED → … → COMPLETED` como relés. Los dos últimos, `PROCESSING` y `READY`, están dibujados **sin cable detrás**: nada los acciona. |
| Registro | Cada petición con método, estado y latencia. El sondeo aparece como lo que es: un repique idéntico. |
| Catálogo de cintas | Lo que el API guarda para este operador. Descargar las completas, reanudar las que quedaron a medias. |
| Banco de pruebas | Canales simultáneos (1–8) en un conmutador detentado, inyectar fallo en un canal, y purgar la memoria local para reanudar solo con lo que responde el API. |

> Convención de color de sala de máquinas, no de web: **rojo es transmitiendo**, no error. El
> fallo es ámbar y parpadea. Toda lámpara lleva su palabra grabada al lado.

Las decisiones visuales duraderas están en `DESIGN.md`; el producto, en `PRODUCT.md`.

Requiere CORS habilitado en el API (`app.cors.allowed-origins`, ya configurado en el perfil `local`)
y en MinIO (`MINIO_API_CORS_ALLOW_ORIGIN` en `docker-compose.yml`). Sin el segundo, el navegador
sube la parte pero no puede leer el header `ETag`, y sin `ETag` no hay `ack` ni `complete`.

## Configuración (perfil `local`)

| Propiedad | Valor local | Descripción |
|---|---|---|
| `app.upload.max-size-bytes` | 5 GiB | tamaño máximo aceptado |
| `app.upload.part-size-bytes` | 10 MiB | tamaño fijo de cada parte |
| `app.upload.allowed-content-types` | `*` | allowlist MIME; `*` admite cualquier tipo |
| `app.upload.presigned-url-ttl` | 15 min | TTL de cada presigned URL |
| `app.upload.session-ttl` | 24 h | ventana de la sesión |
| `app.security.default-user-id` | fijo | identidad local (solo `local`) |
| `server.context-path` | `/api` | prefijo global |

En entornos reales estas claves vienen del entorno (`DB_URL`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`, `UPLOAD_MAX_SIZE_BYTES`, …) sin defaults: arranque fail-fast si faltan.

## ¿Dónde entra Kafka?

No en la subida. El navegador ya habla directo con el almacenamiento y el API solo lleva el libro:
la parte síncrona son llamadas JSON de milisegundos.

La pregunta empieza en `COMPLETED`. Ahí el objeto existe, la petición HTTP terminó, y el enum
`UploadStatus` promete `COMPLETED → PROCESSING → READY` sin que nadie mueva esas dos flechas. Ese
hueco se ve en la consola web: el estado deja de avanzar y las consultas siguen devolviendo lo mismo.

| Opción | Cuándo alcanza |
|---|---|
| Nada | no hay post-proceso. El upload ya terminó. |
| `@Async` / `@TransactionalEventListener` | una tarea corta que puedes perder si el proceso reinicia. |
| Outbox + tabla de jobs | sobrevive reinicios y da reintentos con la BD que ya tienes; un solo consumidor lógico. |
| **Kafka** | un `upload.completed` con varios consumidores independientes (transcodificar, miniaturas, antivirus, avisar) que escalan y fallan por separado, con reintentos y replay. |

Kafka resuelve fan-out durable, no "hacer el upload asíncrono": eso ya lo es.

## Próximos pasos

- Autenticación JWT (reemplazar `HeaderUserAuthenticationFilter`).
- Publicar `upload.completed` en Kafka y un consumidor que mueva `COMPLETED → PROCESSING → READY`.
- Transporte push (SSE) para el estado, reemplazando el polling de la consola web.
- Limpiador ("janitor") de sesiones `EXPIRED`/huérfanas y abort de multiparts.
- Migraciones Flyway/Liquibase.