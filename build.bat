@echo off
setlocal
cd /d "%~dp0"

where node >nul 2>nul || (echo Bitte zuerst Node.js installieren: https://nodejs.org & pause & exit /b)
where java >nul 2>nul || (echo Bitte zuerst Java 21 (JDK) installieren: https://adoptium.net & pause & exit /b)

echo.
echo === 1/3 NoLimite-Mod bauen ===
pushd mod
call gradle build --console=plain
if errorlevel 1 (
  echo.
  echo Mod-Bau fehlgeschlagen. Fallbacks:
  echo  - Java 21 (JDK) installieren, nicht nur die JRE
  echo  - oder: Repo auf GitHub hochladen, dort Actions -> "Build NoLimite"
  popd
  pause
  exit /b 1
)
popd

echo.
echo === 2/3 Mod in den Launcher legen ===
for %%f in (mod\build\libs\nolimite-*.jar) do (
  if /i not "%%f"=="%~f0" (
    copy /y "%%f" "resources\nolimite-mod.jar" >nul
    echo resources\nolimite-mod.jar aktualisiert
  )
)

echo.
echo === 3/3 Launcher bauen ===
call npm install
call npm run build

echo.
echo Fertig. Das Setup liegt in dist\.
explorer dist
pause