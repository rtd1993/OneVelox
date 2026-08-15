# OneVelox - Cose Fatte (Sessione corrente)

## Data
- 2026-08-13
- 2026-08-14

## Attivita completate
1. Analisi iniziale requisiti prodotto OneVelox.
2. Definizione concettuale app non-turn-by-turn basata su posizione e direzione.
3. Definizione funzionalita core:
   - alert strada principale con range variabile 200-1000m
   - opzione strade limitrofe con soglia iniziale 100m
   - velocita consigliata e avviso superamento limite
4. Definizione UI concettuale:
   - indicatori GPS/Internet/Database
   - animazione auto su strada principale
   - indicatori laterali destra/sinistra per pericoli su strade di svolta

## Verifica API Waze (ricerca documentale)
1. Verificata documentazione developers Waze.
2. Rilevate integrazioni ufficiali:
   - Deep Links
   - Transport SDK (accesso partner)
   - Data Feeds partner per closures/incidents
3. Conclusione attuale:
   - non emerge API pubblica consumer per accesso diretto al database autovelox Waze.
   - necessario valutare partnership ufficiali e/o fonti dati alternative con licenza valida.

## Verifica ambiente Android su Windows
1. Android Studio trovato installato:
   - C:\Program Files\Android\Android Studio\bin\studio64.exe
2. Android SDK trovato installato:
   - C:\Users\rtd19\AppData\Local\Android\Sdk
3. Componenti SDK rilevati:
   - platform-tools, emulator, build-tools, platforms, cmdline-tools.
4. adb SDK ufficiale disponibile:
   - versione 37.0.0 rilevata da SDK locale.
5. emulator disponibile:
   - versione 36.6.11.0.
6. Stato AVD:
   - cartella AVD presente ma nessun emulatore configurato al momento.
7. Java disponibile:
   - Java 8 rilevato nel sistema.
8. Java runtime Android Studio disponibile:
   - OpenJDK 21 presente in C:\\Program Files\\Android\\Android Studio\\jbr.
9. Diagnosi cmdline-tools:
   - sdkmanager/avdmanager non funzionano da terminale per installazione cmdline-tools incompleta o corrotta.
   - classi richieste non trovate (ClassNotFoundException su SdkManagerCli/AvdManagerCli).
   - pacchetto commandlinetools-win.zip locale risulta non integro (errore zip END header not found).

## Stato finale sessione
- Documentazione iniziale creata.
- Piano step-by-step creato.
- Verifica preliminare toolchain completata.
- Blocco identificato: riparare/reinstallare cmdline-tools prima di automatizzare setup AVD da CLI.
- Prossimo blocco consigliato: ripristino cmdline-tools + creazione progetto Android + creazione AVD + prova build + test GPS mock.
- Progetto Android beta scaffoldato con flavor beta/prod, Compose, Room, DataStore e unit test.
- Motore rilevamento realtime implementato con alert strada principale e strade laterali.
- Build `app:assembleBetaDebug` completata con successo.
- Test `app:testBetaDebugUnitTest` completati con successo.
- Simulazione debug in-app completata con:
   - scenari multipli
   - progressione percorso in metri
   - pausa/ripresa
   - reset simulazione
   - cambio scenario
   - visualizzazione strada corrente e ramo imminente
- Cmdline-tools Android riparati in layout standard `cmdline-tools\\latest`.
- `sdkmanager` e `avdmanager` nuovamente eseguibili da terminale con Java 21 di Android Studio.
- System image `system-images;android-35;google_apis;x86_64` installata con successo.
- AVD creato con nome `OneVelox_API35`.
- Avvio emulator verificato con successo tramite `emulator.exe -avd OneVelox_API35`.
- `adb` rileva l'emulatore `emulator-5554` e lo vede correttamente come `device` dopo il boot.
- Icona applicazione creata con risorse launcher adaptive (`ic_launcher` e `ic_launcher_round`).
- Manifest aggiornato per usare l'icona propria dell'app.
- Build finale `app:assembleBetaDebug` completata con successo con icona inclusa.
- Artefatto pronto: `app\\build\\outputs\\apk\\beta\\debug\\app-beta-debug.apk`.
- Metadata build verificati:
   - applicationId `com.onevelox.app.beta`
   - versionName `0.1.0-beta`
- APK installato con successo sull'AVD tramite `adb install -r app-beta-debug.apk`.
- Simulazione GPS emulatore verificata con successo tramite `adb emu geo fix 9.1900 45.4642`.
- File `.md` ricontrollati e allineati allo stato corrente del progetto.
- Dashboard grafica ridisegnata in tema scuro con sfondo nero.
- Animazione principale aggiornata con:
   - auto al centro
   - carreggiata verticale
   - linea tratteggiata che scorre in funzione della velocita attuale
   - velocita mostrata sotto l'auto
- Pericoli principali visualizzati con icona del tipo di pericolo.
- Pericoli delle strade vicine trasformati in segnalazioni animate laterali.
- Overlay svolta aggiunto con freccia destra/sinistra, metri mancanti e tipo di pericolo.
- Burger menu impostazioni introdotto con slider e controlli simulazione debug.
- Logica acustica aggiunta per rilevare rallentamento consistente in prossimita di una svolta critica.
- Provider GPS reale implementato con `LocationManager` e monitoraggio rete per build non simulate.
- Richiesta permessi posizione runtime aggiunta all'avvio dell'app.
- Flavor `prod` instradata su GPS reale invece che simulatore.
- Flavor `phone` aggiunta come variante debug-signable con GPS reale.
- APK pronto per telefono verificato in output variante `prodDebug`:
   - `app\\build\\outputs\\apk\\prod\\debug\\app-prod-debug.apk`
   - applicationId `com.onevelox.app`
   - versionName `0.1.0`

## Aggiornamenti implementati (2026-08-14)
- Dashboard strada ridisegnata:
   - corsia principale singola
   - due linee tratteggiate laterali animate in base alla velocita
- Corsie vietate laterali aggiornate:
   - comparsa corsia tratteggiata animata su lato destro/sinistro
   - asfalto rosso per corsia non consentita
- Card POI sotto nome strada semplificata:
   - mostra tipo POI
   - mostra solo cartello limite classico (cerchio rosso, sfondo bianco, velocita nera)
- Tutor media migliorato:
   - media istantanea dall'ingresso tutor sempre visibile in dashboard
   - persistenza visualizzazione media fino a 30s dopo uscita dalla zona tutor
- Feed DB migliorato:
   - barra progresso durante aggiornamento
   - stato progressivo in dashboard e drawer
- Sync DB smart implementato:
   - controllo aggiornamenti remoto tramite timestamp Overpass
   - skip refresh completo quando non ci sono variazioni e cache locale adeguata
   - aggiornamento differenziale locale (upsert modificati/nuovi + delete rimossi)
- Sequenza avvio implementata:
   - usa POI locali all'apertura app
   - refresh completo automatico solo se POI < 10
   - controllo disponibilita aggiornamenti in background
   - stato DB colori: rosso se POI < 20, giallo se update disponibile, verde se aggiornato
- Verifica finale tecnica:
   - build `:app:assembleProdDebug` OK
   - install su `emulator-5554` OK
   - avvio app su emulatore OK
