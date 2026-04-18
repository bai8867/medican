param(
    [Parameter(Mandatory = $true)]
    [string]$TargetProjectPath,

    [ValidateSet("base", "research", "browser-heavy")]
    [string]$Scenario = "base",

    [ValidateSet("merge", "replace")]
    [string]$Mode = "merge"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-TemplatePath {
    param(
        [string]$TemplateRoot,
        [string]$TemplateScenario
    )

    switch ($TemplateScenario) {
        "base" { return Join-Path $TemplateRoot "base\mcp.json" }
        "research" { return Join-Path $TemplateRoot "scenarios\research\mcp.json" }
        "browser-heavy" { return Join-Path $TemplateRoot "scenarios\browser-heavy\mcp.json" }
        default { throw "Unsupported scenario: $TemplateScenario" }
    }
}

function Read-JsonFile {
    param([string]$Path)
    $raw = Get-Content -Path $Path -Raw -Encoding UTF8
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "JSON file is empty: $Path"
    }
    return $raw | ConvertFrom-Json
}

function Has-Property {
    param(
        [object]$InputObject,
        [string]$PropertyName
    )

    if ($null -eq $InputObject) {
        return $false
    }

    return $null -ne $InputObject.PSObject.Properties[$PropertyName]
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$templateRoot = Join-Path $repoRoot "templates\cursor-tools"
$templateMcpPath = Resolve-TemplatePath -TemplateRoot $templateRoot -TemplateScenario $Scenario

if (-not (Test-Path $templateMcpPath)) {
    throw "Template mcp.json not found: $templateMcpPath"
}

$targetProjectResolved = Resolve-Path $TargetProjectPath
$targetCursorDir = Join-Path $targetProjectResolved ".cursor"
$targetMcpPath = Join-Path $targetCursorDir "mcp.json"

if (-not (Test-Path $targetCursorDir)) {
    New-Item -Path $targetCursorDir -ItemType Directory | Out-Null
}

$templateJson = Read-JsonFile -Path $templateMcpPath

if ($Mode -eq "replace") {
    if (Test-Path $targetMcpPath) {
        $backupPath = Join-Path $targetCursorDir ("mcp.json.bak-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
        Copy-Item -Path $targetMcpPath -Destination $backupPath -Force
        Write-Host "Backed up existing mcp.json => $backupPath"
    }

    $templateJson | ConvertTo-Json -Depth 100 | Set-Content -Path $targetMcpPath -Encoding UTF8
    Write-Host "Applied scenario '$Scenario' with mode 'replace' => $targetMcpPath"
}
else {
    $targetJson = $null

    if (Test-Path $targetMcpPath) {
        $backupPath = Join-Path $targetCursorDir ("mcp.json.bak-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
        Copy-Item -Path $targetMcpPath -Destination $backupPath -Force
        Write-Host "Backed up existing mcp.json => $backupPath"
        $targetJson = Read-JsonFile -Path $targetMcpPath
    }
    else {
        $targetJson = [pscustomobject]@{}
    }

    if (-not (Has-Property -InputObject $targetJson -PropertyName "mcpServers") -or $null -eq $targetJson.mcpServers) {
        $targetJson | Add-Member -MemberType NoteProperty -Name "mcpServers" -Value ([pscustomobject]@{})
    }

    foreach ($server in $templateJson.mcpServers.PSObject.Properties) {
        $targetJson.mcpServers | Add-Member -MemberType NoteProperty -Name $server.Name -Value $server.Value -Force
    }

    $targetJson | ConvertTo-Json -Depth 100 | Set-Content -Path $targetMcpPath -Encoding UTF8
    Write-Host "Applied scenario '$Scenario' with mode 'merge' => $targetMcpPath"
}

$envTemplatePath = Join-Path $templateRoot ".cursor-tools.env.example"
$targetEnvExamplePath = Join-Path $targetProjectResolved ".cursor-tools.env.example"

if (Test-Path $envTemplatePath) {
    Copy-Item -Path $envTemplatePath -Destination $targetEnvExamplePath -Force
    Write-Host "Synced env template => $targetEnvExamplePath"
}

Write-Host ""
Write-Host "Next steps:"
Write-Host "1) Fill API keys in .cursor-tools.env.example (or your global env file)."
Write-Host "2) Restart Cursor (or reload MCP servers)."
Write-Host "3) Verify tools in Cursor MCP panel."
