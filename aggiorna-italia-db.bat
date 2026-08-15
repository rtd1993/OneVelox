@echo off
title OneVelox - Aggiorna italia.db
cd /d "%~dp0"
echo.
echo  OneVelox - aggiornamento POI nazionali (italia.db)
echo  Finestra da lasciare aperta: Overpass puo impiegare 10-40 minuti.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\aggiorna-italia-db.ps1"
set ERR=%ERRORLEVEL%
echo.
if %ERR% NEQ 0 (
  echo  Aggiornamento non completato. Codice %ERR%
) else (
  echo  Aggiornamento completato.
)
echo.
pause
exit /b %ERR%
