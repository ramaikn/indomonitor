$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if (Test-Path "$project/.env") {
    Get-Content "$project/.env" | ForEach-Object {
        if ($_ -match '^([^#=]+)=(.*)$') { [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process') }
    }
}
$tools = if ($env:INDOMONITOR_TOOLS_DIR) { $env:INDOMONITOR_TOOLS_DIR } else { Join-Path $project '.tools' }
$env:JAVA_HOME = Join-Path $tools 'jdk'
$env:PATH = "$env:JAVA_HOME\bin;$tools\maven\bin;$tools\pgsql\bin;$env:PATH"
