@echo off
rem ===================================================================
rem  Baut die fertige Saturn Client - Installation fuer Windows.
rem
rem  Schritte:
rem    1. die Mod, eine Jar je unterstuetzter Minecraft-Version
rem    2. den Launcher daraus
rem    3. das Ergebnis anzeigen
rem
rem  Voraussetzungen: Node.js (https://nodejs.org) und ein JDK 25
rem  (https://adoptium.net). Die Mod enthaelt Klassen fuer Java 21, wie
rem  Minecraft 1.21.x es braucht; zum Bauen der Mod verwendet buildmod.bat
rem  selbst ein passendes JDK, falls eines daneben liegt.
rem ===================================================================
setlocal
cd /d "%~dp0"

where node >nul 2>nul || (
  echo.
  echo Bitte zuerst Node.js installieren: https://nodejs.org
  pause
  exit /b 1
)

echo.
echo === 1/2 Saturn-Mod bauen (eine Jar je Minecraft-Version) ===
call buildmod.bat
if errorlevel 1 (
  echo.
  echo Mod-Bau fehlgeschlagen. Moegliche Gruende:
  echo  - kein JDK 25 und kein JDK 21 gefunden
  echo  - oder: Repo auf GitHub hochladen, dort Actions -^> "Build Saturn Client"
  pause
  exit /b 1
)

echo.
echo === 2/2 Launcher bauen ===
call npm install
if errorlevel 1 (
  echo npm install fehlgeschlagen.
  pause
  exit /b 1
)
call npm run build
if errorlevel 1 (
  echo Launcher-Bau fehlgeschlagen.
  pause
  exit /b 1
)

echo.
echo Fertig. Das Setup liegt in dist\.
explorer dist
pause