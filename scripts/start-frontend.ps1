. "$PSScriptRoot/environment.ps1"
Push-Location "$project/frontend"
try {
    & bun run dev
    if ($LASTEXITCODE -ne 0) { throw 'Frontend Indomonitor gagal dimulai' }
} finally { Pop-Location }
