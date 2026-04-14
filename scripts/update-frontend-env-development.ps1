param(
  [Parameter(Mandatory = $true)]
  [string] $FrontendDir,
  [Parameter(Mandatory = $false)]
  [AllowEmptyString()]
  [string] $ApiBase = ''
)

$dir = [System.IO.Path]::GetFullPath($FrontendDir)
$path = Join-Path $dir '.env.development'
$lines = @()
if (Test-Path -LiteralPath $path) {
  $lines = Get-Content -LiteralPath $path -Encoding UTF8
}
$out = New-Object System.Collections.Generic.List[string]
$found = $false
foreach ($line in $lines) {
  if ($line -match '^\s*VITE_API_BASE_URL\s*=') {
    $found = $true
    if (-not [string]::IsNullOrWhiteSpace($ApiBase)) {
      [void]$out.Add('VITE_API_BASE_URL=' + $ApiBase)
    }
  }
  else {
    [void]$out.Add($line)
  }
}
if (-not $found -and -not [string]::IsNullOrWhiteSpace($ApiBase)) {
  [void]$out.Add('VITE_API_BASE_URL=' + $ApiBase)
}
if ($out.Count -eq 0 -and -not (Test-Path -LiteralPath $path)) {
  exit 0
}
$out | Set-Content -LiteralPath $path -Encoding UTF8
