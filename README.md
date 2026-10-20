# DealCheck

Android-App, die Angebote für defekte Geräte auf eBay oder Kleinanzeigen bewertet: Du schickst ein Foto oder einen Screenshot und bekommst eine Empfehlung, ob sich **Kaufen, Reparieren und Weiterverkaufen** lohnt.

- Bilder per Kamera, aus der Galerie oder direkt über **„Teilen“ → DealCheck** aus der eBay- oder Kleinanzeigen-App (mehrere Bilder pro Analyse möglich)
- KI-Analyse über [OpenRouter](https://openrouter.ai): Gerät, Defekt, Reparaturkosten, Verkaufspreis, Gewinnspanne, Risiken und Betrugs-Warnsignale
- Klare Empfehlung: **KAUFEN** (grün), **VERHANDELN** (gelb) oder **NICHT KAUFEN** (rot), dazu ein maximaler Kaufpreis
- Ein Tipp öffnet die verkauften Angebote auf eBay oder die Suche auf Kleinanzeigen zum Preisvergleich
- Eigene Gewinnrechnung mit Gewinn und Marge, live berechnet
- Verlauf aller Analysen, nur lokal auf dem Gerät gespeichert

> Die Ergebnisse sind KI-Schätzungen ohne Gewähr. Prüfe Preise vor dem Kauf immer selbst.

---

## APK herunterladen und installieren

### 1. APK herunterladen

**Variante A: Release (empfohlen, sobald vorhanden)**
1. Öffne auf GitHub die Seite **Releases** (rechts auf der Startseite des Repos oder unter `…/eBay-Resell/releases`).
2. Lade beim neuesten Release die Datei `DealCheck-vX.Y.Z.apk` herunter.

**Variante B: Aktueller Build aus GitHub Actions**
1. Öffne im Repo den Tab **Actions** und dort den Workflow **„Build APK“**.
2. Klicke auf den obersten Lauf mit grünem Haken.
3. Lade unten unter **Artifacts** die Datei **`DealCheck-debug-apk`** herunter. Das klappt nur, wenn du bei GitHub angemeldet bist.
4. Du bekommst eine ZIP-Datei. Entpacke sie, darin liegt die `.apk`.

Tipp: Am einfachsten lädst du die Datei direkt auf dem Handy im Browser herunter. Alternativ lädst du sie am PC herunter und kopierst sie per USB oder Cloud aufs Handy.

### 2. Installation erlauben („Unbekannte Quellen“)

Android installiert Apps außerhalb des Play Stores erst nach deiner Erlaubnis:

1. Tippe auf die heruntergeladene `.apk`, z. B. in der Download-Benachrichtigung oder in der App „Dateien“.
2. Android meldet: *„Aus Sicherheitsgründen ist die Installation unbekannter Apps aus dieser Quelle nicht zulässig.“* Tippe auf **Einstellungen**.
3. Aktiviere **„Dieser Quelle vertrauen“** bzw. **„Installation aus dieser Quelle zulassen“**.
4. Gehe zurück und tippe auf **Installieren**.

Je nach Hersteller heißt der Punkt etwas anders: *Einstellungen → Apps → Spezieller App-Zugriff → Unbekannte Apps installieren* → den Browser oder die Dateien-App auswählen → erlauben.

Falls Google Play Protect warnt („App nicht bekannt“), wähle **Trotzdem installieren**. Die Warnung kommt, weil die App nicht aus dem Play Store stammt.

### Updates

Eine neue Version installierst du einfach über die alte. Meldet Android *„App nicht installiert“* oder *„Paket steht in Konflikt“*, ist die neue APK mit einem anderen Schlüssel signiert. Dann musst du die alte Version erst deinstallieren. Dabei gehen Verlauf und Einstellungen verloren.

Damit das nicht passiert, kannst du einmalig einen festen Signier-Schlüssel hinterlegen. Das ist optional:

1. Am PC mit installiertem Java einen Schlüssel erzeugen:
   ```
   keytool -genkeypair -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
   ```
2. Die Datei in Base64 umwandeln: `base64 -w0 debug.keystore` (Linux) bzw. `base64 -i debug.keystore` (macOS).
3. Auf GitHub unter *Settings → Secrets and variables → Actions → New repository secret* ein Secret mit dem Namen `DEBUG_KEYSTORE_BASE64` anlegen und den Base64-Text einfügen.
4. Die Datei `debug.keystore` **nicht** ins Repo hochladen.

Ab dann sind alle Builds gleich signiert. Nach der Umstellung musst du ein einziges Mal deinstallieren.

---

## OpenRouter-API-Key besorgen und eintragen

DealCheck schickt die Bilder an ein KI-Modell bei OpenRouter. Dafür brauchst du einen eigenen API-Key und etwas Guthaben.

1. Registriere dich auf **[openrouter.ai](https://openrouter.ai)**.
2. Lade unter **[openrouter.ai/credits](https://openrouter.ai/credits)** Guthaben auf. Wenige Euro reichen für viele Analysen: Mit dem Standardmodell kostet eine Analyse meist deutlich unter 1 Cent, mit Websuche etwas mehr.
3. Erstelle unter **[openrouter.ai/keys](https://openrouter.ai/keys)** einen neuen Key (**Create Key**). Er beginnt mit `sk-or-v1-…`. Kopiere ihn, denn er wird nur einmal angezeigt.
   Tipp: Beim Erstellen kannst du ein Ausgabelimit („Credit limit“) für den Key setzen.
4. Öffne in DealCheck den Tab **Einstellungen**, füge den Key ins Feld **API-Key** ein und tippe auf **Speichern**.

Der Key wird nur auf deinem Handy gespeichert, in den privaten App-Daten und ohne Cloud-Backup. Die App sendet ihn ausschließlich an `openrouter.ai`. Er steht nirgends im Code oder in diesem Repo.

### Modell und Websuche

- **Modell:** Standard ist `google/gemini-2.5-flash`, ein günstiges Modell, das Bilder versteht. Du kannst jeden Modellnamen von [openrouter.ai/models](https://openrouter.ai/models) eintragen, solange das Modell Bilder als Eingabe unterstützt (Filter „Input Modalities: Image“), z. B. `openai/gpt-4o-mini` oder `anthropic/claude-sonnet-4.5`.
- **Websuche:** Ist sie an, recherchiert das Modell aktuelle Preise im Web. Das ist genauer, aber langsamer und kostet pro Anfrage etwas extra.

---

## Bedienung

1. Tab **Analyse**: Tippe auf **Foto aufnehmen** oder **Aus Galerie**, oder teile einen Screenshot aus einer anderen App mit DealCheck.
2. Trage optional Zusatzinfos ein, z. B. den Preis, falls er nicht im Bild steht.
3. Tippe auf **Angebot analysieren**. Das dauert meist 10–60 Sekunden.
4. Im Ergebnis siehst du die Empfehlung, die Gewinnspanne, den maximalen Kaufpreis, Risiken und die Begründung. Über die Buttons prüfst du verkaufte Angebote auf eBay oder suchst auf Kleinanzeigen.
5. In der **eigenen Gewinnrechnung** passt du Einkaufspreis, Versand, Teile und Verkaufspreis an. Gewinn und Marge werden sofort neu berechnet und gespeichert.
6. Alle Analysen findest du im Tab **Verlauf**.

### Fehlermeldungen

| Meldung | Ursache / Lösung |
|---|---|
| Keine Internetverbindung | WLAN oder mobile Daten prüfen |
| API-Key ungültig | Key in den Einstellungen prüfen oder neu erstellen |
| Guthaben ist leer | Auf openrouter.ai/credits aufladen |
| Zeitüberschreitung | Erneut versuchen, ggf. Websuche ausschalten |
| Modell nicht gefunden / Anfrage abgelehnt | Modellnamen prüfen; das Modell muss Bilder unterstützen |

---

## Für Entwickler

- Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 35
- Netzwerk: OkHttp; Datenbank: Room; Bilder: Coil
- Bilder werden vor dem Senden auf max. 1600 px (längste Seite) skaliert und als JPEG gespeichert
- Build lokal: `./gradlew assembleDebug` (benötigt Android SDK und JDK 17); Tests: `./gradlew testDebugUnitTest`
- CI (`.github/workflows/build.yml`): Bei jedem Push werden die Tests ausgeführt und eine Debug-APK als Artifact hochgeladen. Ein Tag `v*` (z. B. `git tag v1.0.0 && git push origin v1.0.0`) erstellt zusätzlich ein GitHub-Release mit der APK.
