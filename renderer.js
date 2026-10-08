const { ipcRenderer: ipc } = require("electron");
const { SkinView } = require("./skin.js");

const $ = (id) => document.getElementById(id);
const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g,
  (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

let list = [], sel = null, versions = [], settings = {};
let hist = ["play"], hi = 0;
let contentType = "mod", offset = 0, total = 0, picked = new Set();
let modFiles = [], worlds = [], shots = [], logTarget = null, logLines = [];
let timer = null;
let skinView = null, skinAcc = null, lastFrame = 0;

// ------------------------------------------------------- Unterstuetzte Versionen
// Welche Minecraft-Versionen es mit Menue gibt, steht in mod/versions.json.
// Wird sie im Installer nicht mitgeliefert, bleibt die Liste leer und die
// Oberflaeche sagt das auch - dann wird nichts versprochen, was nicht da ist.
function saturnVersionen() {
  if (typeof window.__saturnVersionen === "object" && window.__saturnVersionen) {
    return window.__saturnVersionen;
  }
  return [];
}
// Erste unterstuetzte Version, die auch in der Liste der Fabric-Versionen
// steht - die Voreinstellung des neuen Profils.
let SATURN_VORBEUGUNG = "";
for (const v of saturnVersionen()) {
  if (versions.includes(v)) { SATURN_VORBEUGUNG = v; break; }
}
if (!SATURN_VORBEUGUNG) SATURN_VORBEUGUNG = saturnVersionen()[0] || versions[0] || "";

// ------------------------------------------------------------------- Skin
// -------------------------------------------------------------- Hintergrund
async function setupBackground() {
  const bg = $("bg");
  if (!bg) return;
  // saturn.webp liegt mit im Projekt; .jpg/.png wuerden es ueberschreiben,
  // falls du spaeter ein eigenes Bild dort ablegst.
  for (const name of ["saturn.jpg", "saturn.png", "saturn.webp"]) {
    const probe = new Image();
    const ok = await new Promise((r) => {
      probe.onload = () => r(true);
      probe.onerror = () => r(false);
      probe.src = "assets/" + name;
    });
    if (ok) {
      bg.style.backgroundImage = 'url("assets/' + name + '")';
      return;
    }
  }
}

async function setupSkin() {
  const canvas = $("skin");
  if (!canvas) return;
  const stage = $("skinStage");

  const measure = () => resizeSkin();

  skinView = new SkinView(canvas);

  // Ziehen zum Drehen. Waehrend des Ziehens wird jedes Mal neu gezeichnet,
  // damit das Modell der Maus folgt und nicht erst beim Loslassen springt.
  stage.addEventListener("pointerdown", (e) => {
    e.preventDefault();
    try { stage.setPointerCapture(e.pointerId); } catch {}
    stage.classList.add("touched");
    skinView.startDrag(e);
  });
  stage.addEventListener("pointermove", (e) => {
    if (!skinView.dragging) return;
    e.preventDefault();
    skinView.drag(e);
    skinView.render();
  });
  const stop = (e) => {
    skinView.endDrag();
    try { stage.releasePointerCapture(e.pointerId); } catch {}
  };
  stage.addEventListener("pointerup", stop);
  stage.addEventListener("pointercancel", stop);
  stage.addEventListener("lostpointercapture", stop);

  // Klick ohne Ziehen -> zurueck auf die Vorderansicht
  stage.addEventListener("click", () => {
    if (skinView.dragDistance < 6) {
      skinView.yaw = 0;
      skinView.render();
    }
  });

  // Erst messen, wenn das Layout steht - sonst passt das Canvas nicht
  // und das Modell wird abgeschnitten.
  measure();
  await new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r)));
  measure();

  if (typeof ResizeObserver !== "undefined") {
    new ResizeObserver(() => {
      measure();
      if (skinView) skinView.render();
    }).observe(stage);
  }
  window.addEventListener("resize", () => { measure(); if (skinView) skinView.render(); });

  await loadSkin();
  requestAnimationFrame(loop);
}

/** Das Logo, das vor dem Namen steht. */
const SATURN_LOGO = "assets/saturn-logo.png";

/**
 * Blendet das Logo vor dem Namen ein oder aus.
 *
 * Abgeschaltet wird es im Spiel unter MISC -> "Launcher Logo". Der Launcher
 * liest das aus der Mod-Konfiguration des passenden Profils, damit die
 * Einstellung dort gilt, wo sie gemacht wurde.
 */
async function setzeLogo() {
  const bild = $("hudFace");
  if (!bild) return;
  let an = true;
  try { an = await ipc.invoke("launcherLogo", sel); } catch {}
  bild.style.display = an ? "" : "none";
}

async function loadSkin() {
  let acc = null;
  try { acc = await ipc.invoke("account"); } catch {}
  skinAcc = acc;
  // Vor dem Namen steht das Saturn-Logo, nicht ein Minecraft-Kopf.
  $("hudFace").src = SATURN_LOGO;
  setzeLogo();
  if (!acc) {
    $("nick").textContent = "Nicht angemeldet";
    skinView.img = null;
    skinView.render();
    return;
  }
  $("nick").textContent = acc.name;
  const skin = acc.skin || {};
  const ok = await skinView.load(skin.url, skin.slim, acc.uuid);
  if (!ok) {
    toast("Skin konnte nicht geladen werden", "err");
  }
  skinView.render();
}

function toggleSpin() {
  const t = $("spinTog");
  t.classList.toggle("on");
  if (skinView) skinView.autoSpin = t.classList.contains("on");
}

function loop(t) {
  const dt = lastFrame ? Math.min(0.1, (t - lastFrame) / 1000) : 0;
  lastFrame = t;
  if (skinView && $("play").classList.contains("on")) {
    const before = skinView.yaw;
    skinView.tick(dt);
    // Waehrend des Drehens zeichnet der Maus-Handler, hier nur die Animation
    if (skinView.autoSpin && !skinView.dragging && skinView.yaw !== before) {
      skinView.render();
    }
  }
  requestAnimationFrame(loop);
}

// ---------------------------------------------------------------- Vorschläge
const SUGGESTED = [
  ["lithium", "Lithium", "optimization"],
  ["modmenu", "Mod Menu", "utility"],
  ["iris", "Iris Shaders", "decoration"],
  ["immediatelyfast", "ImmediatelyFast", "optimization"],
  ["entityculling", "Entity Culling", "optimization"],
  ["ferrite-core", "FerriteCore", "optimization"],
  ["cloth-config", "Cloth Config", "library"],
  ["zoomify", "Zoomify", "utility"],
  ["freelook", "Freelook", "utility"],
  ["mouse-tweaks", "Mouse Tweaks", "utility"],
  ["appleskin", "AppleSkin", "utility"],
  ["betterf3", "BetterF3", "utility"],
  ["sodium-extra", "Sodium Extra", "optimization"],
];

const CATS = {
  "": "Alle", optimization: "Optimierung", utility: "Nützlich", library: "Bibliothek",
  decoration: "Dekoration", technology: "Technik", adventure: "Abenteuer", magic: "Magie",
  storage: "Lagerung", equipment: "Ausrüstung", "game-mechanics": "Mechaniken",
  mobs: "Mobs", worldgen: "Weltgenerierung", social: "Sozial", minigame: "Minigame",
  shaders: "Shader", textures: "Texturen", fonts: "Fonts", skins: "Skins",
  "resource-pack": "Ressourcenpaket", "shaders": "Shader", "data_pack": "Datenpaket",
  fabric: "Fabric", quilt: "Quilt", forge: "Forge", neoforge: "NeoForge",
  client: "Client", server: "Server",
};
const CAT_FOR = { mod: ["", "optimization", "utility", "library", "decoration", "technology", "adventure", "magic", "storage", "equipment", "game-mechanics", "mobs", "worldgen", "social", "minigame"],
                  resourcepack: ["", "textures", "shaders", "fonts", "skins"],
                  shader: ["", "shaders"], datapack: ["", "game-mechanics", "worldgen"] };

// ---------------------------------------------------------------- Helfer
function toast(t, kind) {
  const d = document.createElement("div");
  d.className = "toast" + (kind ? " " + kind : "");
  d.textContent = t;
  $("toasts").appendChild(d);
  setTimeout(() => d.remove(), 4200);
}
const fmtNum = (n) => n >= 1e6 ? (n / 1e6).toFixed(1) + "M" : n >= 1e3 ? Math.round(n / 1e3) + "k" : String(n);
function fmtDur(ms) {
  if (!ms || ms <= 0) return "0 s";
  const s = Math.floor(ms / 1000), h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  if (h) return `${h} h ${m} min`;
  if (m) return `${m} min ${s % 60} s`;
  return `${s} s`;
}
function fmtAgo(ts) {
  if (!ts) return "nie";
  const d = Date.now() - ts;
  if (d < 60e3) return "gerade eben";
  if (d < 3600e3) return Math.floor(d / 60e3) + " min her";
  if (d < 86400e3) return Math.floor(d / 3600e3) + " h her";
  return Math.floor(d / 86400e3) + " T her";
}
const fmtBytes = (b) => {
  if (!b) return "0 B";
  const u = ["B", "KB", "MB", "GB", "TB"];
  let i = 0, v = b;
  while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
  return v.toFixed(v >= 100 || i === 0 ? 0 : 1) + " " + u[i];
};
const cur = () => list.find((i) => i.id === sel);

// ---------------------------------------------------------------- Navigation
function nav(p) {
  hist = hist.slice(0, hi + 1);
  hist.push(p); hi++;
  show(p);
}
function show(p) {
  document.querySelectorAll(".page").forEach((e) => e.classList.toggle("on", e.id === p));
  document.querySelectorAll(".nb").forEach((e) => e.classList.toggle("on", e.dataset.p === p));
  $("instDrop").classList.remove("on");
  // Die Skin-Buehne ist versteckt, solange eine andere Seite offen ist -
  // Canvas dann neu messen, sonst bleibt das Modell abgeschnitten.
  if (p === "play" && skinView) { resizeSkin(); skinView.render(); }
  if (p === "prof") { renderProfiles(); if (sel) openDetail(); }
  if (p === "mods") loadContent(true);
  if (p === "log") selectLog();
}

function resizeSkin() {
  if (!skinView) return;
  const canvas = $("skin"), stage = $("skinStage");
  const dpr = window.devicePixelRatio || 1;
  const r = stage.getBoundingClientRect();
  if (r.width < 10 || r.height < 10) return;
  const w = Math.round(r.width * dpr), h = Math.round(r.height * dpr);
  if (canvas.width !== w || canvas.height !== h) {
    canvas.width = w;
    canvas.height = h;
  }
  canvas.style.width = r.width + "px";
  canvas.style.height = r.height + "px";
}
// Die Knöpfe oben links sind nur Deko - Electron verwaltet den Rahmen selbst.
function win() {}

// ---------------------------------------------------------------- Instanz-Dropdown
function toggleInst() { $("instDrop").classList.toggle("on"); }
async function refreshStatus() {
  let running = [];
  try { running = await ipc.invoke("status"); } catch {}
  const live = new Set(running.map((r) => r.id));
  for (const i of list) { if (live.has(i.id)) i.running = true; else delete i.running; }

  const on = list.filter((i) => i.running).length;
  $("instCount").textContent = on === 1 ? "1 Instanz läuft" : `${on} Instanzen laufen`;
  $("instBtn").classList.toggle("running", on > 0);

  $("instDrop").innerHTML = list.length ? list.map((i) => `
    <div class="dropRow ${i.running ? "live" : ""}" onclick="dropPick('${i.id}')">
      <span class="st"></span>
      <div class="nm"><b>${esc(i.name)}</b><small>${esc(i.mc)} · ${i.running ? "läuft · Logs anzeigen" : fmtDur(i.playtime)}</small></div>
      <span class="go">›</span>
    </div>`).join("") : '<div class="info" style="padding:10px">Noch kein Profil angelegt.</div>';
}
function dropPick(id) {
  sel = id;
  $("instDrop").classList.remove("on");
  // Das Logo richtet sich nach dem gewaehlten Profil - beim Wechsel also
  // neu holen, sonst stuende noch die Entscheidung des anderen.
  setzeLogo();
  if (list.find((i) => i.id === id && i.running)) {
    logTarget = id;
    nav("log");
  } else {
    nav("prof");
  }
}

// ------------------------------------------------------------------ Profile
async function load() {
  list = await ipc.invoke("instances");
  sel = sel && list.find((i) => i.id === sel) ? sel : (list[0] && list[0].id);
  renderProfiles();
  await refreshStatus();
}
function renderProfiles() {
  const grid = $("profGrid");
  if (!list.length) {
    grid.innerHTML = `<div class="info">Noch kein Profil – klicke oben auf <b>＋ Neues Profil</b>.</div>`;
    updatePlay();
    return;
  }
  grid.innerHTML = list.map((i) => `
    <div class="pc ${i.id === sel ? "sel" : ""} ${i.running ? "live" : ""}" onclick="pick('${i.id}')">
      ${i.running ? '<span class="badge">LÄUFT</span>' : ""}
      <div class="ic" style="background-image:url(https://mc-heads.net/avatar/${i.uuid || "MHF_Steve"}/64)"></div>
      <b class="px">${esc(i.name)}</b>
      <small>${esc(i.mc)} · Fabric · ${(i.files || i.installed || []).length} Mods</small>
      <small>${fmtDur(i.playtime)} gespielt · ${fmtAgo(i.lastPlayed)}</small>
    </div>`).join("");
  updatePlay();
}
function pick(id) {
  sel = id;
  renderProfiles();
  openDetail();
}
function updatePlay() {
  const c = cur();
  const nMods = c ? (c.files || c.installed || []).length : 0;
  $("playSub").textContent = c ? `${c.mc}  ·  ${nMods} Mods` : "Keine Instanz";
  $("instCount").textContent = list.length === 1 ? "1 Instanz" : `${list.length} Instanzen`;
  const p = $("playList");
  p.innerHTML = list.map((i) => `
    <div class="mini ${i.running ? "live" : ""}" onclick="pick('${i.id}')">
      <span class="st"></span><b>${esc(i.name)}</b><small>${i.running ? "läuft" : fmtDur(i.playtime)}</small>
    </div>`).join("") || '<div class="info">Noch kein Profil.</div>';
}

// ------------------------------------------------------------ Detailansicht
async function openDetail() {
  const c = cur();
  if (!c) return;
  $("detail").classList.add("on");
  $("dName").textContent = c.name;
  $("dMc").textContent = c.mc;
  $("dLoader").textContent = "Fabric " + (c.loader || "");
  $("dState").textContent = c.running ? "läuft gerade" : "bereit";
  $("dLast").textContent = fmtAgo(c.lastPlayed);
  $("dPlay").textContent = fmtDur(c.playtime);
  $("dAvatar").src = "https://mc-heads.net/avatar/" + (c.uuid || "MHF_Steve") + "/64";
  const sz = await ipc.invoke("size", c.id);
  $("dSize").textContent = fmtBytes(sz);
  await loadMods();
  await loadPacks();
}
function closeDetail() { $("detail").classList.remove("on"); }

document.querySelectorAll(".dt").forEach((e) => {
  e.onclick = () => {
    document.querySelectorAll(".dt").forEach((x) => x.classList.toggle("on", x === e));
    document.querySelectorAll(".dPane").forEach((p) =>
      p.classList.toggle("on", p.dataset.dp === e.dataset.dt));
    if (e.dataset.dt === "worlds") loadWorlds();
    if (e.dataset.dt === "shots") loadShots();
  };
});

async function loadMods() {
  if (!sel) return;
  const r = await ipc.invoke("modList", sel);
  modFiles = r.files;
  renderMods();
}
function renderMods() {
  const q = ($("modFilter").value || "").toLowerCase();
  const list2 = modFiles.filter((f) => !q || f.name.toLowerCase().includes(q));
  $("cMods").textContent = modFiles.filter((f) => f.on).length;
  $("modList").innerHTML = list2.length ? list2.map((f) => `
    <div class="mrow ${f.on ? "" : "off"}">
      <div class="mn"><b>${esc(f.name)}</b><small>${esc(f.version || f.file)}</small>
        ${f.file === "saturn.jar" ? '<span class="lock">Saturn-Mod</span>'
          : f.vorinstalliert ? '<span class="lock">vorinstalliert</span>' : ""}</div>
      <button class="miniBtn" onclick="delMod('${esc(f.file)}')">&#128465;</button>
      <div class="tog ${f.on ? "on" : ""}" onclick="toggleMod('${esc(f.file)}',${!f.on})"></div>
    </div>`).join("") : '<div class="info">Keine Mods installiert.</div>';
}
async function toggleMod(file, on) {
  try {
    await ipc.invoke("toggleMod", { id: sel, file, on });
    await loadMods();
    toast(on ? "Mod aktiviert" : "Mod deaktiviert", "ok");
  } catch (e) { toast(e.message, "err"); }
}
async function delMod(file) {
  if (!confirm("Mod wirklich löschen?")) return;
  try {
    await ipc.invoke("deleteMod", { id: sel, file });
    await loadMods();
  } catch (e) { toast(e.message, "err"); }
}
async function loadPacks() {
  if (!sel) return;
  const map = [["res", "resourcepack"], ["shader", "shader"], ["data", "datapack"]];
  for (const [el, kind] of map) {
    const p = await ipc.invoke("packs", { id: sel, kind });
    $(el + "List").innerHTML = p.length ? p.map((x) => `
      <div class="prow">
        <div class="ph" style="width:38px;height:38px">&#128230;</div>
        <div class="pn2"><b>${esc(x.name)}</b><small>${x.folder ? "Ordner" : fmtBytes(x.size)}</small></div>
      </div>`).join("") : '<div class="info">Nichts installiert. Du kannst Packs unten im Reiter "Inhalte" herunterladen.</div>';
  }
}
async function loadWorlds() {
  if (!sel) return;
  worlds = await ipc.invoke("worlds", sel);
  $("worldList").innerHTML = worlds.length ? worlds.map((w) => `
    <div class="prow">
      <div class="ph" style="width:38px;height:38px">&#128230;</div>
      <div class="pn2"><b>${esc(w.levelName || w.name)}</b>
        <small>${esc(w.version || "")} · ${fmtDur(w.gameTime * 50)} gespielt · ${fmtBytes(w.size)}</small></div>
      <button class="miniBtn" onclick="ipc.invoke('openWorld','${esc(w.path.replace(/\\/g, "/"))}')">&#128230;</button>
    </div>`).join("") : '<div class="info">Noch keine Welt in diesem Profil.</div>';
}
async function loadShots() {
  if (!sel) return;
  shots = await ipc.invoke("screenshots", sel);
  $("shotList").innerHTML = shots.length ? shots.map((s) => `
    <div class="shot">
      <img src="file:///${esc(s.path.replace(/\\/g, "/"))}" onclick="shell_show('${esc(s.path.replace(/\\/g, "/"))}')">
      <div class="cap">${esc(s.name)}</div>
    </div>`).join("") : '<div class="info">Keine Screenshots.</div>';
}
function shell_show() { toast("Screenshots öffnen: im Reiter auf „Ordner öffnen“ klicken"); }

function openRename() {
  const c = cur();
  if (!c) return;
  openModal("Profil umbenennen",
    `<div class="frow"><label>Name</label><input type="text" id="newName" value="${esc(c.name)}"></div>`,
    "Speichern", async () => {
      const v = $("newName").value.trim();
      if (!v) return false;
      list = await ipc.invoke("rename", { id: c.id, name: v });
      renderProfiles(); openDetail();
      toast("Umbenannt", "ok");
    });
}

// -------------------------------------------------------------------- Modal
let modalAction = null;
function openModal(title, body, okLabel, action) {
  $("modalTitle").textContent = title;
  $("modalBody").innerHTML = body;
  $("modalOk").textContent = okLabel;
  modalAction = action;
  $("modal").classList.add("on");
}
function closeModal() { $("modal").classList.remove("on"); modalAction = null; }
async function modalOk() {
  if (modalAction) await modalAction();
  closeModal();
}

async function openCreate() {
  if (!versions.length) return;
  const rows = SUGGESTED.map(([slug, name]) =>
    `<div class="pick" data-slug="${slug}"><span class="ck"></span>${name}</div>`).join("");
  openModal("Neues Profil",
    `<div class="frow"><label>Name</label><input type="text" id="nm" value="NL"></div>
     <div class="frow"><label>Minecraft</label><select id="mcv">${versions.map((v) => `<option${v === SATURN_VORBEUGUNG ? " selected" : ""}>${v}</option>`).join("")}</select></div>
     <p class="info">Zusätzliche Mods (anklicken zum Abwählen):</p>
     <div class="modPick" id="pickBox">${rows}</div>
     <p class="info">Immer dabei: <b>Saturn-Mod</b>, Fabric API, Sodium, Nametags und Fullbright.
       Die Saturn-Mod (Menü, HUD-Editor, Scoreboard) gibt es für
       ${saturnVersionen().join(", ")}.</p>`,
    "Erstellen", async () => {
      const mods = [...document.querySelectorAll("#pickBox .pick.on")].map((e) => e.dataset.slug);
      const name = $("nm").value.trim() || "NL";
      const mc = $("mcv").value;
      toast("Profil wird erstellt …");
      try {
        list = await ipc.invoke("create", { name, mc, mods });
        sel = list[0].id;
        renderProfiles(); openDetail();
        toast("Profil fertig ", "ok");
      } catch (e) { toast("Fehler: " + e.message, "err"); }
    });
  // Standardauswahl
  document.querySelectorAll("#pickBox .pick").forEach((e) => {
    if (!["iris", "freelook"].includes(e.dataset.slug)) e.classList.add("on");
    e.onclick = () => e.classList.toggle("on");
  });
}

async function delProfile(id) {
  const c = list.find((i) => i.id === id);
  if (!confirm(`Profil "${c ? c.name : id}" wirklich löschen?\nAlle Mods und Welten gehen dabei verloren.`)) return;
  try {
    list = await ipc.invoke("delete", id);
    sel = list[0] && list[0].id;
    closeDetail();
    renderProfiles();
    await refreshStatus();
  } catch (e) { toast(e.message, "err"); }
}

// ------------------------------------------------------------------ Starten
async function launch() {
  if (!sel) { toast("Bitte zuerst ein Profil anlegen", "err"); return nav("prof"); }
  const b = $("go");
  if (!b) { toast("Start-Knopf nicht gefunden - Seite bitte neu laden", "err"); return; }
  b.disabled = true;
  $("progress").classList.add("on");
  $("progressText").textContent = "Starte…";
  logTarget = sel;
  try {
    await ipc.invoke("launch", { id: sel, ram: $("ram").value });
  } catch (e) {
    toast(e.message, "err");
    $("progress").classList.remove("on");
    b.disabled = false;
    return;
  }
  setTimeout(() => { b.disabled = false; $("progress").classList.remove("on"); }, 60000);
  await load();
}

async function login() {
  try {
    const a = await ipc.invoke("login");
    $("accName").textContent = a.name;
    toast("Angemeldet als " + a.name, "ok");
    await loadSkin();
    $("accBtn").onclick = () => logout();
  } catch (e) { toast("Login: " + e.message, "err"); }
}

// ------------------------------------------------------------------ Inhalte
document.querySelectorAll(".tab").forEach((t) => {
  t.onclick = () => {
    document.querySelectorAll(".tab").forEach((x) => x.classList.toggle("on", x === t));
    contentType = t.dataset.type;
    offset = 0; picked.clear(); bulk();
    loadContent(true);
  };
});

function fillCats() {
  const list2 = CAT_FOR[contentType] || CAT_FOR.mod;
  $("chips").innerHTML = list2.map((k) =>
    `<div class="cc ${k === "" ? "on" : ""}" data-c="${k}">${CATS[k] || k}</div>`).join("");
  document.querySelectorAll(".cc").forEach((e) => e.onclick = () => {
    document.querySelectorAll(".cc").forEach((x) => x.classList.toggle("on", x === e));
    $("fCat").value = e.dataset.c;
    loadContent(true);
  });
  $("fCat").innerHTML = `<option value="">Alle Kategorien</option>` +
    list2.slice(1).map((k) => `<option value="${k}">${CATS[k] || k}</option>`).join("");
}
function debounce() { clearTimeout(timer); timer = setTimeout(() => loadContent(true), 400); }

async function loadContent(reset) {
  const c = cur();
  if (!c) {
    $("res").innerHTML = '<div class="info">Wähle zuerst links ein Profil.</div>';
    return;
  }
  $("mcinfo").textContent = `für ${c.name} (${c.mc})`;
  fillCats();
  const box = $("res");
  if (reset) {
    offset = 0; picked.clear(); bulk();
    box.innerHTML = '<div class="sk"></div>'.repeat(6);
  }
  const cat = $("fCat").value;
  const loader = $("fLoader").value;
  const env = $("fEnv").value;

  try {
    let type = contentType;
    if (type === "mod" && loader) type = loader;
    const r = await ipc.invoke("search", {
      q: $("q").value, mc: c.mc, cat, sort: $("sort").value, offset, type,
    });
    total = r.total;
    const installed = c.installed || [];
    const have = new Set(installed);
    (c.files || []).forEach((f) => f.slug && have.add(f.slug));
    const html = r.hits.map((m) => {
      const isHave = have.has(m.slug);
      const cats = (m.cats || []).slice(0, 4).map((x) =>
        `<span>${esc(CATS[x] || x)}</span>`).join("");
      return `
      <div class="res ${picked.has(m.slug) ? "sel" : ""}" data-slug="${esc(m.slug)}" onclick="pickContent('${esc(m.slug)}',this)">
        <div class="ck">&#10003;</div>
        ${m.icon ? `<img src="${esc(m.icon)}">` : '<div class="ph">&#128230;</div>'}
        <div class="body">
          <b>${esc(m.title)}</b> <small style="display:inline">von ${esc(m.author)}</small>
          <small>${esc(m.desc)}</small>
          <div class="cs">${cats}</div>
        </div>
        <div class="side2">
          <div class="dl"> ${fmtNum(m.dl)}</div>
          <div class="act">
            ${isHave ? '<span class="miniBtn on">&#10003; Installiert</span>'
                     : '<span class="miniBtn">Installieren</span>'}
          </div>
        </div>
      </div>`;
    }).join("");

    if (reset) box.innerHTML = html || '<div class="info">Keine Treffer.</div>';
    else box.insertAdjacentHTML("beforeend", html);
    offset += r.hits.length;
    $("more").style.display = offset < total ? "inline-block" : "none";
  } catch (e) {
    box.innerHTML = `<div class="info">Suche fehlgeschlagen: ${esc(e.message)}</div>`;
  }
}
function pickContent(slug, el) {
  picked.has(slug) ? picked.delete(slug) : picked.add(slug);
  el.classList.toggle("sel");
  bulk();
}
function bulk() {
  const n = picked.size;
  $("bulk").classList.toggle("on", n > 0);
  $("bulkCount").textContent = n === 1 ? "1 ausgewählt" : `${n} ausgewählt`;
  $("bulkInstall").textContent = contentType === "mod" ? "Installieren" : "Herunterladen";
}
function clearSel() {
  picked.clear();
  document.querySelectorAll(".res.sel").forEach((e) => e.classList.remove("sel"));
  bulk();
}
async function installSel() {
  if (!sel) { toast("Erst ein Profil wählen", "err"); return; }
  const projects = [...picked];
  toast(contentType === "mod" ? `Installiere ${projects.length} …` : `Lade ${projects.length} herunter …`);
  try {
    if (contentType === "mod") {
      await ipc.invoke("addmods", { id: sel, projects });
      list = await ipc.invoke("instances");
      renderProfiles();
      if ($("detail").classList.contains("on")) await loadMods();
      toast("Fertig ", "ok");
    } else {
      await ipc.invoke("addContent", { id: sel, projects, kind: contentType });
      if ($("detail").classList.contains("on")) await loadPacks();
      toast("Heruntergeladen ", "ok");
    }
    clearSel();
    loadContent(true);
  } catch (e) { toast("Fehler: " + e.message, "err"); }
}

// -------------------------------------------------------------------- Logs
async function selectLog() {
  const selBox = $("logSel");
  const prev = logTarget;
  const opts = list.map((i) =>
    `<option value="${i.id}" ${i.id === prev ? "selected" : ""}>${esc(i.name)}${i.running ? " (läuft)" : ""}</option>`).join("");
  selBox.innerHTML = opts;
  if (!list.length) { $("logBox").textContent = "Noch kein Profil angelegt."; return; }
  logTarget = list.find((i) => i.id === prev) ? prev : sel;
  logLines = await ipc.invoke("logs", logTarget);
  paintLog();
}
function paintLog() {
  const filter = $("logFilter").checked;
  const lines = filter ? logLines.filter((l) => /error|exception|warn|fail/i.test(l)) : logLines;
  const box = $("logBox");
  box.textContent = lines.length ? lines.join("\n") : "(keine Ausgabe)";
  if ($("logAuto").checked) box.scrollTop = box.scrollHeight;
}
function clearLog() { logLines = []; paintLog(); }
function copyLog() {
  navigator.clipboard.writeText(logLines.join("\n"));
  toast("Log kopiert", "ok");
}

ipc.on("ilog", (_e, { id, line }) => {
  if (id === logTarget) { logLines.push(line); if (logLines.length > 3000) logLines.shift(); paintLog(); }
});
ipc.on("progress", (_e, { id, pct, task, total: t }) => {
  if (id !== sel) return;
  $("progress").classList.add("on");
  $("progressBar").style.width = pct + "%";
  // Ohne "total" kommt aus dem Hauptprozess ein fertiger Text - z. B.
  // "Lade 8 Mods von Modrinth...". Der soll auch erscheinen, sonst
  // stuende dort nur eine Zahl ohne Bedeutung.
  $("progressText").textContent = t ? `${pct}%  (${task}/${t})` : (task || `${pct}%`);
});
ipc.on("installed", (_e, { file }) => toast("+ " + file, "ok"));
ipc.on("done", async ({ id }) => {
  $("progress").classList.remove("on");
  toast("Minecraft beendet.", "ok");
  await load();
  if ($("detail").classList.contains("on")) openDetail();
});
ipc.on("log", (_e, m) => {
  const s = String(m);
  if (s.startsWith("-")) toast(s.slice(0, 110), "err");
});

// ---------------------------------------------------------------- Einstellungen
function save() {
  settings = { ram: $("ram").value, col: $("col").value, theme: $("theme").value, font: $("font").value };
  applySettings();
  ipc.invoke("saveSettings", settings);
}
function applySettings() {
  const root = document.documentElement;
  root.dataset.theme = settings.theme || "dark";

  // Akzentfarbe: Standard ist Schwarz-Weiss, der Farbwähler darf sie ändern.
  const ac = settings.col || (settings.theme === "light" ? "#111111" : "#ffffff");
  root.style.setProperty("--ac", ac);
  // Text auf der Akzentfläche muss kontrastieren, sonst wird Weiß auf Weiß unlesbar
  root.style.setProperty("--on-ac", luminance(ac) > 0.55 ? "#000000" : "#ffffff");
  const c = hexToRgb(ac);
  root.style.setProperty("--ac-soft",
    `rgba(${c.r},${c.g},${c.b},${settings.theme === "light" ? 0.08 : 0.12})`);

  document.body.style.fontFamily = settings.font === "pixel"
    ? '"Silkscreen", monospace' : 'Inter, "Segoe UI", sans-serif';
}

function hexToRgb(hex) {
  const v = hex.replace("#", "");
  const n = v.length === 3 ? v.split("").map((x) => x + x).join("") : v;
  return {
    r: parseInt(n.slice(0, 2), 16) || 0,
    g: parseInt(n.slice(2, 4), 16) || 0,
    b: parseInt(n.slice(4, 6), 16) || 0,
  };
}

/** Helligkeit 0..1 - ab 0.55 gilt die Farbe als hell. */
function luminance(hex) {
  const { r, g, b } = hexToRgb(hex);
  return (0.299 * r + 0.587 * g + 0.114 * b) / 255;
}
["ram", "col", "theme", "font"].forEach((id) => $(id).onchange = save);

// --------------------------------------------------------------------- Start
(async function () {
  // Kein festes "1.0.0" als Rueckfall: sonst zeigt der Client eine Version,
  // die es gar nicht mehr gibt, sobald das Einspruezen scheitert.
  $("ver").textContent = window.saturnVersion ? "v" + window.saturnVersion : "";
  document.querySelectorAll(".nb").forEach((e) => e.onclick = () => nav(e.dataset.p));
  settings = await ipc.invoke("settings");
  if (settings.ram) $("ram").value = settings.ram;
  if (settings.col) $("col").value = settings.col;
  if (settings.theme) $("theme").value = settings.theme;
  if (settings.font) $("font").value = settings.font;
  applySettings();
  setupBackground();

  versions = await ipc.invoke("versions");

  await setupSkin();

  const a = skinAcc;
  if (a) $("accName").textContent = a.name;
  $("accBtn").onclick = a ? () => logout() : () => login();

  await load();
  fillCats();
  setupFensterKnoepfe();
  setInterval(refreshStatus, 2000);
})();

async function logout() {
  await ipc.invoke("logout");
  toast("Abgemeldet");
  location.reload();
}

// ------------------------------------------------------------ Fensterknöpfe
// Das Fenster hat keinen Rahmen, die Knöpfe zeichnet die Seite selbst.
// "min", "max" und "close" macht das Hauptfenster, weil die Seite mit
// nodeIntegration keinen Zugriff auf BrowserWindow hat.
function winAktion(aktion) {
  ipc.invoke("win", aktion).catch(() => {});
}

/**
 * Hält das Zeichen des Maximieren-Knopfes passend zum Zustand: Quadrat, solange
 * das Fenster normal ist, und zwei übereinander, wenn es zurück in die normale
 * Größe soll. Das Hauptfenster meldet den Wechsel, damit auch ein Maximieren
 * per Doppelklick auf die Kopfzeile das Zeichen mitändert.
 */
function setupFensterKnoepfe() {
  const knopf = $("wkMax");
  if (!knopf) return;
  const uebertragen = (z) => {
    if (!z) return;
    const gross = z.maximized || z.fullScreen;
    knopf.dataset.stand = gross ? "restore" : "maximize";
    knopf.title = gross ? "Wiederherstellen" : "Vergrößern";
    knopf.setAttribute("aria-label", knopf.title);
  };
  uebertragen({ maximized: false, fullScreen: false });
  ipc.invoke("winState").then(uebertragen).catch(() => {});
  ipc.on("winState", (_e, z) => uebertragen(z));

  // Doppelklick auf die Kopfzeile (aber nicht auf die Knöpfe) schaltet gross/klein
  const kopf = document.querySelector("header");
  if (kopf) {
    kopf.addEventListener("dblclick", (e) => {
      if (e.target.closest("button, .pill")) return;
      winAktion("toggle");
    });
  }
}