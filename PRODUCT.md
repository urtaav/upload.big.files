# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

A single backend developer, the author of the API, using the console to understand a
mechanism he built. He reads it from a large second monitor next to his IDE, in daylight,
glancing over while a transfer runs rather than sitting in front of it. No other audience:
it is never shown to teammates, stakeholders or end users, so nothing needs to be softened
or explained for a non-technical reader. Domain vocabulary (part, ETag, presigned URL, CAS,
multipart) is native to him and should be used, not translated.

## Product Purpose

Make a direct-to-storage multipart upload observable while it happens, so the developer can
see where the boundaries of the system actually are and decide what infrastructure the
system does or does not need. Success is a decision made from evidence on screen, not a
completed upload: the upload is the demonstration, not the goal.

## Positioning

It does not simulate the flow, it executes it. Every box that lights up corresponds to a
real HTTP request against the running Spring API and a real PUT to object storage. The
console's one irreplaceable claim: it shows that the file's bytes never traverse the
application server, and it shows the exact point where the synchronous flow ends and
nothing takes over.

## Operating Context

Permanent test bench, never a product surface. It runs against `localhost` with Docker
Compose infrastructure (PostgreSQL + MinIO) alongside it. The developer drives it
deliberately and adversarially: raising concurrency to watch wall-clock change, forcing a
part to fail to watch retry and resume, discarding local state to prove the server is the
source of truth. Sessions are short and hands-on, interleaved with reading Java code in the
IDE on the other screen.

## Capabilities and Constraints

- Real flow: create session → presigned URLs per part → PUT direct to storage → ack per
  part → complete → poll status. Bytes never pass through the API, on the way up or down.
- The browser slices the file with `File.slice`; the whole file is never held in memory.
- Any content type, any size up to the API limit (5 GiB locally). The original file name is
  preserved for download; the storage key is sanitised separately.
- Parts are uploaded through a fixed-size worker pool (1–8 configurable). Failures retry
  with exponential backoff; a rejected signature is re-signed without consuming an attempt.
- Presigned URLs are requested in batches just before use, because they expire in 15 min.
- Resume rebuilds from `GET /v1/uploads/{id}` alone, including the ETags of stored parts.
- A file explorer lists what the API holds for the user, with download and resume.
- Status has no push channel: the browser polls. The cost of that polling is itself
  something the console must make visible.
- `UploadStatus` declares `COMPLETED → PROCESSING → READY`, and nothing currently drives
  the last two transitions. This dead end is a deliberate subject of the UI, not a defect
  to hide.
- Identity is a simulated `X-User-Id` header; JWT is explicitly out of scope for now.
- Undecided: whether Kafka, an outbox, or nothing will eventually drive post-processing.
  The console exists partly to inform that decision and must not presuppose an answer.

## Brand Commitments

None. No logo, no existing identity, no colour or typographic constraint inherited from
anywhere. The API is named `upload-api`; the domain package is `com.videoflow.upload`.

## Evidence on Hand

- Running Spring Boot API with OpenAPI at `/api/swagger-ui.html`; 53 backend unit tests.
- Working frontend in `web/` with 11 tests over the upload orchestrator.
- Real uploads already performed through the console, including a 500 MB file and a 5 MB
  PDF verified byte-identical on download.
- Real lifecycle states, real error codes (`ErrorCode`), real latencies and part counts are
  all available at runtime. Nothing about the flow needs to be invented or mocked.
- No production deployment, no users, no benchmarks at scale. Any figure beyond what the
  local stack produces would be fabrication.

## Product Principles

- Show the mechanism, do not describe it. If the screen states something, the state it
  refers to must be visible next to the claim.
- The server is the source of truth; whenever the browser's belief and the API's answer can
  differ, show both rather than reconciling them silently.
- Instrument over assistant. The developer is driving on purpose, including into failure;
  the interface never protects him from an outcome he asked for.
- Density is correct here. This is a bench, not an onboarding surface.
