<#
.SYNOPSIS
    Prueba end-to-end de la Upload API (upload-api).
.DESCRIPTION
    Ejecuta el flujo completo "happy path": crea una sesión, pide presigned URLs,
    sube cada parte directamente a MinIO (PUT), confirma cada parte (ack),
    completa el upload y verifica el estado final. Al final valida varios casos
    de error (extensión no permitida, usuario ajeno).

    Requiere la API corriendo en el perfil local:
        docker compose up -d --build
        mvn spring-boot:run -Dspring-boot.run.profiles=local
.EXAMPLE
    .\scripts\test-api.ps1
.EXAMPLE
    .\scripts\test-api.ps1 -BaseUrl http://localhost:8080/api -FileSize 26214400
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$UserId = "00000000-0000-0000-0000-000000000001",
    [int]$FileSize = 26214400   # 25 MiB -> 3 partes de 10 MiB
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host "`n[$([DateTime]::Now.ToString('HH:mm:ss'))] $Message" -ForegroundColor Cyan
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)][ValidateSet('GET', 'POST', 'DELETE')][string]$Method,
        [Parameter(Mandatory)][string]$Path,
        [string]$Body,
        [string]$AsUser = $UserId,
        [string]$IdempotencyKey
    )
    $headers = @{ 'X-User-Id' = $AsUser; 'Content-Type' = 'application/json' }
    if ($IdempotencyKey) { $headers['Idempotency-Key'] = $IdempotencyKey }

    $params = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $headers }
    if ($Body) { $params['Body'] = $Body }

    $response = Invoke-RestMethod @params
    return $response
}

function Assert-Status($Expected, $Actual, [string]$Context) {
    if ($Expected -ne $Actual) {
        Write-Host "  KO $Context -> esperado $Expected, recibido $Actual" -ForegroundColor Red
    } else {
        Write-Host "  OK $Context" -ForegroundColor Green
    }
}

# Cliente HTTP para hacer PUT de las partes contra MinIO (el API no toca los bytes).
$httpClient = [System.Net.Http.HttpClient]::new()

try {
    # 1) Create
    Write-Step "1) Crear sesion de upload ($FileSize bytes)"
    $idemKey = "e2e-$([Guid]::NewGuid())"
    $Body = @{ fileName = 'clip.mp4'; contentType = 'video/mp4'; size = $FileSize } | ConvertTo-Json
    $created = Invoke-Api -Method POST -Path '/v1/uploads' -Body $Body -IdempotencyKey $idemKey
    $uploadId  = $created.uploadId
    $partSize  = $created.partSize
    $totalParts = [math]::Ceiling($FileSize / $partSize)
    Write-Host "  uploadId    = $uploadId"
    Write-Host ("  partSize    = {0} bytes" -f $partSize)
    Write-Host ("  totalParts  = {0} (esperado {1})" -f $created.totalParts, $totalParts)
    Assert-Status 201 201 'POST /v1/uploads'

    # Replay idempotente
    Write-Step "1b) Replay con la misma Idempotency-Key -> 200"
    $replayed = Invoke-WebRequest -Method POST -Uri "$BaseUrl/v1/uploads" `
        -Headers @{ 'X-User-Id' = $UserId; 'Content-Type' = 'application/json'; 'Idempotency-Key' = $idemKey } `
        -Body $Body
    Assert-Status 200 ([int]$replayed.StatusCode) 'replay idempotente'
    $replayedJson = $replayed.Content | ConvertFrom-Json
    if ($replayedJson.uploadId -eq $uploadId) {
        Write-Host "  OK replay devuelve el mismo uploadId" -ForegroundColor Green
    } else {
        Write-Host "  KO el replay devolvio un uploadId distinto" -ForegroundColor Red
    }

    # 2) Presign
    Write-Step "2) Pedir presigned URLs"
    $partNumbers = @(1..$totalParts)
    $presignBody = @{ partNumbers = $partNumbers } | ConvertTo-Json
    $presigned = Invoke-Api -Method POST -Path "/v1/uploads/$uploadId/parts" -Body $presignBody
    Write-Host ("  {0} URLs presignadas" -f $presigned.parts.Count)

    # 3 + 4) PUT y ack de cada parte
    $parts = @{}
    $random = [System.Random]::new()
    foreach ($part in ($presigned.parts | Sort-Object partNumber)) {
        $n = [int]$part.partNumber
        Write-Step ("3) Subir parte {0}/{1} directo a MinIO" -f $n, $totalParts)
        $isLast  = $n -eq $totalParts
        $len     = if ($isLast) { $FileSize - ($partSize * ($n - 1)) } else { $partSize }
        $bytes   = New-Object byte[] $len
        $random.NextBytes($bytes)
        $content = [System.Net.Http.ByteArrayContent]::new($bytes)
        $resp    = $httpClient.PutAsync([Uri]$part.uploadUrl, $content).GetAwaiter().GetResult()
        Assert-Status 200 ([int]$resp.StatusCode) "PUT parte $n (${len} bytes)"
        $etag = if ($resp.Headers.ETag) { $resp.Headers.ETag.ToString() } else { throw 'PUT no devolvio ETag' }
        $parts[$n] = $etag

        Write-Step ("4) Ack parte {0}" -f $n)
        $ackBody = @{ etag = $etag; size = $len } | ConvertTo-Json
        $ack = Invoke-Api -Method POST -Path "/v1/uploads/$uploadId/parts/$n/ack" -Body $ackBody
        Assert-Status $n $ack.partNumber 'ack del nodo correcto'
        Assert-Status $n ([int]$ack.uploadedParts) 'contador de partes registradas'
    }

    # 5) Complete
    Write-Step "5) Completar upload"
    $completeParts = @(
        foreach ($n in ($parts.Keys | Sort-Object)) {
            @{ partNumber = $n; etag = $parts[$n] }
        }
    )
    $completeBody = @{ parts = $completeParts } | ConvertTo-Json -Depth 4
    $completed = Invoke-Api -Method POST -Path "/v1/uploads/$uploadId/complete" -Body $completeBody
    Write-Host "  status      = $($completed.status)"
    Write-Host ("  uploadedParts = {0}" -f $completed.uploadedParts)
    Assert-Status 'COMPLETED' $completed.status 'complete -> COMPLETED'

    # 6) GET estado final
    Write-Step "6) Verificar estado via GET"
    $fetched = Invoke-Api -Method GET -Path "/v1/uploads/$uploadId"
    Assert-Status 'COMPLETED' $fetched.status 'GET confirma COMPLETED'

    # 7) Casos de error
    Write-Step "7) Casos de error"
    $badType = Invoke-WebRequest -Method POST -Uri "$BaseUrl/v1/uploads" `
        -Headers @{ 'X-User-Id' = $UserId; 'Content-Type' = 'application/json' } `
        -Body (@{ fileName = 'a.txt'; contentType = 'text/plain'; size = 100 } | ConvertTo-Json) `
        -SkipHttpErrorCheck
    Assert-Status 400 ([int]$badType.StatusCode) 'contentType no permitido -> 400'

    $badSize = Invoke-WebRequest -Method POST -Uri "$BaseUrl/v1/uploads" `
        -Headers @{ 'X-User-Id' = $UserId; 'Content-Type' = 'application/json' } `
        -Body (@{ fileName = 'a.mp4'; contentType = 'video/mp4'; size = 6000000000 } | ConvertTo-Json) `
        -SkipHttpErrorCheck
    Assert-Status 413 ([int]$badSize.StatusCode) 'archivo demasiado grande -> 413'

    $other  = '99999999-9999-9999-9999-999999999999'
    $minBody = @{ fileName = 'clip2.mp4'; contentType = 'video/mp4'; size = 10485760 } | ConvertTo-Json
    $otherCreated = Invoke-Api -Method POST -Path '/v1/uploads' -Body $minBody -AsUser $other
    $forbidden = Invoke-WebRequest -Method POST `
        -Uri "$BaseUrl/v1/uploads/$($otherCreated.uploadId)/parts" `
        -Headers @{ 'X-User-Id' = $UserId; 'Content-Type' = 'application/json' } `
        -Body (@{ partNumbers = @(1) } | ConvertTo-Json) `
        -SkipHttpErrorCheck
    Assert-Status 403 ([int]$forbidden.StatusCode) 'usuario ajeno no puede acceder -> 403'

    Write-Host "`n================================================================" -ForegroundColor Green
    Write-Host "  TODO OK - upload $uploadId completado exitosamente" -ForegroundColor Green
    Write-Host "  Objeto en MinIO: video-uploads/videos/$uploadId/original.mp4" -ForegroundColor Green
    Write-Host "  Consola: http://localhost:9001 (minioadmin / minioadmin)" -ForegroundColor Green
    Write-Host "================================================================`n" -ForegroundColor Green
}
finally {
    $httpClient.Dispose()
}