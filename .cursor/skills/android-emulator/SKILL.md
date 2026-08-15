---
name: android-emulator
description: Build, install, launch, and inspect OneVelox on the Android Studio emulator from Cursor. Use when testing the app, starting the emulator, reading logcat, capturing screenshots, or fixing runtime crashes on AVD OneVelox_API35.
---

# OneVelox Android emulator

## Environment

Dot-source `tools/android-env.ps1` or call `tools/emulator-run.ps1`. Never use system Java 8.

| Item | Value |
|------|--------|
| SDK | `C:\Users\rtd19\AppData\Local\Android\Sdk` |
| JBR | `C:\Program Files\Android\Android Studio\jbr` |
| AVD | `OneVelox_API35` |
| Default flavor | `beta` → `com.onevelox.app.beta` |
| Activity | `com.onevelox.app.MainActivity` |
| Logs | `%TEMP%\onevelox-emulator\` |

Windows sandbox cannot isolate this machine. Run Shell with `required_permissions: ["all"]`.

## Commands

```
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 status
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 run
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 logs
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 screenshot
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 geo
powershell -ExecutionPolicy Bypass -File tools/emulator-run.ps1 crash
```

`run` starts the AVD if needed, installs the flavor, grants location, launches the app, dumps logcat, and pulls a screenshot. Leave the emulator window open for the user.

Flavors: `beta` (simulation), `phone`, `prod`. Pass `-Flavor phone` / `-Flavor prod` when needed.

## After every install/launch

1. Read `%TEMP%\onevelox-emulator\errors-*.txt` and `crash-*.txt`.
2. Read the latest `screen-*.png` with the Read tool.
3. Fix crashes / fatal exceptions in app code, then `run` or `install` + `launch` again.
4. Do not kill the emulator unless the user asks.

## GPS mock

`adb emu geo fix <lon> <lat>` — Milan default: `9.1900 45.4642`.
