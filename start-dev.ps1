# MiniJava Compiler 开发环境启动脚本
# 后端: vis (Spring Boot)  |  前端: Vue/minijava (Vite)

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  MiniJava Compiler - 启动开发环境" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 启动后端 (Spring Boot)
Write-Host "`n[1/2] 启动后端 Spring Boot (vis)..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Write-Host '=== 后端: Spring Boot (vis) ===' -ForegroundColor Yellow; Set-Location '$root\vis'; mvn spring-boot:run"
) -WindowStyle Normal

# 稍等让后端先启动
Write-Host "  等待 3 秒后启动前端..."
Start-Sleep -Seconds 3

# 启动前端 (Vite + Vue)
Write-Host "[2/2] 启动前端 Vite (Vue/minijava)..." -ForegroundColor Green
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-Command',
    "Write-Host '=== 前端: Vite Dev Server (Vue/minijava) ===' -ForegroundColor Yellow; Set-Location '$root\Vue\minijava'; npm run dev"
) -WindowStyle Normal

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  后端和前端已在各自窗口中启动!" -ForegroundColor Cyan
Write-Host "  后端:  mvn spring-boot:run  (vis)" -ForegroundColor White
Write-Host "  前端:  npm run dev           (Vue/minijava)" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan
