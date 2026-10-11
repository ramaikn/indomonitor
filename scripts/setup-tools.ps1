$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
. "$PSScriptRoot/environment.ps1"
New-Item -ItemType Directory -Force -Path $tools | Out-Null

function Get-Archive($url, $name, $checksum) {
    $file = Join-Path $tools $name
    if (!(Test-Path $file)) { Invoke-WebRequest -Uri $url -OutFile $file -UseBasicParsing -TimeoutSec 600 }
    if ($checksum -and (Get-FileHash $file -Algorithm SHA256).Hash.ToLower() -ne $checksum.ToLower()) {
        Remove-Item $file
        throw "Checksum tidak cocok: $name"
    }
    return $file
}

if (!(Test-Path "$tools/jdk/bin/java.exe")) {
    $assets = Invoke-RestMethod 'https://api.adoptium.net/v3/assets/latest/21/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
    $package = $assets[0].binary.package
    $archive = Get-Archive $package.link 'jdk.zip' $package.checksum
    Expand-Archive $archive "$tools/jdk-extract" -Force
    $jdk = Get-ChildItem "$tools/jdk-extract" -Directory | Select-Object -First 1
    Move-Item $jdk.FullName "$tools/jdk"
    Remove-Item "$tools/jdk-extract", $archive -Recurse -Force
}
if (!(Test-Path "$tools/maven/bin/mvn.cmd")) {
    $url = 'https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip'
    $archive = Get-Archive $url 'maven.zip' ''
    $expected = (Invoke-WebRequest "$url.sha512" -UseBasicParsing).Content.Trim().Split(' ')[0]
    if ((Get-FileHash $archive -Algorithm SHA512).Hash.ToLower() -ne $expected.ToLower()) { throw 'Checksum Maven tidak cocok' }
    & tar -xf $archive -C $tools
    if ($LASTEXITCODE -ne 0) { throw 'Ekstraksi Maven gagal' }
    Move-Item "$tools/apache-maven-3.9.11" "$tools/maven"
    Remove-Item $archive
}
if (!(Test-Path "$tools/pgsql/.ready")) {
    $archive = Get-Archive 'https://get.enterprisedb.com/postgresql/postgresql-17.9-1-windows-x64-binaries.zip' 'postgresql.zip' ''
    # Paket GUI/StackBuilder tidak diperlukan server lokal dan menghabiskan ruang.
    & tar -xf $archive -C $tools 'pgsql/bin' 'pgsql/lib' 'pgsql/share'
    if ($LASTEXITCODE -ne 0) { throw 'Ekstraksi PostgreSQL gagal' }
    New-Item "$tools/pgsql/.ready" -ItemType File -Force | Out-Null
    Remove-Item $archive
}
& "$tools/jdk/bin/java.exe" -version
& "$tools/pgsql/bin/postgres.exe" --version
Write-Output "Alat lokal siap di $tools. Tidak ada perubahan PATH sistem atau layanan Windows."
