. "$PSScriptRoot/environment.ps1"
$env:PGPASSWORD = $env:LOCAL_PG_PASSWORD
$exists = & psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='media_intelligence_test'"
if (!$exists) {
    & psql -h 127.0.0.1 -p 55432 -U postgres -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE media_intelligence_test OWNER media_owner'
    if ($LASTEXITCODE -ne 0) { throw 'Pembuatan database test gagal' }
}
$env:PGPASSWORD = $env:MIGRATION_PASSWORD
& psql -h 127.0.0.1 -p 55432 -U media_owner -d media_intelligence_test -v ON_ERROR_STOP=1 -c 'DROP SCHEMA public CASCADE; CREATE SCHEMA public;'
& psql -h 127.0.0.1 -p 55432 -U media_owner -d media_intelligence_test -v ON_ERROR_STOP=1 -f "$project/database/V1__initial.sql"
if ($LASTEXITCODE -ne 0) { throw 'Schema pengujian gagal' }
$env:TEST_DATABASE_URL = $env:DATABASE_URL.Replace('/media_intelligence','/media_intelligence_test')
$failure = $false
Push-Location "$project/pipeline"
try {
    & uv run --link-mode hardlink --module pytest -q
    if ($LASTEXITCODE -ne 0) { $failure = $true }
    & uv run --link-mode hardlink ruff check src tests
    if ($LASTEXITCODE -ne 0) { $failure = $true }
} finally { Pop-Location }
Push-Location "$project/backend"
try {
    & mvn -B test
    if ($LASTEXITCODE -ne 0) { $failure = $true }
} finally { Pop-Location }
Push-Location "$project/frontend"
try {
    & bun run build
    if ($LASTEXITCODE -ne 0) { $failure = $true }
} finally { Pop-Location }
if ($failure) { throw 'Ada pemeriksaan gagal. Periksa output sebelum melanjutkan.' }
Write-Output 'Pengujian kritis, pemeriksaan Python, dan build frontend berhasil.'
