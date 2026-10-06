/**
 * Zeichnet den angemeldeten Skin als echtes 3D-Modell auf ein Canvas.
 *
 * Grundaufbau: ein Minecraft-Skin ist eine 64x64-Textur, aus der sich sechs
 * Körperteile (Kopf, Rumpf, Arme, Beine) als Quader schneiden lassen. Jeder
 * Quader hat sechs Flächen mitUV-Koordinaten. Beim Zeichnen wird jede Fläche
 * umYaw und Pitch gedreht, in die Bildschirmebene abgebildet und nach Tiefe
 * sortiert - dadurch verdecken sich verdeckte Seiten korrekt.
 */

const TEX = 64;               // Kantenlänge der Skin-Textur

/** UV-Bereiche: [x, y, breite, hoehe] auf der 64x64-Textur. */
const UV = {
  head: { front: [8, 8, 8, 8], back: [24, 8, 8, 8], right: [0, 8, 8, 8], left: [16, 8, 8, 8], top: [8, 0, 8, 8], bottom: [16, 0, 8, 8] },
  body: { front: [20, 20, 8, 12], back: [32, 20, 8, 12], right: [16, 20, 4, 12], left: [28, 20, 4, 12], top: [20, 16, 8, 4], bottom: [28, 16, 8, 4] },
  armR: { front: [44, 20, 4, 12], back: [52, 20, 4, 12], right: [40, 20, 4, 12], left: [48, 20, 4, 12], top: [44, 16, 4, 4], bottom: [48, 16, 4, 4] },
  armL: { front: [36, 52, 4, 12], back: [44, 52, 4, 12], right: [32, 52, 4, 12], left: [40, 52, 4, 12], top: [36, 48, 4, 4], bottom: [40, 48, 4, 4] },
  // Schmale (slim, 3 Einheiten breite) Arme liegen an DENSELBEN Stellen wie
  // die normalen - nur die breitenabhaengigen Flaechen schrumpfen auf 3.
  // Die vier tiefen Seiten (left/right/back) bleiben 4 breit, weil der Arm
  // 4 Einheiten tief ist.
  armRslim: { front: [44, 20, 3, 12], back: [51, 20, 4, 12], right: [40, 20, 4, 12], left: [47, 20, 4, 12], top: [44, 16, 3, 4], bottom: [47, 16, 3, 4] },
  armLslim: { front: [36, 52, 3, 12], back: [43, 52, 4, 12], right: [32, 52, 4, 12], left: [39, 52, 4, 12], top: [36, 48, 3, 4], bottom: [39, 48, 3, 4] },
  legR: { front: [4, 20, 4, 12], back: [12, 20, 4, 12], right: [0, 20, 4, 12], left: [8, 20, 4, 12], top: [4, 16, 4, 4], bottom: [8, 16, 4, 4] },
  legL: { front: [20, 52, 4, 12], back: [28, 52, 4, 12], right: [16, 52, 4, 12], left: [24, 52, 4, 12], top: [20, 48, 4, 4], bottom: [24, 48, 4, 4] },
};

/**
 * Koerperteile in Minecraft-Einheiten. y zeigt nach oben, +z nach vorn.
 * Bei einem Slim-Skin sind BEIDE Arme nur 3 Einheiten breit und sitzen
 * jeweils 1 Einheit weiter innen - sonst stehen sie schief vom Rumpf ab.
 */
function parts(slim) {
  const armW = slim ? 3 : 4;
  return [
    { uv: UV.head, x0: -4, y0: 24, z0: -4, x1: 4, y1: 32, z1: 4 },
    { uv: UV.body, x0: -4, y0: 12, z0: -2, x1: 4, y1: 24, z1: 2 },
    { uv: slim ? UV.armRslim : UV.armR, x0: -4 - armW, y0: 12, z0: -2, x1: -4, y1: 24, z1: 2 },
    { uv: slim ? UV.armLslim : UV.armL, x0: 4, y0: 12, z0: -2, x1: 4 + armW, y1: 24, z1: 2 },
    { uv: UV.legR, x0: -4, y0: 0, z0: -2, x1: 0, y1: 12, z1: 2 },
    { uv: UV.legL, x0: 0, y0: 0, z0: -2, x1: 4, y1: 12, z1: 2 },
  ];
}

/**
 * Die sechs Flächen eines Quaders.
 * Je Fläche: Startpunkt, u-Richtung, v-Richtung im 3D-Raum.
 * v zeigt nach unten, damit die Textur nicht gespiegelt wirkt.
 */
function facesOf(p) {
  return [
    { name: "front", o: [p.x0, p.y1, p.z1], u: [p.x1 - p.x0, 0, 0], v: [0, -(p.y1 - p.y0), 0] },
    { name: "back", o: [p.x1, p.y1, p.z0], u: [-(p.x1 - p.x0), 0, 0], v: [0, -(p.y1 - p.y0), 0] },
    { name: "left", o: [p.x0, p.y1, p.z1], u: [0, 0, p.z0 - p.z1], v: [0, -(p.y1 - p.y0), 0] },
    { name: "right", o: [p.x1, p.y1, p.z0], u: [0, 0, p.z1 - p.z0], v: [0, -(p.y1 - p.y0), 0] },
    { name: "top", o: [p.x0, p.y1, p.z1], u: [p.x1 - p.x0, 0, 0], v: [0, 0, p.z0 - p.z1] },
    { name: "bottom", o: [p.x0, p.y0, p.z0], u: [p.x1 - p.x0, 0, 0], v: [0, 0, p.z1 - p.z0] },
  ];
}

function rotate(v, yaw, pitch) {
  const cy = Math.cos(yaw), sy = Math.sin(yaw);
  const cp = Math.cos(pitch), sp = Math.sin(pitch);
  // erst um Y (Yaw), dann um X (Neigung)
  let x = v[0] * cy + v[2] * sy;
  let z = -v[0] * sy + v[2] * cy;
  let y = v[1] * cp - z * sp;
  z = v[1] * sp + z * cp;
  return [x, y, z];
}

class SkinView {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.img = null;
    this.slim = false;
    this.yaw = 0.6;
    this.pitch = 0.12;
    this.autoSpin = true;
    this.spin = 0;
    this.dragging = false;
    this.lastX = 0;
    this.dragDistance = 0;
    this.onDragStart = null;
  }

  /** Lädt die Skin-Textur. Quelle fällt auf einen freien Dienst zurück. */
  async load(url, slim, uuid) {
    const sources = [];
    if (url) sources.push(url);
    if (uuid) sources.push(`https://mc-heads.net/skin/${uuid}`);
    for (const src of sources) {
      try {
        const img = await loadImage(src);
        if (img.width < TEX || img.height < TEX) continue;
        this.img = img;
        // Das Profil-Flag vom Mojang-Server stimmt nicht immer mit der
        // tatsaechlich hochgeladenen Textur ueberein. Was wirklich im Bild
        // steht, gewinnt - sonst fehlt je nach Quelle ein Arm.
        this.slim = detectSlim(img);
        if (detectSlim(img) !== !!slim) {
          console.info(
            `[skin] Profil meldet slim=${!!slim}, Textur ist slim=${this.slim} - es gilt die Textur`
          );
        }
        return true;
      } catch {
        // nächste Quelle versuchen
      }
    }
    this.img = null;
    return false;
  }

  startDrag(e) {
    this.dragging = true;
    this.dragDistance = 0;
    this.lastX = e.clientX;
    this.lastY = e.clientY;
    this.wasSpinning = this.autoSpin;
    this.autoSpin = false;
    if (this.onDragStart) this.onDragStart();
  }

  drag(e) {
    if (!this.dragging) return;
    const dx = e.clientX - this.lastX;
    const dy = e.clientY - this.lastY;
    this.lastX = e.clientX;
    this.lastY = e.clientY;
    this.dragDistance += Math.abs(dx) + Math.abs(dy);
    this.yaw += dx * 0.012;
    this.pitch = Math.max(-0.9, Math.min(0.9, this.pitch + dy * 0.008));
  }

  endDrag() {
    this.dragging = false;
    this.autoSpin = this.wasSpinning;
  }

  tick(dt) {
    if (this.autoSpin && !this.dragging) this.yaw += dt * 0.55;
  }

  /** Einmal neu zeichnen. */
  render() {
    const c = this.canvas, ctx = this.ctx;
    const w = c.width, h = c.height;
    ctx.clearRect(0, 0, w, h);
    if (!this.img) return;

    const list = parts(this.slim);
    const quads = [];

    for (const p of list) {
      for (const f of facesOf(p)) {
        const uv = p.uv[f.name];
        if (!uv) continue;
        const o = rotate(f.o, this.yaw, this.pitch);
        const uEnd = rotate(add(f.o, f.u), this.yaw, this.pitch);
        const vEnd = rotate(add(f.o, f.v), this.yaw, this.pitch);
        quads.push({
          o, u: sub(uEnd, o), v: sub(vEnd, o),
          tex: uv,
          depth: (o[2] + uEnd[2] + vEnd[2]) / 3,
        });
      }
    }

    // weit weg zuerst zeichnen, damit Naeheres ueberdeckt
    quads.sort((a, b) => a.depth - b.depth);

    // Skalierung: Modell ist 32 Einheiten hoch
    const scale = Math.min(w / 34, h / 38);
    const cx = w / 2;
    const cy = h / 2 + 16 * scale;   // Fusspunkte leicht unter der Mitte

    for (const q of quads) {
      const O = project(q.o, scale, cx, cy);
      const U = project(add(q.o, q.u), scale, cx, cy);
      const V = project(add(q.o, q.v), scale, cx, cy);
      const [tw, th] = [q.tex[2], q.tex[3]];

      // Flaeche zu klein -> Rechteck weniger als ein Pixel, das faellt weg
      const area = Math.abs((U.x - O.x) * (V.y - O.y) - (V.x - O.x) * (U.y - O.y));
      if (area < 0.35) continue;

      ctx.save();
      // Die Texturvierecke exakt auf die projizierte Flaeche abbilden
      ctx.setTransform(
        (U.x - O.x) / tw, (U.y - O.y) / tw,
        (V.x - O.x) / th, (V.y - O.y) / th,
        O.x, O.y
      );
      ctx.imageSmoothingEnabled = false;
      try {
        ctx.drawImage(this.img, q.tex[0], q.tex[1], tw, th, 0, 0, tw, th);
      } catch {
        // Quelle eventuell mit CORS gesperrt - dann Fläche überspringen
      }
      ctx.restore();
    }
  }
}

function add(a, b) { return [a[0] + b[0], a[1] + b[1], a[2] + b[2]]; }
function sub(a, b) { return [a[0] - b[0], a[1] - b[1], a[2] - b[2]]; }

/** Orthografische Projektion: x nach rechts, y nach unten. */
function project(p, scale, cx, cy) {
  return { x: cx + p[0] * scale, y: cy - p[1] * scale };
}

/**
 * Sind die Arme in dieser Textur schmal (3 Einheiten)?
 *
 * Beide Arm-Vorderseiten beginnen bei x=44 (rechts) bzw. x=36 (links) und
 * sind 4 breit, wenn der Skin normal ist. Beim schmalen Skin wird nur die
 * erste Spalte der Vorderseiten mit Farbe bemalt - die zweite (x=47 bzw.
 * x=39) bleibt durchsichtig, weil dort in Minecraft nichts hingehoert wird.
 * Wir zaehlen also, wie viele dieser beiden Spalten bemalt sind.
 */
function detectSlim(img) {
  const c = document.createElement("canvas");
  c.width = TEX;
  c.height = TEX;
  const g = c.getContext("2d", { willReadFrequently: true });
  g.drawImage(img, 0, 0, TEX, TEX);
  let d;
  try {
    d = g.getImageData(0, 0, TEX, TEX).data;
  } catch {
    return false;          // fremde Herkunft ohne Pixelzugriff
  }
  // Spalten, die bei einem normalen Arm zwingend bemalt waeren
  const spalten = [
    [47, 20],               // rechter Arm, 2. Spalte
    [39, 52],               // linker Arm, 2. Spalte
  ];
  let bemalt = 0;
  for (const [x, y0] of spalten) {
    for (let y = y0; y < y0 + 12; y++) {
      if (d[(y * TEX + x) * 4 + 3] > 8) { bemalt++; break; }
    }
  }
  // Beide Spalten frei = schmale Arme
  return bemalt === 0;
}

/** Bild laden; Fehler werden als Ablehnung gemeldet. */
function loadImage(src) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error("Bild nicht ladbar: " + src));
    img.src = src;
  });
}

module.exports = { SkinView };