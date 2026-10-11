. "$PSScriptRoot/environment.ps1"
$runtime = Join-Path $project '.runtime'
New-Item -ItemType Directory -Force -Path $runtime | Out-Null
function New-Secret {
    $bytes = New-Object byte[] 32
    $generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($bytes)
    $generator.Dispose()
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLower()
}
if (!(Test-Path "$project/.env")) {
    $ownerPassword = New-Secret
    $apiPassword = New-Secret
    $adminKey = New-Secret
    $superPassword = New-Secret
    @"
DATABASE_URL=postgresql://media_owner:${ownerPassword}@127.0.0.1:55432/media_intelligence
JDBC_DATABASE_URL=jdbc:postgresql://127.0.0.1:55432/media_intelligence
DB_USER=media_api
DB_PASSWORD=$apiPassword
MIGRATION_USER=media_owner
MIGRATION_PASSWORD=$ownerPassword
ADMIN_API_KEY=$adminKey
LOCAL_PG_PASSWORD=$superPassword
"@ | Set-Content "$project/.env" -Encoding UTF8
    . "$PSScriptRoot/environment.ps1"
}
$env:PGPASSWORD = $env:LOCAL_PG_PASSWORD
if (!(Test-Path "$runtime/pgdata/PG_VERSION")) {
    $passwordFile = Join-Path $runtime 'pg-password.txt'
    [System.IO.File]::WriteAllText($passwordFile, $env:LOCAL_PG_PASSWORD)
    try {
        & initdb -D "$runtime/pgdata" -U postgres -A scram-sha-256 --pwfile="$passwordFile" --encoding=UTF8 --locale=C
        if ($LASTEXITCODE -ne 0) { throw 'initdb gagal' }
    } finally { Remove-Item $passwordFile -ErrorAction SilentlyContinue }
    Add-Content "$runtime/pgdata/postgresql.conf" "`nlisten_addresses='127.0.0.1'`nport=55432`nmax_connections=30`nshared_buffers='64MB'"
}
& pg_ctl -D "$runtime/pgdata" status 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    & pg_ctl -D "$runtime/pgdata" -l "$runtime/postgresql.log" -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL tidak dapat dimulai' }
}
$existing = & psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='media_intelligence'"
if (!$existing) {
    $sql = @"
CREATE ROLE media_owner LOGIN PASSWORD '$env:MIGRATION_PASSWORD';
CREATE ROLE media_api LOGIN PASSWORD '$env:DB_PASSWORD';
CREATE DATABASE media_intelligence OWNER media_owner;
"@
    $sql | & psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'Pembuatan database gagal' }
}
$grants = @"
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER DEFAULT PRIVILEGES FOR ROLE media_owner IN SCHEMA public GRANT SELECT ON TABLES TO media_api;
GRANT USAGE ON SCHEMA public TO media_api;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO media_api;
"@
$grants | & psql -h 127.0.0.1 -p 55432 -U postgres -d media_intelligence -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw 'Pengaturan akses database gagal' }
Write-Output 'Database siap di 127.0.0.1:55432. Jalankan backend untuk migrasi pertama.'
