# NoLimite Mod (Fabric 1.21.4)

Die Mod hinter dem NoLimite-Launcher. Keine Abhängigkeiten ausser Fabric API.

## Tasten

| Taste | Funktion |
|---|---|
| Rechts-Shift | NoLimite-Menü |
| F6 | HUD-Editor |
| F7 | Chat Utils |
| F8 | Vanilla-HUD umschalten |

Alle unter *Steuerung* → *NoLimite* änderbar.

## Aufbau

| Datei | Inhalt |
|---|---|
| `Module.java` | ein Modul: an/aus, Position, Grösse, Einstellungen |
| `Modules.java` | alle Moduldefinitionen |
| `Setting.java` | typisierte Einstellung (Schalter, Zahl, Text, Auswahl, Farbe) |
| `ConfigStore.java` | `config/nolimite.json`, Mod-Profile |
| `Ui.java` | Themes und Zeichen-Bausteine |
| `Icons.java` | 9x9-Pixel-Icons als Bitmaps, kein Asset nötig |
| `NoLimiteScreen.java` | Basis der Fenster |
| `NoLimiteMenuScreen.java` | Modul-Raster mit Kategorien und Suche |
| `ModuleSettingsScreen.java` | automatisch erzeugte Einstellungsseite |
| `ScoreboardScreen.java` | Scoreboard-Seite (ON/RESET, DISPLAY, SETTINGS) |
| `ScoreboardRenderer.java` | eigener Scoreboard-Zeichner |
| `HudEditorScreen.java` | Elemente ziehen, Mausrad = Grösse |
| `ChatUtilsScreen.java` | Chatverlauf, Suche, Kopieren |
| `HudRenderer.java` | zeichnet alle HUD-Elemente |
| `NametagRenderer.java` | eigene Nametags |
| `Trackers.java` | CPS, Combo, Licht, Biome, Chatverlauf |
| `Effects.java` | Laufzeiteffekte und Zustand für die Mixins |
| `mixin/` | Scoreboard, Vollbild, Nebel, Kamera, Titel, Chat, Namen, FOV |

## Bauen

```bash
gradle build
```

Ergebnis: `build/libs/nolimite-<version>.jar`.
Wird sie in den `mods`-Ordner eines Fabric-1.21.4-Profils gelegt, erscheint
Rechts-Shift als neue Taste und im Pause-Menü der Knopf **NoLimite**.

Der Launcher nimmt diese Jar automatisch mit und kopiert sie bei jedem Start
in passende Profile.

## Hinweise

- **Motion Blur** fehlt bewusst: der Effekt braucht einen eigenen Shader, und
  die Fabric-API 1.21.4 bietet keine Shader-Registrierung.
- **Fullbright** regelt die Helligkeits-Option und den Dunkelheitsfaktor der
  Lightmap - also ein Helligkeitsregler, kein echtes Lichtlevel 15.
- Alle Mixins laufen mit `require = 0`: passt eine Zielmethode in einer
  anderen Minecraft-Version nicht, bleibt das Spiel spielbar und das Modul
  wird wirkungslos, statt abzustürzen.
- Die Refmap im fertigen Jar listet jedes Ziel auf, das der Mixin-Annotation
  Processor auflösen konnte.