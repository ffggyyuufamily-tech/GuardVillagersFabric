@echo off
setlocal enabledelayedexpansion

rem ==========================================================
rem  Gradle Startup Script (Enhanced)
rem ==========================================================

if "%DEBUG%"=="" echo off

set "APP_HOME=%~dp0"
for %%i in ("%APP_HOME%") do set "APP_HOME=%%~fi"

set "APP_BASE_NAME=%~n0"

rem JVM default options (can override via JAVA_OPTS / GRADLE_OPTS)
set "DEFAULT_JVM_OPTS=-Xmx256m -Xms64m"

rem ----------------------------------------------------------
rem Locate Java
rem ----------------------------------------------------------

set "JAVA_EXE="

if defined JAVA_HOME (
    set "JAVA_HOME=%JAVA_HOME:"=%"
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    
    if not exist "!JAVA_EXE!" (
        echo ERROR: JAVA_HOME is set but java.exe not found at:
        echo   !JAVA_EXE!
        goto fail
    )
) else (
    set "JAVA_EXE=java.exe"
    where java >nul 2>nul
    if errorlevel 1 (
        echo ERROR: JAVA_HOME is not set and 'java' is not in PATH.
        echo Please install JDK or set JAVA_HOME.
        goto fail
    )
)

rem ----------------------------------------------------------
rem Run Gradle
rem ----------------------------------------------------------

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% ^
  "-Dorg.gradle.appname=%APP_BASE_NAME%" ^
  -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*

set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" goto fail

endlocal
exit /b 0

:fail
echo.
echo Gradle failed with exit code %EXIT_CODE%
endlocal
exit /b %EXIT_CODE%