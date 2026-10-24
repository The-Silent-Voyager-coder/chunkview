param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectId,
    [string]$Jar = "",
    [string]$Token = $env:MODRINTH_TOKEN
)

$ErrorActionPreference = "Stop"
if (-not $Token) {
    throw "MODRINTH_TOKEN is not set. Create a Modrinth token with version-create permission first."
}

$version = ((Select-String -Path "gradle.properties" -Pattern '^version=').Line -split '=', 2)[1].Trim()
if (-not $Jar) {
    $Jar = "dist\ChunkView-$version-mc26.2.jar"
}
if (-not (Test-Path $Jar)) {
    throw "Jar not found: $Jar"
}
$JarPath = (Resolve-Path $Jar).Path

$changelog = Get-Content "CHANGELOG.md" -Raw
$body = @{
    name           = "ChunkView $version"
    version_number = $version
    changelog      = $changelog
    dependencies   = @(
        @{ project_id = "P7dR8mSH"; dependency_type = "required" }
    )
    game_versions  = @("26.2")
    version_type   = "release"
    loaders        = @("fabric")
    featured       = $false
    project_id     = $ProjectId
    file_parts     = @("file")
} | ConvertTo-Json -Depth 10

$temp = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($temp, $body, [System.Text.UTF8Encoding]::new($false))
    & curl.exe --fail-with-body --max-time 120 -X POST "https://api.modrinth.com/v2/version" `
        -H "Authorization: $Token" `
        -H "User-Agent: chunkview-publisher/$version" `
        -F "data=@$temp;type=application/json" `
        -F "file=@$JarPath;type=application/java-archive"
    if ($LASTEXITCODE -ne 0) {
        throw "Modrinth upload failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
