@echo off
rem ===================================================================
rem  Baut die Saturn-Mod - eine Jar je unterstuetzter Minecraft-Version.
rem
rem  Warum so viele Builds: Yarn benennt zwischen den Minecraft-Versionen
rem  um (Methoden, Klassen). Eine Jar, die fuer 1.21.1 gebaut ist, zeigt auf
rem  1.21.11 ins Leere - dann fehlen genau die Menues und Knöpfe. Also
rem  bekommt jede Version ihre eigene Jar, und der Launcher legt die
rem  passende in die Instanz.
rem
rem  Welche Versionen es sind und welche Yarn-/Fabric-API-Fassung dazugehoert,
rem  steht in mod\versions.json.
rem
rem  Aufruf:
rem    buildmod.bat                  alle Versionen bauen
rem    buildmod.bat 1.21.4           nur diese eine
rem
rem  Ergebnis: mod\build\libs\saturn-<version>-<modversion>.jar
rem ===================================================================
setlocal enabledelayedexpansion
set "TOOLS=F:\LingLing\.toolcache"
cd /d "%~dp0"

rem Gradle laeuft auf Java 25 - das verlangt das aktuelle Fabric-Loom beim
rem Start. Erzeugt werden aber weiterhin Klassen fuer Java 21, so wie es
rem Minecraft braucht.
rem
rem Wichtig: nichts davon darf fest verdrahtet sein. Auf einem Build-Runner
rem (GitHub Actions) gibt es den Toolordner F:\LingLing\.toolcache nicht, und
rem Java 25 liefert der Workflow bereits mit. Deshalb wird JAVA_HOME nur
rem gesetzt, wenn der lokale Pfad wirklich existiert - sonst bleibt, was da
rem ist. Das war der Grund, warum der erste Build auf GitHub abbrach.
if exist "%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\java.exe" (
  set "JAVA_HOME=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-25.0.3.9-hotspot"
) else if exist "%TOOLS%\jdk-21.0.12.1+1\bin\java.exe" (
  set "JAVA_HOME=%TOOLS%\jdk-21.0.12.1+1"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"

rem Gradle: der Wrapper ist die verlaesliche Quelle. Er laedt genau die in
rem gradle\wrapper\gradle-wrapper.properties genannte Fassung selbst und
rem funktioniert damit ueberall - lokal wie auf dem Build-Runner. Ohne ihn
rem haengt der Build an einem Gradle, das irgendwo im Pfad liegen muss, und
rem genau daran ist der erste Lauf auf GitHub gescheitert.
set "WRAPPER=%~dp0mod\gradlew.bat"
set "GRADLE=%WRAPPER%"
if not exist "%GRADLE%" (
  set "GRADLE=%TOOLS%\gradle-9.8.0\bin\gradle.bat"
)
if not exist "%GRADLE%" set "GRADLE="
if not defined GRADLE (
  for /f "usebackq delims=" %%g in (`where gradle.bat 2^>nul`) do if not defined GRADLE set "GRADLE=%%g"
)
if not defined GRADLE (
  echo Gradle nicht gefunden.
  echo   erwartet: mod\gradlew.bat
  echo   oder:    %TOOLS%\gradle-9.8.0\bin\gradle.bat
  echo   oder:    gradle.bat im PATH
  echo Nachladen: https://services.gradle.org/distributions
  pause
  exit /b 1
)
echo Gradle: %GRADLE%

rem ------------------------------------------------------------- Versionsliste
rem Die Liste holt sich Gradle aus mod\versions.json - damit gibt es nur eine
rem Stelle, an der Versionen eingetragen werden.
set "LISTE=%~dp0mod\build\versionen.txt"
if not "%~1"=="" (
  set "VERSIONEN=%~1"
) else (
  pushd mod
  call "%GRADLE%" saturnVersionenDatei --no-daemon --console=plain >nul 2>&1
  popd
  if not exist "%LISTE%" (
    echo Versionsliste nicht erzeugt - mod\build\versionen.txt fehlt.
    pause
    exit /b 1
  )
  set "VERSIONEN="
  for /f "usebackq tokens=*" %%v in ("%LISTE%") do set "VERSIONEN=!VERSIONEN! %%v"
)

if "%VERSIONEN%"=="" (
  echo Keine Version gefunden - mod\versions.json lesbar?
  pause
  exit /b 1
)

echo.
echo === Saturn-Mod: %VERSIONEN% ===
echo.

set "ERFOLGREICH="
set "FEHLGESCHLAGEN="
for %%v in (%VERSIONEN%) do (
  set "SATURN_MC=%%v"
  echo --- Minecraft %%v ---
  pushd mod
  call "%GRADLE%" build --no-daemon --console=plain
  if errorlevel 1 (
    set "FEHLGESCHLAGEN=!FEHLGESCHLAGEN! %%v"
    echo     FEHLGESCHLAGEN
  ) else (
    set "ERFOLGREICH=!ERFOLGREICH! %%v"
    echo     OK
  )
  popd
  echo.
)

echo === Ergebnis ===
if not "%ERFOLGREICH%"=="" echo   gebaut     :%ERFOLGREICH%
if not "%FEHLGESCHLAGEN%"=="" echo   gescheitert:%FEHLGESCHLAGEN%
echo.
echo Jars in mod\build\libs:
dir /b mod\build\libs\*.jar
echo.

rem ---------------------- Jars in den Launcher uebernehmen (resources\)
if not "%ERFOLGREICH%"=="" (
  for %%j in (mod\build\libs\saturn-*.jar) do (
    if /i not "%%j"=="%~f0" (
      copy /y "%%j" "resources\%%~nxj" >nul && echo   resources\%%~nxj
    )
  )
  echo.
  echo Der Launcher legt resources\saturn-<version>.jar automatisch in das
  echo Profil, dessen Minecraft-Version dazu passt.
)
exit /b 0