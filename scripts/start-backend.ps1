. "$PSScriptRoot/environment.ps1"
if (!(Test-Path "$project/.env")) { throw 'Jalankan start-database.ps1 dahulu.' }
$port = if ($env:API_PORT) { [int]$env:API_PORT } else { 8080 }
$listener = [System.Net.Sockets.TcpClient]::new()
try {
    $busy = $listener.ConnectAsync('127.0.0.1', $port).Wait(500) -and $listener.Connected
} catch { $busy = $false } finally { $listener.Dispose() }
if ($busy) { throw "Port $port sudah digunakan. Hentikan backend lama sebelum menjalankan Indomonitor; proses lain tidak dihentikan otomatis." }
Push-Location "$project/backend"
try {
    & mvn package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw 'Build backend gagal' }
    $runtimeJar = Join-Path $project '.runtime/application.jar'
    New-Item -ItemType Directory -Force (Split-Path $runtimeJar -Parent) | Out-Null
    Copy-Item 'target/media-intelligence-0.1.0.jar' $runtimeJar -Force
    & java -jar $runtimeJar
    if ($LASTEXITCODE -ne 0) { throw 'Backend Indomonitor berhenti dengan kesalahan' }
} finally { Pop-Location }
