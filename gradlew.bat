@echo off
:: NOTE: Any file paths passed as arguments should be relative to the 'third_party' 
:: directory (or fully qualified).

cd /d "%~dp0third_party"
call gradlew.bat %*
