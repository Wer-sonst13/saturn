# Saturn Client

Minecraft-Launcher mit einem eigenen Client-Ordner (Mod) für Fabric 1.21.4.
Enthalten: Instanzen mit Spielzeit, Live-Status und Logs, Mod-/Paket-Suche über
Modrinth, und im Spiel ein NoRisk-artiges Menü mit Modul-Karten, HUD-Editor
und einer voll einstellbaren Scoreboard-Seite.

## Bauen

### Variante A: Auf deinem PC (Windows)
1. Node.js installieren: https://nodejs.org
2. Java 21 (JDK) installieren: https://adoptium.net
3. `build.bat` doppelklicken
4. Im Ordner `dist` liegt `Saturn-Client-Setup-1.0.0.exe`

`build.bat` baut zuerst die Mod und legt sie in `resources\saturn-mod.jar`,
danach den Launcher.

### Variante B: Fertige .exe von GitHub bauen lassen
1. Neues GitHub-Repo erstellen und diesen Ordner hochladen (inkl. `.github`)
2. Tab "Actions" -> "Build Saturn Client" abwarten
3. Bei "Artifacts" `Saturn-Client-Setup` herunterladen, entpacken, installieren.

Entwicklung: `npm install` und `npm start`

## Auto-Update
1. In `package.json` bei `build.publish` `Wer-sonst13` durch deinen GitHub-Namen
   ersetzen (Repo muss öffentlich sein).
   **Wichtig:** Der Repo-Name in `build.publish` muss exakt dem GitHub-Repo
   entsprechen. Er steht absichtlich noch auf `nolimite` - das Programm selbst
   heißt Saturn Client, aber das Repo heißt noch anders. Benennst du das Repo
   um, musst du `build.publish.repo` hier gleichzeitig anpassen, sonst findet
   der Launcher kein Update.
2. Neue Version veröffentlichen: `version` in `package.json` erhöhen (z. B. 1.0.1),
   committen, dann `git tag v1.0.1` und `git push --tags`.
3. GitHub baut das Setup und legt es unter "Releases" ab. Jeder installierte
   Launcher findet es beim Start, lädt es und aktualisiert sich selbst.
Die erste Installation macht man einmal über die Setup-Datei aus dem Release.

## Der Launcher

| Seite | Was |
|---|---|
| **Spielen** | Avatar, großer Start-Knopf, Liste der Instanzen mit Status |
| **Profile** | Profil-Karten; Klick öffnet die Detailseite |
| **Inhalte** | Modpacks, Mods, Ressourcenpakete, Shader, Datenpakete von Modrinth |
| **Logs** | Ausgabe der gewählten Instanz, Filter auf Fehler, Kopieren |
| **Einstellungen** | RAM, Akzentfarbe, Theme, Schrift |

**Kopfzeile:** Die Pille zeigt jederzeit, wie viele Instanzen laufen. Ein Klick
öffnet die Liste; bei einer laufenden Instanz geht es direkt in deren Logs.

**Profil-Detailseite:** Avatar, Version, Loader, Zustand, *Zuletzt gespielt*,
*Spielzeit* (wird pro Sekunde mitgeschrieben) und *Speicher* (Ordnergröße).
Reiter: Mods, Ressourcenpakete, Shader, Datenpakete, Welten, Screenshots.

**Mods:** Jede Mod lässt sich per Schalter aus- und einschalten. Ausgeschaltete
Mods werden nach `mods-off/` verschoben, nicht gelöscht, und sind über den
Schalter wieder aktivierbar. Die Saturn-Mod selbst lässt sich nicht abschalten.

**Neues Profil:** Dialog mit Namensfeld, Minecraft-Version und einer Liste
empfohlener Mods, die angeklickt werden können. Die Saturn-Mod wird für 1.21.4
automatisch mitinstalliert.

## Die Mod (im Spiel)

Tasten (alle unter *Steuerung* änderbar):

| Taste | Funktion |
|---|---|
| Rechts-Shift | Saturn-Menü öffnen |
| F6 | HUD-Editor: Elemente ziehen, Mausrad = Größe |
| F7 | Chat Utils: Chat durchsuchen, Zeile anklicken zum Kopieren |
| F8 | Vanilla-HUD ein-/ausblenden |

Im Pause-Menü gibt es zusätzlich einen **Saturn**-Knopf.

### Menü

Karten-Raster mit Kategorien (RENDER, HUD, CHAT, MISC) und Suche.
Klick auf eine Karte schaltet das Modul an/aus, der Schalter rechts unten
öffnet die Einstellungen. Jede Einstellung (Schalter, Schieberegler, Auswahl,
Text, Farbe) wird aus der Modul-Definition erzeugt.

**Module** (alle einzeln schaltbar):
Fullbright, Helligkeit, Nofog, Color Saturation, Clear Background, Nametags,
FOV Changer, Old Animations, Weather Changer, Time Changer, Titles, Shiny Pots,
Item Highlighter, Freelook, FPS, Coordinates, Light Level, Armorstatus,
Musas Armor HUD, Potion Status, Item Counter, CPS, Combo Counter, Clock,
Speedometer, Keystrokes, Held Item, Biome & World, Server IP, Scoreboard,
Split Chat, Smooth Chat, Chat Utils, Timestamps, Theme, GUI Scale, Icon, Profiles.

### Scoreboard

Eigene Seite mit **ON / RESET**, einer Vorschau und den Abschnitten wie im
NoRisk-Menü:

- **DISPLAY:** HUD Scale, Background (Vanilla / Transparent / Blur / eigene
  Farbe), Corners + Eckengröße, Dynamic Padding, Width, Height
- **SETTINGS:** Show Numbers, Font Shadow, Hide Scoreboard

Position und Größe lassen sich im HUD-Editor mit der Maus verschieben
(rechts verankert, vertikal mittig).

### HUD-Editor (F6)

Zeigt jedes aktive HUD-Element als Rahmen über dem Spiel. Ziehen verschiebt,
Mausrad ändert die Größe. In der Leiste lassen sich alle Elemente ein-/
ausblenden oder die Positionen zurücksetzen.

### Profile

`Profiles` merkt sich einen eigenen Satz an-/ausgeschalteter Module
(`config/saturn.json`), um zwischen verschiedenen Setups zu wechseln.

## Dateien

```
main.js          Launcher-Backend (Instanzen, Start, Logs, Mod-Suche)
index.html       Oberfläche
styles.css       Gestaltung (Themes hell/dunkel, Pixel- und Standardschrift)
renderer.js      Seitenlogik
mod/             Fabric-Mod (Java 21, Gradle)
resources/       hier landet saturn-mod.jar
```

## Hinweise

- Die Mod läuft nur auf **Fabric 1.21.4**. Andere Versionen starten normal,
  ohne Saturn-Menü.
- Nicht implementiert: **Motion Blur**. Der Effekt bräuchte einen eigenen
  Shader; die Fabric-API 1.21.4 bietet keine Shader-Registrierung, daher wurde
  er entfernt, statt etwas zu liefern, das kaputtgeht.
- Vollbright steuert die vorhandene Helligkeits-Option und den Dunkelheitsfaktor
  der Lightmap. Es ist damit ein Helligkeitsregler, kein echtes Lichtlevel-15.
- Mods werden direkt von Modrinth geladen; ein CurserForge-Zugang ist nicht
  eingebunden. Erscheint eine Mod nicht, liegt das meist an der Version.