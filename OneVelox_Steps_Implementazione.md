# OneVelox - Piano Implementazione Step by Step

## Fase 0 - Setup ambiente
1. Verificare Android Studio, SDK, emulator, adb e JDK.
2. Verificare integrita cmdline-tools (sdkmanager/avdmanager eseguibili da terminale).
3. Configurare variabili ambiente (ANDROID_SDK_ROOT, JAVA_HOME con Java 17+).
4. Creare almeno 1 AVD (Android 14 o 15) con Google APIs.
5. Verificare build di progetto demo e run su emulator.
6. Verificare simulazione GPS (Extended Controls + adb emu geo fix).

## Fase 1 - Product e requisiti
1. Definire requisiti funzionali in user stories.
2. Definire limiti tecnici MVP (solo Android, solo Italia, ecc).
3. Definire rischi legali su dati autovelox/ZTL e licenze.
4. Congelare scope MVP per evitare creep.

## Fase 2 - Architettura e progetto Android
1. Creare progetto Android Kotlin.
2. Setup moduli base (app, data, domain, ui).
3. Integrare DI (Hilt/Koin), Coroutines, Flow.
4. Setup Room per cache locale eventi.
5. Setup logging, crash reporting, analytics base.

## Fase 3 - Modello dati geospaziale
1. Definire schema evento:
   - id, tipo, lat, lon, limite, direzione, affidabilita, fonte, updated_at.
2. Definire indice geospaziale pragmatico:
   - bounding box + filtro distanza + filtro bearing.
3. Implementare repository eventi con query per:
   - corridoio principale
   - rami laterali
4. Implementare normalizzazione dati da fonti eterogenee.

## Fase 4 - Motore rilevamento realtime
1. Acquisire posizione/heading/velocita con frequenza adattiva.
2. Implementare map matching leggero strada corrente.
3. Implementare ricerca eventi in avanti (200-1000m).
4. Implementare ricerca eventi laterali (soglia default 100m).
5. Definire scoring priorita alert:
   - distanza
   - gravita tipo
   - superamento velocita
6. Implementare debounce/rate-limit notifiche.

## Fase 5 - UI/UX guida e animazioni
1. Dashboard con stati:
   - GPS, Internet, Database.
2. Animazione principale:
   - auto su strada dritta + velocita corrente.
3. Card alert principale:
   - tipo rischio, distanza, limite, velocita consigliata.
4. Indicatori laterali:
   - ramo destro/sinistro con tipo rischio e limite.
5. Gestione casi multipli strade laterali:
   - naming strada o indice ramo per distinguere chiaramente.
6. Alert audio quando velocita supera limite.

## Fase 6 - Data source e backend
1. Confermare fonti dati consentite (open data + partner).
2. Creare backend ingest/sync (job schedulati).
3. Esportare feed client ottimizzato.
4. Gestire versioning dati e invalidazione cache.

## Fase 7 - Test
1. Unit test dominio (filtro direzione, ranking, soglie).
2. Integration test repository + Room.
3. Test strumentali su emulator con GPS mock.
4. Test scenari urbani complessi (rami multipli, rotatorie).
5. Test batteria/performance con guida simulata lunga.

## Fase 8 - Hardening e release
1. Privacy policy e consensi runtime.
2. Ottimizzazioni batteria e gestione background.
3. QA finale e beta chiusa.
4. Raccolta feedback e tuning soglie alert.
5. Preparazione release store.

## Checklist tecnica minima prima di sviluppo feature
1. AVD creato e avviabile.
2. Simulazione GPS confermata.
3. Pipeline CI per build/debug attiva.
4. Logging diagnostico attivo per rilevamento.
5. Strategia dati legale confermata per MVP.

## Delta implementazione recente (2026-08-14)
1. Dashboard strada migrata a corsia singola con tratteggi laterali animati.
2. Gestione corsie vietate resa laterale con asfalto rosso e tratteggio animato.
3. Alert POI principale semplificato a tipo POI + cartello limite.
4. Tutor media resa persistente fino a 30 secondi dopo uscita dal tratto.
5. Refresh DB con progress bar e stato esplicito durante download/sync.
6. Controllo aggiornamento remoto pre-refresh via timestamp Overpass.
7. Sync locale differenziale (solo record cambiati + rimozioni obsolete).
8. Avvio app aggiornato:
   - cache locale immediata
   - refresh forzato solo con POI < 10
   - check update in background
   - stato DB semaforico rosso/giallo/verde in dashboard.
