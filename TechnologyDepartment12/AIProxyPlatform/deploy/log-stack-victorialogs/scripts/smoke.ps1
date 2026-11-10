# 冒烟：检查 compose 语法、容器状态、/health、LogsQL 查询。
# 用法：在 deploy/log-stack-victorialogs 下执行  pwsh -File .\scripts\smoke.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$ComposeArgs)
    docker compose --env-file .env.example @ComposeArgs
}

Write-Host "== compose config（中心机） =="
Invoke-Compose config --quiet
Write-Host "中心机 compose 语法通过"

Write-Host "== compose config（业务机 Vector） =="
docker compose --env-file (Join-Path $Root "vector\.env.example") -f (Join-Path $Root "vector\docker-compose.yml") --profile demo config --quiet
Write-Host "Vector compose 语法通过"

$dockerOk = $true
try {
    docker info | Out-Null
} catch {
    $dockerOk = $false
}

if (-not $dockerOk) {
    Write-Host "Docker 未运行，跳过在线检查。请先启动 Docker Desktop 再执行："
    Write-Host "  docker compose --env-file .env.example up -d"
    Write-Host "  docker compose --env-file vector\.env.example -f vector\docker-compose.yml --profile demo up -d"
    exit 0
}

Write-Host "== 容器状态 =="
Invoke-Compose ps

$vlBase = "http://127.0.0.1:9428"

function Get-DotEnvValue {
    param(
        [string]$Path,
        [string]$Key,
        [string]$Default
    )
    if (-not (Test-Path $Path)) {
        return $Default
    }
    $line = Get-Content -LiteralPath $Path | Where-Object { $_ -match "^\s*$([regex]::Escape($Key))=" } | Select-Object -First 1
    if (-not $line) {
        return $Default
    }
    return ($line -split "=", 2)[1].Trim().Trim('"').Trim("'")
}

$envFile = if (Test-Path (Join-Path $Root ".env")) { Join-Path $Root ".env" } else { Join-Path $Root ".env.example" }
$vlUser = Get-DotEnvValue -Path $envFile -Key "VL_AUTH_USERNAME" -Default "admin"
$vlPass = Get-DotEnvValue -Path $envFile -Key "VL_AUTH_PASSWORD" -Default "changeme"

function Invoke-Curl {
    param([string[]]$CurlArgs)
    & curl.exe -sS --max-time 15 @CurlArgs
}

Write-Host "== VictoriaLogs /health =="
try {
    $healthCode = & curl.exe -sS -o NUL -w "%{http_code}" --max-time 5 -u "${vlUser}:${vlPass}" "$vlBase/health"
    if ($healthCode -ne "200") {
        throw "HTTP $healthCode"
    }
    Write-Host "GET $vlBase/health -> $healthCode"
} catch {
    Write-Host "VictoriaLogs 未就绪：$($_.Exception.Message)"
    Write-Host "先启动：docker compose --env-file .env.example up -d"
    exit 0
}

Write-Host "== LogsQL（limit=5） =="
try {
    $content = Invoke-Curl @("-u", "${vlUser}:${vlPass}", "-X", "POST", "$vlBase/select/logsql/query", "-d", "query=*", "-d", "limit=5")
    $lines = @($content -split "(`r`n|`n)" | Where-Object { $_.Trim() -ne "" })
    Write-Host "匹配行数（最多 5）：$($lines.Count)"
    $lines | Select-Object -First 3 | ForEach-Object { Write-Host $_ }
} catch {
    Write-Host "查询失败：$($_.Exception.Message)"
}

Write-Host "== LogsQL error =="
try {
    $content = Invoke-Curl @("-u", "${vlUser}:${vlPass}", "-X", "POST", "$vlBase/select/logsql/query", "-d", "query=error", "-d", "limit=5")
    $lines = @($content -split "(`r`n|`n)" | Where-Object { $_.Trim() -ne "" })
    Write-Host "error 行数（最多 5）：$($lines.Count)"
    $lines | Select-Object -First 3 | ForEach-Object { Write-Host $_ }
} catch {
    Write-Host "查询失败：$($_.Exception.Message)"
}

Write-Host ""
Write-Host "内置 UI: http://127.0.0.1:9428/select/vmui/"
Write-Host "业务机 Vector: docker compose --env-file vector\.env.example -f vector\docker-compose.yml --profile demo up -d"
