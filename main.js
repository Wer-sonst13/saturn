const { app, BrowserWindow, ipcMain, shell } = require("electron");
const fs = require("fs"), path = require("path"), os = require("os");
const { Client } = require("minecraft-launcher-core");
const { Auth } = require("msmc");
const { autoUpdater } = require("electron-updater");

const ROOT = path.join(os.homedir(), ".saturn");
const OLD_ROOT = path.join(os.homedir(), ".nolimite");
const INST = path.join(ROOT, "instances");
const DB = path.join(ROOT, "instances.json");
const SETTINGS = path.join(ROOT, "settings.json");
const UA = { "User-Agent": "SaturnClient/1.0" };

/** Beim ersten Start nach dem Umbenennen die alten Daten übernehmen. */
function migrateOldRoot() {
  if (!fs.existsSync(OLD_ROOT) || fs.existsSync(ROOT)) return;
  try {
    // rename ist sofort und belegt keinen zweiten Platz. Klappt er nicht
    // (anderes Laufwerk, offene Dateien), wird kopiert.
    fs.renameSync(OLD_ROOT, ROOT);
    log("Datenordner .nolimite nach .saturn umbenannt");
  } catch {
    try {
      fs.cpSync(OLD_ROOT, ROOT, { recursive: true });
      log("Datenordner .nolimite nach .saturn kopiert");
    } catch (e) {
      log(`- Datenübernahme nicht möglich: ${e.message}`);
    }
  }
}

let win, account = null;
/** Laufende Minecraft-Prozesse: id -> {proc, started, log:[], exitCode} */
const RUNNING = new Map();
/** Spielzeit je Instanz in Millisekunden (aus allen Sitzungen). */
const PLAYTIME = new Map();
const MAX_LOG = 3000;

// ------------------------------------------------------------------- Mod

/**
 * Ordner, in dem die Mod-Jars liegen.
 *
 * Beim Entwickeln ist das resources\ neben dem Code, im installierten
 * Launcher der resources-Ordner neben der app.asar.
 */
const modDir = () =>
  app.isPackaged
    ? path.join(process.resourcesPath)
    : path.join(__dirname, "resources");

/**
 * Die Minecraft-Versionen, fuer die der Launcher eine Mod-Jar mitbringt.
 *
 * Gelesen aus den Dateinamen resources\saturn-<version>.jar - das stimmt
 * sowohl beim Entwickeln als auch im installierten Programm, weil dort
 * dieselben Dateien neben der app.asar liegen. mod\versions.json waere
 * nur beim Entwickeln vorhanden.
 */
const supportedMc = () => {
  const d = modDir();
  if (!fs.existsSync(d)) return [];
  return fs
    .readdirSync(d)
    .map((f) => /^saturn-(.+?)(?:-[\d.]+)?\.jar$/.exec(f))
    .filter(Boolean)
    .map((m) => m[1])
    // Nur echte Minecraft-Versionen. Sonst rutscht der alte Dateiname
    // "saturn-0.1.0.jar" mit in die Liste und die Oberflaeche behauptet,
    // das Menue gae es fuer Minecraft 0.1.0.
    .filter((v) => /^\d+\.\d+(\.\d+)?$/.test(v))
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
};

/**
 * Die zur Minecraft-Version passende Mod-Jar.
 *
 * Es gibt eine Jar je Minecraft-Version, weil Yarn zwischen den Versionen
 * umbenennt: eine Jar, die fuer 1.21.1 gebaut ist, findet auf 1.21.5 nichts
 * und umgekehrt. Fehlt die passende, gibt es null - die Instanz startet
 * dann ganz normal, nur eben ohne Menue.
 */
const modJar = (mc) => {
  const d = modDir();
  if (mc) {
    const j = path.join(d, `saturn-${mc}.jar`);
    if (fs.existsSync(j)) return j;
  }
  return null;
};

/** Legt die Mod in die Instanz, wenn es eine passende Jar gibt. */
const syncMod = (inst) => {
  const j = modJar(inst.mc);
  if (!j) return false;
  const d = path.join(INST, inst.id, "mods");
  fs.mkdirSync(d, { recursive: true });
  const ziel = path.join(d, "saturn.jar");
  // Nur kopieren, wenn sie sich geaendert hat - sonst schreibt Fabric die
  // ganze Datei bei jedem Start neu.
  if (!fs.existsSync(ziel) || fs.statSync(j).size !== fs.statSync(ziel).size
      || fs.statSync(j).mtimeMs > fs.statSync(ziel).mtimeMs) {
    fs.copyFileSync(j, ziel);
  }
  return true;
};

const jget = async (u) => (await fetch(u, { headers: UA })).json();

const read = () => (fs.existsSync(DB) ? JSON.parse(fs.readFileSync(DB, "utf8")) : []);
const write = (d) => {
  fs.mkdirSync(ROOT, { recursive: true });
  fs.writeFileSync(DB, JSON.stringify(d, null, 2));
};

const readSettings = () => (fs.existsSync(SETTINGS) ? JSON.parse(fs.readFileSync(SETTINGS, "utf8")) : {});
const writeSettings = (d) => {
  fs.mkdirSync(ROOT, { recursive: true });
  fs.writeFileSync(SETTINGS, JSON.stringify(d, null, 2));
};

/** Microsoft-Anmeldung merken, damit sie einen Neustart überlebt. */
const saveAccount = (data) => {
  const s = readSettings();
  s.account = data;
  writeSettings(s);
};
const savedAccount = () => readSettings().account || null;
const clearAccount = () => {
  const s = readSettings();
  delete s.account;
  writeSettings(s);
  account = null;
};

/** Die eigentliche Skin-PNG des angemeldeten Accounts. */
const skinUrl = () => {
  const p = account && account.profile;
  const skin = p && p.skins && p.skins.find((s) => s.state === "ACTIVE");
  return skin ? { url: skin.url, slim: skin.variant === "SLIM" } : null;
};

const log = (m) => win && win.webContents.send("log", m);
const perInstance = (id, line) => {
  const r = RUNNING.get(id);
  if (!r) return;
  r.log.push(line);
  if (r.log.length > MAX_LOG) r.log.splice(0, r.log.length - MAX_LOG);
  win && win.webContents.send("ilog", { id, line });
};

/** Ordnergrösse in Bytes (flach, mit Unterordnern). */
function dirSize(dir) {
  let total = 0;
  const walk = (d) => {
    let entries = [];
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else {
        try { total += fs.statSync(p).size; } catch {}
      }
    }
  };
  walk(dir);
  return total;
}

/** Ordnergrösse ohne harte Links zu zählen (Saves/Welt können gross sein). */
function dirSizeFast(dir) {
  let total = 0;
  const walk = (d) => {
    let entries = [];
    try { entries = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of entries) {
      const p = path.join(d, e.name);
      if (e.isDirectory()) walk(p);
      else {
        try {
          const st = fs.lstatSync(p);
          if (!st.isSymbolicLink()) total += st.size;
        } catch {}
      }
    }
  };
  walk(dir);
  return total;
}

app.whenReady().then(() => {
  migrateOldRoot();
  // Ohne Rahmen: die Knöpfe zum Minimieren, Maximieren und Schliessen zeichnet
  // die Oberfläche selbst (siehe .winKnöpfe in styles.css). Nur so sitzen sie
  // in der Leiste neben dem Anmelden statt als graues Windows-Fensterkreuz
  // oben an der Titelleiste.
  win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 1024, minHeight: 660,
    frame: false, titleBarStyle: "hidden",
    backgroundColor: "#050505", autoHideMenuBar: true,
    webPreferences: { nodeIntegration: true, contextIsolation: false },
  });

  // ------------------------------------------------------------ Fensterknöpfe
  const fensterAktion = (aktion) => {
    if (!win || win.isDestroyed()) return;
    switch (aktion) {
      case "min": win.minimize(); break;
      case "max":
        if (win.isMaximized()) win.unmaximize();
        else win.maximize();
        break;
      case "close": win.close(); break;
      // "toggle" wird vom Doppelklick auf die Kopfzeile benutzt
      case "toggle":
        if (win.isMaximized()) win.unmaximize();
        else win.maximize();
        break;
      default: break;
    }
    // Der Zustand "maximiert" ändert sich auch, wenn der Spieler das Fenster
    // per Doppelklick oder Aufgabe in einen anderen Zustand bringt - die Seite
    // bekommt es darum über ein Ereignis mitgeteilt.
  };

  ipcMain.handle("win", (_, aktion) => { fensterAktion(aktion); return true; });
  ipcMain.handle("winState", () =>
    win && !win.isDestroyed()
      ? { maximized: win.isMaximized(), fullScreen: win.isFullScreen() }
      : { maximized: false, fullScreen: false });

  // Ziehen am oberen Rand: die rahmenlose Seite kann das Fenster sonst nicht
  // bewegen. -webkit-app-region: drag steht dafür in styles.css.
  win.loadFile("index.html");
  // Versionsnummer fuer die Fusszeile (renderer.js liest window.saturnVersion).
  // Muss nach dem Laden gesetzt werden: erst dann existiert das Fenster-DOM.
  win.webContents.on("did-finish-load", () => {
    // Version fuer die Fusszeile und die Liste der Minecraft-Versionen,
    // fuer die es die Mod gibt. Beides erst nach dem Laden setzen: vorher
    // gibt es das Fenster-DOM noch nicht.
    win.webContents.executeJavaScript(
      `window.saturnVersion = ${JSON.stringify(app.getVersion())};` +
      `window.__saturnVersionen = ${JSON.stringify(supportedMc())};`
    ).catch(() => {});
  });
  // Kein zweites Fenster: die Min-/Schliessen-Knoepfe der Seite sind nur Deko,
  // das echte Fenstermanagement macht Electron.
  win.webContents.on("did-create-window", () => {});

  // Der Knopf zum Wechseln zwischen Vollbild und Fenster heisst je nach Zustand
  // anders. Damit die Seite das anzeigen kann, bekommt sie jedes Mal Bescheid,
  // wenn sich der Zustand aendert - auch wenn der Spieler das Fenster mit der
  // Windows-Taste oder ueber den Taskleisten-Vorschau verschiebt.
  const zustandMelden = (label) => {
    if (!win || win.isDestroyed()) return;
    const zustand = win.isMaximized() || win.isFullScreen();
    win.webContents.send("winState", {
      maximized: zustand,
      fullScreen: win.isFullScreen(),
      // "restore" = zurück in die normale Grösse, "maximize" = gross machen
      label: zustand ? "restore" : "maximize",
    });
  };
  win.on("maximize", () => zustandMelden("max"));
  win.on("unmaximize", () => zustandMelden("unmax"));
  win.on("enter-full-screen", () => zustandMelden("fs"));
  win.on("leave-full-screen", () => zustandMelden("leaffs"));
  win.webContents.on("did-finish-load", () => zustandMelden("start"));
  if (app.isPackaged) {
    autoUpdater.on("update-available", () => log("Update gefunden, wird geladen ..."));
    autoUpdater.on("update-downloaded", () => autoUpdater.quitAndInstall());
    autoUpdater.checkForUpdates().catch(() => {});
  }
});

app.on("window-all-closed", () => {
  for (const [, r] of RUNNING) { try { r.proc.kill(); } catch {} }
  if (process.platform !== "darwin") app.quit();
});

// ---------------------------------------------------------------- Instanzen

ipcMain.handle("versions", async () =>
  (await jget("https://meta.fabricmc.net/v2/versions/game"))
    .filter((v) => v.stable).map((v) => v.version).slice(0, 40));

ipcMain.handle("instances", () => {
  // Spielzeit- und Zeitstempel-Daten nachziehen
  const list = read();
  for (const i of list) {
    i.playtime = PLAYTIME.get(i.id) || 0;
    if (RUNNING.has(i.id)) i.running = true; else delete i.running;
    if (i.running) i.runningSince = RUNNING.get(i.id).started;
  }
  return list;
});

ipcMain.handle("create", async (_, { name, mc, mods }) => {
  const loader = (await jget(`https://meta.fabricmc.net/v2/versions/loader/${mc}`))[0].loader.version;
  const prof = await jget(`https://meta.fabricmc.net/v2/versions/loader/${mc}/${loader}/profile/json`);
  const vdir = path.join(ROOT, "versions", prof.id);
  fs.mkdirSync(vdir, { recursive: true });
  fs.writeFileSync(path.join(vdir, prof.id + ".json"), JSON.stringify(prof));

  const id = "nl-" + Date.now();
  const dir = path.join(INST, id, "mods");
  fs.mkdirSync(dir, { recursive: true });
  const files = [];

  // Erst die, die der Spieler angeklickt hat, dann die vorinstallierten.
  // Mitinstalliert wird alles in einem Rutsch, damit die Abhaengigkeiten
  // (Sodium braucht z. B. noch etwas) zusammen aufloesen und nicht doppelt
  // geladen werden.
  const gewaehlt = (mods || []).filter((m) => !vorinstalliertSlugs().includes(m));
  await installMods(mc, dir, gewaehlt.concat(vorinstalliertSlugs()), files);
  // installMods merkt sich Namen und Version aus Modrinth, aber nicht, dass
  // eine Mod vorinstalliert ist - das hier nachziehen.
  for (const slug of vorinstalliertSlugs()) alsVorinstalliertMerken(files, slug);

  const list = read();
  const inst = {
    id, name, mc, loader, versionId: prof.id,
    created: Date.now(), lastPlayed: 0, playtime: 0,
    // installiert merkt alle Modrinth-Projekte, die im Ordner liegen -
    // die angeklickten und die vorinstallierten. Sonst wuerden die
    // vorinstallierten beim naechsten Aufruf als "nicht installiert"
    // gelten und noch einmal geladen.
    installed: gewaehlt.concat(vorinstalliertSlugs()),
    files,
  };
  list.unshift(inst);
  write(list);

  // Die Saturn-Mod kommt als letztes: sie gehoert zum Programm und wird
  // bei jedem Start einsortiert (siehe syncMod), auch in Profile, die
  // nachtraeglich angelegt wurden.
  syncMod(inst);
  return list;
});

ipcMain.handle("delete", (_, id) => {
  if (RUNNING.has(id)) throw new Error("Instanz läuft gerade - bitte zuerst beenden.");
  fs.rmSync(path.join(INST, id), { recursive: true, force: true });
  PLAYTIME.delete(id);
  const l = read().filter((i) => i.id !== id);
  write(l);
  return l;
});

// -------------------------------------------------------------- Anmelden

/** Alles, was die Oberfläche über den Account wissen muss. */
function accountInfo() {
  const p = account.profile;
  return { name: p.name, uuid: p.id, skin: skinUrl() };
}

ipcMain.handle("login", async () => {
  const xbox = await new Auth("select_account").launch("electron");
  const mc = await xbox.getMinecraft();
  account = mc;
  // Refresh-Token merken, damit die Anmeldung einen Neustart überlebt
  try {
    saveAccount({
      refresh: xbox.save(),
      name: mc.profile.name,
      uuid: mc.profile.id,
      skin: skinUrl(),
    });
  } catch (e) {
    log(`- Anmeldung nicht gespeichert: ${e.message}`);
  }
  return accountInfo();
});

/**
 * Holt eine gespeicherte Anmeldung zurück an den Start.
 *
 * Wichtig: `auth.refresh()` gibt einen *neuen* Refresh-Token zurück und der
 * alte wird dabei ungültig. Deshalb muss das Ergebnis jedes Mal wieder
 * gespeichert werden - sonst ist die Anmeldung nach dem nächsten Start weg.
 * Deshalb laufen beide Aufrufer über diese eine Funktion.
 */
async function restoreAccount() {
  if (account) return accountInfo();
  const saved = savedAccount();
  if (!saved || !saved.refresh) return null;
  try {
    const auth = new Auth("select_account");
    const xbox = await auth.refresh(saved.refresh);
    account = await xbox.getMinecraft();
    saveAccount({
      refresh: xbox.save(),
      name: account.profile.name,
      uuid: account.profile.id,
      skin: skinUrl(),
    });
    return accountInfo();
  } catch (e) {
    log(`- gespeicherte Anmeldung nicht gültig: ${e.message}`);
    clearAccount();
    return null;
  }
}

/** Stellt eine gespeicherte Anmeldung beim Start wieder her. */
ipcMain.handle("hasLogin", () => restoreAccount());

ipcMain.handle("logout", () => {
  clearAccount();
  return true;
});

/** Name, Avatar und Skin des angemeldeten Accounts. */
ipcMain.handle("account", () => restoreAccount());

// ---------------------------------------------------------------- Starten

/**
 * RAM-Wert in die Form bringen, die die JVM erwartet.
 *
 * Kommt aus der Oberfläche "4", aus einem gespeicherten Wert aber auch mal
 * "4G" oder "4096M". Einfach die Einheit anzuhängen ergäbe "-Xmx4GG", und
 * Minecraft bricht dann mit einer Java-Fehlermeldung ab, die man dem Spieler
 * nicht zeigen kann. Deshalb wird alles auf Megabyte gerechnet.
 */
function ramToXmx(ram) {
  const s = String(ram ?? "").trim().toUpperCase();
  const m = s.match(/^(\d+(?:[.,]\d+)?)\s*([KMG]?)$/);
  if (!m) return "4096M";
  const n = parseFloat(m[1].replace(",", "."));
  if (!Number.isFinite(n) || n <= 0) return "4096M";
  const faktor = { K: 1 / 1024, M: 1, G: 1024, "": 1024 }[m[2]];
  const mb = Math.round(n * faktor);
  // 512 MB bis 64 GB - darunter startet Minecraft nicht, darueber waere der
  // Rechner blockiert
  return Math.min(Math.max(mb, 512), 65536) + "M";
}

ipcMain.handle("launch", async (_, { id, ram }) => {
  if (!account) throw new Error("Bitte zuerst mit Microsoft anmelden.");
  if (RUNNING.has(id)) throw new Error("Diese Instanz läuft bereits.");
  const inst = read().find((i) => i.id === id);
  if (!inst) throw new Error("Instanz nicht gefunden.");
  syncMod(inst);

  // Fehlt eine der vorinstallierten Mods - etwa weil sie abgeschaltet wurde
  // oder das Profil aus einer aelteren Version des Launchers stammt -, wird
  // sie vor dem Start nachgeholt. Sonst faellt die Fabric-API auf und der
  // Client laedt gar nicht erst.
  await fehlendeVorinstallierteNachladen(inst);

  const l = new Client();
  const push = (m) => perInstance(id, String(m));

  l.on("progress", (p) => {
    const pct = p.total ? Math.round((p.task / p.total) * 100) : 0;
    win && win.webContents.send("progress", { id, pct, task: p.task, total: p.total });
    if (p.total) push(`[${pct}%] ${p.task}/${p.total}`);
  });
  l.on("data", (d) => push("[out] " + d.toString().trimEnd()));
  l.on("debug", (d) => push("[log] " + d.toString().trimEnd()));
  l.on("close", () => log("Minecraft beendet."));

  let child;
  try {
    child = await l.launch({
      authorization: account.mclc(),
      root: ROOT,
      version: { number: inst.mc, type: "release", custom: inst.versionId },
      memory: { max: ramToXmx(ram), min: "1G" },
      overrides: { gameDirectory: path.join(INST, inst.id) },
    });
  } catch (e) {
    RUNNING.delete(id);
    win && win.webContents.send("progress", { id, pct: 0 });
    throw e;
  }

  const rec = { proc: child, started: Date.now(), log: ["=== Saturn Client: Instanz gestartet ==="] };
  RUNNING.set(id, rec);
  win && win.webContents.send("progress", { id, pct: 100 });

  // stdout/stderr mitlesen und in den Log schreiben
  const pipe = (stream, tag) => {
    if (!stream) return;
    let buf = "";
    stream.on("data", (d) => {
      buf += d.toString();
      const parts = buf.split(/\r?\n/);
      buf = parts.pop();
      for (const line of parts) if (line.trim()) push(`[${tag}] ${line}`);
    });
    stream.on("end", () => { if (buf.trim()) push(`[${tag}] ${buf.trim()}`); });
  };
  pipe(child.stdout, "out");
  pipe(child.stderr, "err");

  // Spielzeit ab hier zählen
  rec.playTimer = setInterval(() => {
    const instNow = read().find((i) => i.id === id);
    if (instNow) {
      const total = (PLAYTIME.get(id) || 0) + 1000;
      PLAYTIME.set(id, total);
      instNow.playtime = total;
      instNow.lastPlayed = Date.now();
      write(read());
    }
  }, 1000);

  child.on("close", (code) => {
    clearInterval(rec.playTimer);
    RUNNING.delete(id);
    push(`=== Saturn Client: Minecraft beendet (Code ${code}) ===`);
    const list = read();
    const it = list.find((i) => i.id === id);
    if (it) {
      it.lastPlayed = Date.now();
      it.playtime = PLAYTIME.get(id) || 0;
      delete it.running;
      delete it.runningSince;
      write(list);
    }
    win && win.webContents.send("done", { id, code });
    log("Minecraft beendet.");
  });

  return true;
});

ipcMain.handle("stop", (_, id) => {
  const r = RUNNING.get(id);
  if (!r) return false;
  try { r.proc.kill(); } catch {}
  return true;
});

ipcMain.handle("status", () => {
  return [...RUNNING.entries()].map(([id, r]) => ({
    id, started: r.started, lines: r.log.length,
  }));
});

ipcMain.handle("logs", (_, id) => {
  const r = RUNNING.get(id);
  return r ? r.log : [];
});

// ------------------------------------------------------ Vorinstallierte Mods

/**
 * Mods, die in jedem neuen Profil automatisch landen.
 *
 * Das sind allesamt Mods, ohne die der Client entweder nicht lauffaehig ist
 * (Fabric API fehlt -> der Client laedt gar nicht erst) oder bei denen man
 * sich staendig etwas einstellt (Fullbright, Nametags, Sodium).
 *
 * Wichtig: `version` ist bewusst weggelassen. Modrinth liefert dann die
 * neueste Fassung, die zur jeweiligen Minecraft-Version passt - sonst
 * muesste hier fuer jede Minecraft-Version eine eigene Nummer stehen, und
 * ein neues Spielrelease wuerde das hier sofort wieder veralten lassen.
 */
const VORINSTALLIERT = [
  { slug: "P7dR8mSH", name: "Fabric API" },        // Pflicht, sonst laeuft nichts
  { slug: "sodium", name: "Sodium" },               // groesster clientseitiger Gewinn
  { slug: "entityculling", name: "EntityCulling" },// Entitys hinter Bloecken auslassen
  { slug: "immediatelyfast", name: "ImmediatelyFast" }, // Entitys, Partikel, GUI
  { slug: "ferrite-core", name: "FerriteCore" },   // weniger Speicher
  { slug: "modernfix", name: "ModernFix" },        // Speicher, Ladezeiten
  { slug: "dynamic-fps", name: "Dynamic FPS" },    // Bildrate senken, wenn man nicht hinsieht
  { slug: "lithium", name: "Lithium" },            // Spiel-Logik, Weltberechnung
  { slug: "ebe", name: "Enhanced Block Entities" },// Block-Entities schneller zeichnen
  { slug: "sodium-extra", name: "Sodium Extra" },  // Addon, braucht Sodium
  { slug: "simple-voice-chat", name: "Simple Voice Chat" }, // Voicechat
  { slug: "third-person-nametags", name: "Nametags" },
  { slug: "fullbright", name: "Fullbright" },
];

/*
 * Zur Versionsfrage: bei keinem Eintrag steht eine Version. installMods
 * fragt Modrinth nach der neuesten Fassung, die zur Minecraft-Version der
 * Instanz passt. Eine feste Version einzutragen wuerde sofort veralten.
 *
 * Fehlt ein Mod fuer eine Minecraft-Version - aktuell ModernFix bei 1.21.5 -
 * wird es uebersprungen und geloggt. Die Instanz startet trotzdem.
 *
 * Lithium ist eine Server-Optimierung. Auf einem Client bringt es nichts -
 * es ist auf Wunsch enthalten, weil es nuetzlich ist, sobald der Spieler
 * selber einen Server betreibt.
 */

/** Die Kennungen der vorinstallierten Mods, wie installMods sie braucht. */
const vorinstalliertSlugs = () => VORINSTALLIERT.map((m) => m.slug);

/** Merkt eine Mod als vorinstalliert, damit die Liste das zeigen kann. */
function alsVorinstalliertMerken(files, slug) {
  if (!files) return;
  const e = files.find((f) => f.slug === slug);
  if (e) e.vorinstalliert = true;
}

/**
 * Holt die vorinstallierten Mods nach, die im Profil fehlen.
 *
 * Ein Profil kann sie verlieren, wenn der Spieler eine Mod abschaltet (sie
 * wandert dann nach mods-off) oder wenn es aus einer aelteren Launcher-
 * Version stammt. Beides ist unkritisch und wird hier in Ruhe nachgeholt -
 * ein Fehler bricht den Start nicht ab, sonst waere ein Profil nach einem
 * kurzen Netzausfall nicht mehr startbar.
 */
async function fehlendeVorinstallierteNachladen(inst) {
  const dir = path.join(INST, inst.id, "mods");
  if (!fs.existsSync(dir)) return;
  const dateien = new Set(fs.readdirSync(dir));
  const fehlen = VORINSTALLIERT.filter((m) => {
    const e = (inst.files || []).find((f) => f.slug === m.slug);
    // Kein Eintrag in files heisst "noch nie geladen". Ein Eintrag zaehlt
    // als vorhanden, wenn eine Datei mit dem Namen wirklich da liegt -
    // sonst waeren die Dateinamen aus einer anderen Modrinth-Fassung falsch.
    if (!e) return true;
    return !dateien.has(e.file);
  });
  if (!fehlen.length) return;
  log(`- ${fehlen.length} vorinstallierte Mod fehlt, wird nachgeladen: ` +
      fehlen.map((m) => m.name).join(", "));

  // Ohne diese Meldung sah der erste Start auf einem frischen Rechner aus
  // wie ein Haenger: es wurden mehrere Megabyte von Modrinth geholt,
  // aber der Ladebalken gehoerte zu Minecraft und kam erst danach.
  if (win) {
    win.webContents.send("progress", {
      id: inst.id,
      pct: 0,
      task: `Lade ${fehlen.length} Mod${fehlen.length > 1 ? "s" : ""} von Modrinth...`,
    });
  }
  const files = inst.files || (inst.files = []);
  await installMods(inst.mc, dir, fehlen.map((m) => m.slug), files);
  for (const m of fehlen) alsVorinstalliertMerken(files, m.slug);
  if (!inst.installed) inst.installed = [];
  for (const m of fehlen) {
    if (!inst.installed.includes(m.slug)) inst.installed.push(m.slug);
  }
  const alle = read();
  const pos = alle.findIndex((i) => i.id === inst.id);
  if (pos >= 0) {
    alle[pos] = { ...alle[pos], files, installed: inst.installed };
    write(alle);
  }
}

/**
 * Die Mod-Konfiguration eines Profils lesen.
 *
 * Die Mod legt ihre Einstellungen unter <profilordner>/config/saturn.json ab.
 * Daraus braucht der Launcher eine Angabe: ob das Logo vor dem Namen stehen
 * soll. Das ist im Spiel unter "Launcher Logo" schaltbar.
 */
const readSaturnConfig = (inst) => {
  if (!inst) return null;
  const p = path.join(INST, inst.id, "config", "saturn.json");
  if (!fs.existsSync(p)) return null;
  try {
    return JSON.parse(fs.readFileSync(p, "utf8"));
  } catch {
    return null;         // Datei halb geschrieben oder kaputt
  }
};

/**
 * Soll das Logo vor dem Namen stehen?
 *
 * Standard ist ja. Ausgeschaltet wird es ueber das Modul "launcherlogo" im
 * Spiel. Welches Profil gilt, entscheidet das gerade ausgewaehlte; gibt es
 * keines, das zuletzt gespielte - sonst merkt der Spieler die Einstellung
 * nicht an der Stelle, wo er sie gemacht hat.
 */
ipcMain.handle("launcherLogo", (_, id) => {
  const liste = read();
  const gewaehlt = (id && liste.find((i) => i.id === id)) || selOderLetzte(liste);
  const cfg = readSaturnConfig(gewaehlt);
  const eintrag = cfg && cfg.modules && cfg.modules.launcherlogo;
  if (!eintrag) return true;           // kein Eintrag = Standard = an
  return eintrag.on !== false;
});

/** Das im Profil-Detail gewaehlte, sonst das zuletzt gespielte, sonst das erste. */
function selOderLetzte(liste) {
  if (!liste.length) return null;
  return liste
    .slice()
    .sort((a, b) => (b.lastPlayed || b.created || 0) - (a.lastPlayed || a.created || 0))[0];
}

// --------------------------------------------------------- Mod-Verwaltung

/**
 * Lädt Mods (inkl. Abhängigkeiten) und merkt sich Name + Version,
 * damit die Liste schön lesbar bleibt statt Dateinamen zu zeigen.
 */
async function installMods(mc, dir, projects, files) {
  fs.mkdirSync(dir, { recursive: true });
  const done = new Set();
  const remember = (entry) => {
    if (!files) return;
    const i = files.findIndex((f) => f.file === entry.file);
    if (i >= 0) files[i] = entry;
    else files.push(entry);
  };
  const get = async (p) => {
    if (done.has(p)) return;
    done.add(p);
    try {
      const q = `game_versions=${encodeURIComponent(JSON.stringify([mc]))}&loaders=${encodeURIComponent('["fabric"]')}`;
      const vs = await jget(`https://api.modrinth.com/v2/project/${p}/version?${q}`);
      if (!vs.length) return log(`- ${p}: nicht verfügbar für ${mc}`);
      const v = vs[0];
      const f = v.files.find((x) => x.primary) || v.files[0];
      fs.writeFileSync(path.join(dir, f.filename),
        Buffer.from(await (await fetch(f.url, { headers: UA })).arrayBuffer()));
      log("+ " + f.filename);
      remember({ slug: p, file: f.filename, name: v.name || p, version: v.version_number || "" });
      win && win.webContents.send("installed", { project: p, file: f.filename });
      for (const d of v.dependencies) {
        if (d.dependency_type === "required" && d.project_id) await get(d.project_id);
      }
    } catch (e) {
      log(`- ${p}: ${e.message}`);
    }
  };
  for (const m of projects || []) await get(m);
}

/**
 * Mods eines Profils. Name und Version kommen aus den gemerkten Daten
 * (Modrinth), sonst wird der Dateiname aufgeteilt.
 */
ipcMain.handle("modList", (_, id) => {
  const dir = path.join(INST, id, "mods");
  const off = path.join(INST, id, "mods-off");
  const inst = read().find((i) => i.id === id);
  const meta = new Map((inst && inst.files || []).map((f) => [f.file, f]));

  const files = [];
  const add = (list, on) => {
    for (const f of list) {
      if (!f.endsWith(".jar")) continue;
      const m = meta.get(f);
      files.push({
        file: f,
        name: m ? m.name : baseName(f),
        version: m ? m.version : verOf(f),
        on,
        // damit die Liste "vorinstalliert" anzeigen kann
        vorinstalliert: !!(m && m.vorinstalliert),
      });
    }
  };
  let active = [], disabled = [];
  try { active = fs.readdirSync(dir); } catch {}
  try { disabled = fs.readdirSync(off); } catch {}
  add(active, true);
  add(disabled, false);
  files.sort((a, b) => a.name.localeCompare(b.name));
  return { files, saturn: files.some((f) => f.file === "saturn.jar") };
});

/**
 * Anzeigename aus einem Dateinamen (nur als Notlösung, wenn von Modrinth
 * nichts bekannt ist): "sodium-fabric-0.8.15+mc1.21.11.jar" -> "sodium"
 */
function baseName(f) {
  return f.replace(/\.jar$/i, "")
    // Loader- und Minecraft-Kennungen überall entfernen
    .replace(/[-_]minecraft[-_]\d[\d.x_]*/gi, "")
    .replace(/[-_]mc\d[\d.x_]*/gi, "")
    .replace(/[-_](fabric|forge|neoforge|quilt|common)(?=[-+_]|$)/gi, "")
    // ab der ersten Version ist Schluss
    .replace(/[-_]\d[\d.]*.*$/, "")
    .replace(/[_-]/g, " ")
    .replace(/\s+/g, " ")
    .trim() || f;
}
/**
 * Versions-Teil eines Dateinamens - nur als Notlösung.
 * Aus einem Dateinamen lässt sich die Version nicht zuverlässig raten
 * (das Raten lieferte bei "appleskin-fabric-mc1.21.11-3.0.8.jar" die
 * falsche Zahl). Deshalb kommt die Version normalerweise von Modrinth
 * (siehe installMods); diese Funktion greift nur bei alten Installationen
 * und gibt dann "" zurück, damit der Dateiname angezeigt wird.
 */
function verOf() {
  return "";
}

ipcMain.handle("toggleMod", (_, { id, file, on }) => {
  if (RUNNING.has(id)) throw new Error("Instanz läuft gerade.");
  const dir = path.join(INST, id, "mods");
  const off = path.join(INST, id, "mods-off");
  fs.mkdirSync(dir, { recursive: true });
  fs.mkdirSync(off, { recursive: true });
  if (on) {
    const src = path.join(off, file);
    if (!fs.existsSync(src)) throw new Error("Datei nicht gefunden: " + file);
    fs.renameSync(src, path.join(dir, file));
  } else {
    const src = path.join(dir, file);
    if (!fs.existsSync(src)) throw new Error("Datei nicht gefunden: " + file);
    if (file === "saturn.jar") throw new Error("Die Saturn-Mod kann nicht deaktiviert werden.");
    fs.renameSync(src, path.join(off, file));
  }
  return true;
});

ipcMain.handle("deleteMod", (_, { id, file }) => {
  if (RUNNING.has(id)) throw new Error("Instanz läuft gerade.");
  for (const d of ["mods", "mods-off"]) {
    const p = path.join(INST, id, d, file);
    if (fs.existsSync(p)) fs.rmSync(p, { force: true });
  }
  const list = read();
  const inst = list.find((i) => i.id === id);
  if (inst && inst.files) {
    inst.files = inst.files.filter((f) => f.file !== file);
    write(list);
  }
  return true;
});

ipcMain.handle("addmods", async (_, { id, projects }) => {
  const list = read();
  const inst = list.find((i) => i.id === id);
  await installMods(inst.mc, path.join(INST, id, "mods"), projects, inst.files || (inst.files = []));
  inst.installed = [...new Set([...(inst.installed || []), ...projects])];
  write(list);
  return inst.installed;
});

/**
 * Resourcepacks / Shader / Datenpakete herunterladen.
 * Modrinth nennt sie project_type: resourcepack | shader | datapack.
 */
ipcMain.handle("addContent", async (_, { id, projects, kind }) => {
  const list = read();
  const inst = list.find((i) => i.id === id);
  const folder = {
    resourcepack: "resourcepacks",
    shader: "shaderpacks",
    datapack: path.join("saves", "datapacks"),
  }[kind] || "resourcepacks";

  const dir = path.join(INST, id, folder);
  fs.mkdirSync(dir, { recursive: true });

  for (const p of projects) {
    try {
      const q = `game_versions=${encodeURIComponent(JSON.stringify([inst.mc]))}&loaders=${encodeURIComponent('["fabric"]')}`;
      const vs = await jget(`https://api.modrinth.com/v2/project/${p}/version?${q}`);
      // Datenpakete haben keine Loader-Filter
      let v = vs[0];
      if (!v) v = (await jget(`https://api.modrinth.com/v2/project/${p}/version`))[0];
      if (!v) { log(`- ${p}: keine passende Version`); continue; }
      const f = v.files.find((x) => x.primary) || v.files[0];
      fs.writeFileSync(path.join(dir, f.filename),
        Buffer.from(await (await fetch(f.url, { headers: UA })).arrayBuffer()));
      log("+ " + f.filename);
    } catch (e) {
      log(`- ${p}: ${e.message}`);
    }
  }
  return true;
});

// ------------------------------------------------------------------ Suche

ipcMain.handle("search", async (_, { q, mc, cat, sort, offset, type }) => {
  const ptype = type || "mod";
  const facets = [["project_type:" + ptype], ["versions:" + mc]];
  if (ptype === "mod" || ptype === "shader") facets.push(["categories:fabric"]);
  if (cat) facets.push(["categories:" + cat]);
  const url = `https://api.modrinth.com/v2/search?query=${encodeURIComponent(q || "")}`
    + `&facets=${encodeURIComponent(JSON.stringify(facets))}`
    + `&index=${sort || "relevance"}&limit=20&offset=${offset || 0}`;
  const r = await jget(url);
  return {
    total: r.total_hits,
    hits: r.hits.map((h) => ({
      slug: h.slug, title: h.title, desc: h.description, icon: h.icon_url,
      dl: h.downloads, author: h.author, cats: h.display_categories,
    })),
  };
});

// ------------------------------------------------------------------ Inhalte

/** Resourcepacks / Shader / Datenpakete einer Instanz als Liste. */
ipcMain.handle("packs", (_, { id, kind }) => {
  const dirs = {
    resourcepack: "resourcepacks",
    shader: "shaderpacks",
    datapack: path.join("saves", "datapacks"),
  };
  const dir = path.join(INST, id, dirs[kind] || "resourcepacks");
  const out = [];
  let files = [];
  try { files = fs.readdirSync(dir); } catch {}
  for (const f of files) {
    const p = path.join(dir, f);
    let size = 0, isDir = false;
    try {
      const st = fs.statSync(p);
      isDir = st.isDirectory();
      size = st.size;
    } catch {}
    out.push({ name: f, size, folder: isDir });
  }
  return out;
});

/** Welche Welt wurde zuletzt gespielt? */
ipcMain.handle("worlds", (_, id) => {
  const saves = path.join(INST, id, "saves");
  const out = [];
  let dirs = [];
  try { dirs = fs.readdirSync(saves, { withFileTypes: true }); } catch {}
  for (const d of dirs) {
    if (!d.isDirectory()) continue;
    const dir = path.join(saves, d.name);
    let levelName = d.name, last = 0, gameTime = 0, version = "";
    try {
      const lvl = JSON.parse(fs.readFileSync(path.join(dir, "level.dat")));
      levelName = lvl?.Data?.LevelName || d.name;
      last = lvl?.Data?.LastPlayed || 0;
      gameTime = lvl?.Data?.Time || 0;
      version = lvl?.Data?.Version?.Name || "";
      if (version?.id) version = `${version.id} (${version.name})`;
    } catch {}
    out.push({
      name: d.name, levelName, last, gameTime, version,
      size: dirSizeFast(dir),
      path: dir,
    });
  }
  out.sort((a, b) => b.last - a.last);
  return out;
});

ipcMain.handle("screenshots", (_, id) => {
  const dir = path.join(INST, id, "screenshots");
  const out = [];
  let files = [];
  try { files = fs.readdirSync(dir); } catch {}
  for (const f of files) {
    if (!/\.(png|jpg|jpeg)$/i.test(f)) continue;
    const p = path.join(dir, f);
    let size = 0, mtime = 0;
    try { size = fs.statSync(p).size; mtime = fs.statSync(p).mtimeMs; } catch {}
    out.push({ name: f, path: p, size, mtime });
  }
  out.sort((a, b) => b.mtime - a.mtime);
  return out;
});

/** Grösse einer Instanz in Bytes (inkl. saves). */
ipcMain.handle("size", (_, id) => dirSizeFast(path.join(INST, id)));

/** Weltordner im Explorer öffnen. */
ipcMain.handle("openWorld", (_, p) => shell.openPath(p));
ipcMain.handle("openScreenshots", (_, id) => shell.openPath(path.join(INST, id, "screenshots")));

ipcMain.handle("folder", (_, id) => shell.openPath(path.join(INST, id)));

ipcMain.handle("rename", (_, { id, name }) => {
  const list = read();
  const i = list.find((x) => x.id === id);
  if (!i) throw new Error("Instanz nicht gefunden.");
  i.name = String(name || "").trim() || i.name;
  write(list);
  return list;
});

ipcMain.handle("settings", () => readSettings());
ipcMain.handle("saveSettings", (_, s) => {
  // Das `account` gehoert dem Backend, nicht der Oberflaeche. Die Seite
  // laedt die Einstellungen einmal beim Start und schreibt sie später
  // komplett zurueck - ohne diese Ausnahme wuerde dabei eine inzwischen
  // gespeicherte Anmeldung wieder geloescht.
  const alt = readSettings();
  const merged = { ...s };
  if (alt.account) merged.account = alt.account;
  else delete merged.account;
  writeSettings(merged);
  return true;
});