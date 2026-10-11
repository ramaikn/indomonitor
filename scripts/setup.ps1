. "$PSScriptRoot/environment.ps1"
& "$PSScriptRoot/setup-tools.ps1"
Push-Location "$project/frontend"
try {
    & bun install --backend hardlink --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Pemasangan frontend gagal' }
} finally { Pop-Location }
Push-Location "$project/pipeline"
try {
    & uv sync --link-mode hardlink --frozen
    if ($LASTEXITCODE -ne 0) { throw 'Pemasangan pipeline gagal' }
} finally { Pop-Location }
& "$PSScriptRoot/start-database.ps1"
