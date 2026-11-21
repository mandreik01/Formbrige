param([switch]$WithBielik)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
New-Item -ItemType Directory -Force -Path models | Out-Null

$pllumUrl = if ($env:PLLUM_URL) { $env:PLLUM_URL } else { "https://huggingface.co/mradermacher/PLLuM-4B-instruct-2512-i1-GGUF/resolve/main/PLLuM-4B-instruct-2512.i1-Q4_K_M.gguf" }
$bielikUrl = if ($env:BIELIK_URL) { $env:BIELIK_URL } else { "https://huggingface.co/speakleash/Bielik-1.5B-v3.0-Instruct-GGUF/resolve/main/Bielik-1.5B-v3.0-Instruct.Q8_0.gguf" }

function Get-Model($url, $name) {
    $out = Join-Path "models" $name
    if ((Test-Path $out) -and (Get-Item $out).Length -gt 0) { Write-Host "Jest juz: $out"; return }
    Write-Host "Pobieram $out"
    $headers = @()
    if ($env:HF_TOKEN) { $headers = @("-H", "Authorization: Bearer $($env:HF_TOKEN)") }
    & curl.exe -fL --retry 3 -C - @headers -o "$out.part" $url
    if ($LASTEXITCODE -ne 0) { throw "Nie udalo sie pobrac $name" }
    Move-Item -Force "$out.part" $out
}

Get-Model $pllumUrl "PLLuM-4B-instruct-2512.i1-Q4_K_M.gguf"
if ($WithBielik) { Get-Model $bielikUrl "Bielik-1.5B-v3.0-Instruct.Q8_0.gguf" }
Get-ChildItem models
