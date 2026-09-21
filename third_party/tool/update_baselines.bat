@echo off
setlocal

:: Run the IntelliJ plugin verifier and refresh the committed baselines.
:: Run this from the repository root.
::
:: The comparison logic lives in check_verifier_baselines.sh so that the
:: Windows, POSIX, and CI paths can never drift apart. Git for Windows provides
:: the bash used below.

if not exist "third_party" (
    echo Error: This script must be run from the repository root directory.
    exit /b 1
)

where bash >nul 2>nul
if errorlevel 1 (
    echo Error: 'bash' was not found on PATH.
    echo Install Git for Windows ^(which provides Git Bash^) and re-run, or run
    echo   ./third_party/tool/update_baselines.sh
    echo from a bash shell.
    exit /b 1
)

echo Running plugin verification...
if exist "third_party\build\reports\pluginVerifier" (
    rd /s /q "third_party\build\reports\pluginVerifier"
)

pushd third_party
call gradlew.bat verifyPlugin
popd

:: verifyPlugin exits non-zero when it finds problems, which is exactly the
:: case we want to re-baseline, so its status is intentionally ignored.

bash "third_party/tool/check_verifier_baselines.sh" update
exit /b %errorlevel%
