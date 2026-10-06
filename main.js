const { app, BrowserWindow, ipcMain, shell } = require("electron");
const fs = require("fs"), path = require("path"), os = require("os");
const { Client } = require("minecraft-launcher-core");
const { Auth } = require("msmc");
const { autoUpdater } = require("electron-updater");

const ROOT = path.join(os.homedir(), ".nolimite");
const INST = path.join(ROOT, "instances");
const DB = path.join(ROOT, "instances.json");
const SETTINGS = path.join(ROOT, "settings.json");
const UA = { "User-Agent": "NoLimite/1.0" };

let win, account = null;
/** Laufende Minecraft-Prozesse: id -> {proc, started, log:[], exitCode} */
const RUNNING = new Map();
/** Spielzeit je Instanz in Millisekunden (aus allen Sitzungen). */
const PLAYTIME = new Map();
const MAX_LOG = 3000;

const modJar = () => {
  const j = app.isPackaged
    ? path.join(process.resourcesPath, "nolimite-mod.jar")
    : path.join(__dirname, "resources", "nolimite-mod.jar");
  return fs.existsSync(j) ? j : null;
};

const syncMod = (inst) => {
  const j = modJar();
  if (!j || inst.mc !== "1.21.4") return;
  const d = path.join(INST, inst.id, "mods");
  fs.mkdirSync(d, { recursive: true });
  fs.copyFileSync(j, path.join(d, "nolimite.jar"));
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
  win = new BrowserWindow({
    width: 1280, height: 800, minWidth: 1024, minHeight: 660, frame: true,
    backgroundColor: "#050505", autoHideMenuBar: true,
    webPreferences: { nodeIntegration: true, contextIsolation: false },
  });
  win.loadFile("index.html");
  // Kein zweites Fenster: die Min-/Schliessen-Knoepfe der Seite sind nur Deko,
  // das echte Fenstermanagement macht Electron.
  win.webContents.on("did-create-window", () => {});
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
  await installMods(mc, dir, mods, files);

  const list = read();
  const inst = {
    id, name, mc, loader, versionId: prof.id,
    created: Date.now(), lastPlayed: 0, playtime: 0,
    installed: mods || [],
    files,
  };
  list.unshift(inst);
  write(list);
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

/** Stellt eine gespeicherte Anmeldung beim Start wieder her. */
ipcMain.handle("hasLogin", async () => {
  if (account) return accountInfo();
  const saved = savedAccount();
  if (!saved || !saved.refresh) return null;
  try {
    const auth = new Auth("select_account");
    const xbox = await auth.refresh(saved.refresh);
    const mc = await xbox.getMinecraft();
    account = mc;
    saveAccount({
      refresh: xbox.save(),
      name: mc.profile.name,
      uuid: mc.profile.id,
      skin: skinUrl(),
    });
    return accountInfo();
  } catch (e) {
    log(`- gespeicherte Anmeldung nicht gültig: ${e.message}`);
    clearAccount();
    return null;
  }
});

ipcMain.handle("logout", () => {
  clearAccount();
  return true;
});

/** Name, Avatar und Skin des angemeldeten Accounts. */
ipcMain.handle("account", async () => {
  if (!account && savedAccount()) {
    try {
      const auth = new Auth("select_account");
      const xbox = await auth.refresh(savedAccount().refresh);
      account = await xbox.getMinecraft();
    } catch {
      clearAccount();
      return null;
    }
  }
  return account ? accountInfo() : null;
});

// ---------------------------------------------------------------- Starten

ipcMain.handle("launch", async (_, { id, ram }) => {
  if (!account) throw new Error("Bitte zuerst mit Microsoft anmelden.");
  if (RUNNING.has(id)) throw new Error("Diese Instanz läuft bereits.");
  const inst = read().find((i) => i.id === id);
  if (!inst) throw new Error("Instanz nicht gefunden.");
  syncMod(inst);

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
      memory: { max: (ram || 4) + "G", min: "1G" },
      overrides: { gameDirectory: path.join(INST, inst.id) },
    });
  } catch (e) {
    RUNNING.delete(id);
    win && win.webContents.send("progress", { id, pct: 0 });
    throw e;
  }

  const rec = { proc: child, started: Date.now(), log: ["=== NoLimite: Instanz gestartet ==="] };
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
    push(`=== NoLimite: Minecraft beendet (Code ${code}) ===`);
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
      });
    }
  };
  let active = [], disabled = [];
  try { active = fs.readdirSync(dir); } catch {}
  try { disabled = fs.readdirSync(off); } catch {}
  add(active, true);
  add(disabled, false);
  files.sort((a, b) => a.name.localeCompare(b.name));
  return { files, nolimite: files.some((f) => f.file === "nolimite.jar") };
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
    if (file === "nolimite.jar") throw new Error("Die NoLimite-Mod kann nicht deaktiviert werden.");
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
  writeSettings(s);
  return true;
});