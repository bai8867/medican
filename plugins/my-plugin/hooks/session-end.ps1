#!/usr/bin/env pwsh

$ErrorActionPreference = "Stop"

try {
  $eventJson = [Console]::In.ReadToEnd()
  $logDir = Join-Path $PSScriptRoot ".."
  $logPath = Join-Path $logDir "hook.log"
  $timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")

  if ([string]::IsNullOrWhiteSpace($eventJson)) {
    $line = "$timestamp | sessionEnd | no payload"
  } else {
    $line = "$timestamp | sessionEnd | payload received"
  }

  Add-Content -Path $logPath -Value $line
  exit 0
}
catch {
  [Console]::Error.WriteLine("session-end hook failed: $($_.Exception.Message)")
  exit 0
}
