. "$PSScriptRoot/environment.ps1"
& pg_ctl -D "$project/.runtime/pgdata" -m fast -w stop
if ($LASTEXITCODE -ne 0) { throw 'Penghentian PostgreSQL gagal' }
