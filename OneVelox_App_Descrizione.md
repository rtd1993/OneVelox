# OneVelox - Descrizione Prodotto

## Visione
OneVelox e una app Android di assistenza alla guida che avvisa in anticipo su autovelox, T-Red, ZTL e altri eventi sensibili per evitare multe.

Non e un navigatore turn-by-turn classico: analizza posizione, direzione e contesto stradale in tempo reale, e segnala i rischi davanti al veicolo senza obbligare l'utente a impostare un percorso.

## Obiettivo Utente
- Ricevere alert utili e tempestivi mentre guida.
- Sapere subito la velocita consigliata per restare entro il limite.
- Vedere chiaramente se il rischio e sulla strada principale o su strade laterali utili a una possibile svolta.

## Funzionalita Core

### 1) Rilevamento pericoli su strada principale
- Input dinamico:
  - GPS corrente (lat/lon)
  - heading/direzione di marcia
  - velocita attuale
- Ricerca pericoli in avanti su strada corrente:
  - range configurabile utente da 200m a 1000m
  - default consigliato: 400m
- Tipi pericolo minimi:
  - Autovelox fisso
  - T-Red
  - ZTL
  - Altri hazard stradali (estendibili)

### 2) Opzione strade limitrofe
- Finestra laterale configurabile (esempio iniziale: primi 100m delle strade in cui potrei svoltare).
- Se presente pericolo su strada laterale:
  - evidenzia lato (destra/sinistra)
  - mostra tipo pericolo
  - mostra limite consentito
- Deve restare visibile fino a superamento punto rischio o uscita dalla finestra utile.

### 3) Logica velocita consigliata
- Per ogni rischio rilevato con limite noto, calcola:
  - velocita_target = limite - margine_sicurezza
- Se velocita_attuale > limite:
  - alert audio + visivo prioritario
- Se velocita_attuale <= limite:
  - stato rassicurante non invasivo

## UI Principale (Home guida)
- Indicatori in alto:
  - stato GPS
  - stato rete internet
  - stato database
- Area centrale animata:
  - auto in movimento su strada dritta a corsia singola
  - due tratteggi laterali animati in base alla velocita
  - velocita attuale ben visibile
- Alert strada principale:
  - etichetta tipo POI
  - cartello limite stradale classico
  - distanza al pericolo
- Alert strade laterali:
  - mini-ramo strada destra/sinistra
  - etichetta tipo pericolo + limite
  - identificazione chiara della strada coinvolta (es: nome strada o indice ramo)

## Strategia aggiornamento POI (runtime)
- Avvio app:
  - usa immediatamente i POI locali salvati
  - se POI locali < 10 forza un refresh completo
- Check aggiornamenti in background:
  - verifica disponibilita update da Overpass senza download completo
  - se update disponibile evidenzia stato DB in giallo
- Stato DB in dashboard:
  - rosso se POI locali < 20
  - giallo se update disponibile
  - verde se dataset locale aggiornato
- Refresh DB:
  - mostra progress bar per non dare percezione di blocco
  - applica update differenziale locale (solo modifiche) per ridurre lavoro su dataset >10K POI

## Architettura Tecnica Proposta
- Android nativo con Kotlin.
- Pattern consigliato: Clean Architecture + MVVM.
- Componenti:
  - Data layer: provider eventi/pericoli, cache locale, sync.
  - Domain layer: matching geospaziale, filtro direzione, ranking alert.
  - Presentation layer: dashboard realtime + animazioni.
- Database locale:
  - Room con tabelle eventi geolocalizzati e metadati limite.
- Geospatial:
  - map matching leggero e filtro bearing (strada davanti vs dietro)
  - supporto a rami laterali entro distanza soglia.

## Motore Alert (alto livello)
1. Leggi posizione/heading/velocita.
2. Costruisci corridoio avanti su strada principale (distanza utente).
3. Cerca eventi nel corridoio principale.
4. Costruisci corridoi laterali (entro soglia laterale).
5. Valuta priorita:
   - imminenza distanza
   - severita evento
   - superamento limite velocita
6. Aggiorna UI + audio con debounce anti-spam.

## Verifica API Waze (stato attuale)

### Evidenze trovate
- Waze Developers espone:
  - Deep Links
  - Transport SDK (partner)
  - Data Feeds (partner feed per closures/incidents)
- Waze for Cities e orientato a enti pubblici e operatori traffico.
- Transport SDK richiede partnership e non e descritto come API pubblica libera per estrarre feed autovelox raw lato server.

### Conclusione pratica
- Non risulta una API pubblica ufficiale e aperta per terze parti consumer che permetta di interrogare in modo diretto il database Waze degli autovelox.
- Integrazioni disponibili sono principalmente:
  - invio dati a Waze come partner (closures/incidents)
  - integrazione con app Waze (deep links / SDK partner)
- Per OneVelox serve quindi una strategia dati alternativa o partnership formale con programmi Waze compatibili.

## Strategia Dati Consigliata (MVP)
- Fonte primaria: dataset legalmente utilizzabili con licenza esplicita (open data locali/regionali/nazionali).
- Fonte secondaria: partnership ufficiali dove disponibili.
- Pipeline:
  - ingest periodico
  - normalizzazione schema unico
  - validazione qualita e stale-data detection
  - distribuzione aggiornata al client

## Sicurezza, legale e compliance
- Evitare uso di sorgenti non autorizzate o scraping contro ToS.
- Conservare prova licenze dataset usati.
- Privacy by design:
  - minimizzare retention posizione
  - trasparenza su uso dati
  - consenso informato per localizzazione in background

## KPI di successo MVP
- Precisione alert utili su strada principale.
- Riduzione falsi positivi su strade non pertinenti.
- Tempo medio da rilevamento a notifica < 1s lato client.
- Stabilita app in guida prolungata.

## Rischi principali
- Qualita dati pericoli non uniforme.
- Difficolta map matching in zone urbane dense.
- Consumo batteria con localizzazione continua.

## Mitigazioni
- Adaptive polling GPS basato su velocita.
- Caching e prefetch geospaziale.
- Regole anti-rumore alert e priorita eventi.
