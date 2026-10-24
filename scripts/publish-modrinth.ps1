param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectId,
    [string]$Jar = "dist\ChunkView-0.4.1-mc26.2.jar",
    [string]$Token = $env:MODRINTH_TOKEN
)

$ErrorActionPreference = "Stop"
if (-not $Token) {
    throw "MODRINTH_TOKEN is not set. Create a Modrinth token with version-create permission first."
}
if (-not (Test-Path $Jar)) {
    throw "Jar not found: $Jar"
}

$changelog = Get-Content "CHANGELOG.md" -Raw
$body = @{
    name           = "ChunkView 0.4.1"
    version_number = "0.4.1"
    changelog      = $changelog
    dependencies   = @(
        @{ project_id = "P7dR8mSH"; dependency_type = "required" }
    )
    game_versions  = @("26.2")
    version_type   = "release"
    loaders        = @("fabric")
    featured       = $false
    project_id     = $ProjectId
} | ConvertTo-Json -Depth 10

$temp = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($temp, $body, [System.Text.UTF8Encoding]::new($false))
    & curl.exe --fail-with-body -X POST "https://api.modrinth.com/v2/version" `
        -H "Authorization: $Token" `
        -F "data=@$temp;type=application/json" `
        -F "file=@$Jar"
    if ($LASTEXITCODE -ne 0) {
        throw "Modrinth upload failed with exit code $LASTEXITCODE"
    }
} finally {
    Remove-Item $temp -Force -ErrorAction SilentlyContinue
}
