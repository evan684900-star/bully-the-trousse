/* Bully the Trousse — Monde Ville : moteur (plateformes 2D, transitions,
   évènements du calendrier, cinématique d'arrivée, escaliers, générique).
   Les niveaux eux-mêmes (aéroport + rue, réception, pièce noire, monde bugé
   et combat de boss) sont dans ville-levels.js. Accès au jeu via window.__bt. */
(function () {
    "use strict";
    const B = window.__bt;
    const A = window.VilleArt;
    if (!B || !A) { console.warn("ville.js : __bt ou VilleArt absent"); return; }
    const S = () => B.save;
    const L = (fr, en) => (S().lang === "en" ? en : fr);
    const { sr, rr, clamp, c01, lerp } = A;
    const eIO = (t) => (t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2);
    const eOut = (t) => 1 - Math.pow(1 - t, 3);
    const eIn = (t) => t * t * t;
    const pseudo = () => S().pseudo || L("toi", "you");

    /* ============ ÉVÈNEMENTS (calendrier identique pour tous les joueurs) ============
       Chaque jour UTC tire, à partir de son seul numéro, 5 à 9 évènements de
       2 à 20 minutes : tout le monde voit donc exactement le même calendrier,
       sans serveur. Le tableau de la réception affiche les 5 jours à venir. */
    const DAY = 86400000;
    const EVENTS = {
        coupure: { emoji: "⚡", name: ["Coupure de courant", "Power outage"], fx: ["Boutique et ascenseur hors service : il faudra prendre les escaliers.", "Shop and elevator out of order: take the stairs."] },
        fuite: { emoji: "💧", name: ["Fuite d'eau", "Water leak"], fx: ["Des flaques sur le toit : atterris dedans pour glisser 3 s puis repartir plus vite.", "Puddles on the roof: land in one to slide for 3 s, then take off faster."] },
        pluie: { emoji: "🌧️", name: ["Pluie", "Rain"], fx: ["Comme la fuite d'eau, mais avec bien plus de flaques.", "Like the water leak, but with many more puddles."] },
        canicule: { emoji: "🥵", name: ["Canicule", "Heatwave"], fx: ["La trousse doit se reposer 2 minutes tous les 5 lancers.", "The pencil case must rest 2 minutes every 5 throws."] },
    };
    const EVENT_KEYS = Object.keys(EVENTS);
    function mulberry(a) {
        return function () {
            a |= 0; a = (a + 0x6D2B79F5) | 0;
            let t = Math.imul(a ^ (a >>> 15), 1 | a);
            t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
            return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
        };
    }
    const dayCache = {};
    function eventsForDay(d) {
        if (dayCache[d]) return dayCache[d];
        const rnd = mulberry((((d * 2654435761) >>> 0) ^ 0x9e3779b9) >>> 0);
        const n = 5 + Math.floor(rnd() * 5);
        const list = [];
        for (let i = 0; i < n; i++) {
            const type = EVENT_KEYS[Math.floor(rnd() * EVENT_KEYS.length)];
            const dur = (2 + Math.floor(rnd() * 19)) * 60000; // 2 à 20 min
            list.push({ type, start: d * DAY + Math.floor(rnd() * 1400) * 60000, dur });
        }
        list.sort((a, b) => a.start - b.start);
        const out = [];
        let lastEnd = -Infinity;
        list.forEach((e) => {
            const s = Math.max(e.start, lastEnd + 10 * 60000);
            if (s + e.dur > (d + 1) * DAY) return;
            out.push({ type: e.type, start: s, end: s + e.dur });
            lastEnd = s + e.dur;
        });
        dayCache[d] = out;
        return out;
    }
    // TEST TEMPORAIRE — pluie forcée pendant 24 h à chaque chargement du jeu.
    // Pour l'enlever : repasser TEST_FORCED_RAIN à false (ou supprimer ces lignes).
    const TEST_FORCED_RAIN = true;
    let forcedEvent = TEST_FORCED_RAIN
        ? { type: "pluie", start: Date.now(), end: Date.now() + DAY, forced: true }
        : null;
    function activeEvent(now) {
        now = now || Date.now();
        if (forcedEvent && now < forcedEvent.end) return forcedEvent;
        const d = Math.floor(now / DAY);
        for (const dd of [d - 1, d]) {
            for (const e of eventsForDay(dd)) if (now >= e.start && now < e.end) return e;
        }
        return null;
    }
    function upcomingEvents() {
        const now = Date.now(), d = Math.floor(now / DAY);
        const out = [];
        if (forcedEvent && now < forcedEvent.end) out.push(forcedEvent);
        for (let dd = d - 1; dd <= d + 5; dd++) {
            eventsForDay(dd).forEach((e) => { if (e.end > now && e.start < now + 5 * DAY) out.push(e); });
        }
        return out;
    }
    const isOutage = () => { const e = activeEvent(); return !!e && e.type === "coupure"; };
    function fmtTime(ts) {
        const d = new Date(ts);
        return String(d.getHours()).padStart(2, "0") + ":" + String(d.getMinutes()).padStart(2, "0");
    }
    function fmtDay(ts) {
        const d = new Date(ts);
        return d.toLocaleDateString(S().lang === "en" ? "en-GB" : "fr-FR", { weekday: "short", day: "numeric", month: "short" });
    }

    /* ============ ÉTAT GLOBAL ============ */
    const TS = 64;           // taille de la trousse (px virtuels)
    const SPEED = 300, GRAV = 2300, JUMP = 820;
    const DASH_V = 950, DASH_T = 0.17, DASH_CD = 3;
    const V = {
        on: false, paused: false, raf: 0, last: 0, t: 0, dt: 0,
        mode: "play", level: null, camX: 0, camLock: null,
        VW: 960, VH: 540, GY: 445, sc: 1, dpr: 1,
        shake: 0, glitch: 0, near: null, parts: [], npcs: [],
        lock: false, allowDash: true, story: null, dashHint: 0,
    };
    const P = {
        x: 0, alt: 0, vx: 0, va: 0, dir: 1, ground: true, coyote: 0,
        dashT: 0, dashCd: 0, walk: 0, rot: 0, invert: false, sword: false,
        swing: 0, swingCd: 0, hurt: 0, hearts: 5, frozen: false, visible: true,
        scale: 1, alpha: 1, bang: 0, say: "", sayT: 0,
    };

    /* ============ DOM ============ */
    const css = `
#ville-screen{position:fixed;inset:0;z-index:45;display:none;background:#000;touch-action:none;user-select:none;-webkit-user-select:none;overflow:hidden}
#ville-screen.on{display:block}
#ville-canvas{position:absolute;inset:0;width:100%;height:100%;display:block}
#ville-back{z-index:3}
.vl-hud{position:absolute;top:calc(10px + env(safe-area-inset-top));left:50%;transform:translateX(-50%);display:flex;gap:8px;z-index:2;pointer-events:none;flex-wrap:wrap;justify-content:center;max-width:70vw}
.vl-hud .money-pill{font-size:14px;padding:6px 14px;white-space:nowrap}
#ville-event{display:none}
.vl-msg{position:absolute;left:50%;bottom:calc(130px + env(safe-area-inset-bottom));transform:translateX(-50%);width:max-content;max-width:min(90vw,560px);text-align:center;color:#fff;font-weight:800;font-size:18px;line-height:1.4;text-shadow:0 2px 8px rgba(0,0,0,.95);opacity:0;transition:opacity .4s;pointer-events:none;z-index:2}
.vl-msg.show{opacity:1}
.vl-pop{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);background:var(--panel-bg);border:2px solid var(--panel-border);border-radius:16px;padding:16px 18px;width:max-content;min-width:230px;max-width:min(88vw,380px);z-index:4;display:none;color:var(--text);box-shadow:0 10px 30px rgba(0,0,0,.45)}
.vl-pop.show{display:block}
.vl-pop h3{margin:0 34px 12px 0;font-size:16px}
.vl-pop p{margin:0 0 10px;font-size:13.5px;line-height:1.45;color:var(--text)}
.vl-col{display:flex;flex-direction:column;gap:8px}
.vl-col .btn{width:100%;text-align:left}
.vl-x{position:absolute;top:10px;right:10px;width:30px;height:30px;border-radius:50%;border:2px solid var(--panel-border);background:transparent;color:var(--text);font-weight:800;cursor:pointer}
.vl-board{max-height:50vh;overflow:auto;display:flex;flex-direction:column;gap:6px;font-size:13px}
.vl-board .row{padding:8px 10px;border-radius:10px;background:var(--card-bg);border:2px solid var(--panel-border);font-weight:700}
.vl-board .row.now{border-color:var(--accent)}
.vl-board small{display:block;color:var(--text-dim);font-weight:600;margin-top:2px}
.vl-keys{display:grid;grid-template-columns:auto 1fr;gap:6px 12px;font-size:13px;margin:6px 0 4px}
.vl-keys b{background:var(--card-bg);border:2px solid var(--panel-border);border-radius:6px;padding:1px 7px;text-align:center;white-space:nowrap}
.vl-choices{position:absolute;left:50%;bottom:calc(26px + env(safe-area-inset-bottom));transform:translateX(-50%);display:none;gap:10px;z-index:4;flex-wrap:wrap;justify-content:center;width:max-content;max-width:94vw;background:var(--panel-bg);border:2px solid var(--panel-border);border-radius:16px;padding:12px}
.vl-choices.show{display:flex}
.vl-touch{position:absolute;left:0;right:0;bottom:calc(14px + env(safe-area-inset-bottom));display:none;justify-content:space-between;padding:0 14px;z-index:3;pointer-events:none}
.vl-touch.show{display:flex}
.vl-touch .grp{display:flex;gap:10px;pointer-events:auto;align-items:flex-end}
.vl-touch button{width:58px;height:58px;border-radius:50%;border:2px solid rgba(255,255,255,.35);background:rgba(20,24,36,.55);color:#fff;font-size:22px;font-weight:900;touch-action:none;-webkit-tap-highlight-color:transparent}
.vl-touch button.on{background:rgba(255,210,63,.6)}
.vl-touch button.hide{display:none}
.vl-touch button.cd{opacity:.4}
.vl-credits{position:absolute;inset:0;background:#000;display:none;overflow:hidden;z-index:6;color:#fff}
.vl-credits.show{display:block}
.vl-roll{position:absolute;left:50%;top:0;transform:translateX(-50%);width:min(90vw,560px);text-align:center;font-size:18px;line-height:1.6;will-change:transform}
.vl-roll p{margin:0 0 20px}
.vl-roll h4{font-size:13px;letter-spacing:3px;color:#9a9a9a;margin:42px 0 12px;font-weight:700}
.vl-stat{display:flex;justify-content:space-between;gap:16px;font-size:15px;color:#d8d8d8;margin:0 0 6px;text-align:left}
.vl-stat span:last-child{color:#fff;font-weight:700;text-align:right}
.vl-final{font-size:22px;font-weight:800;margin-top:70px}
.vl-cont{position:absolute;bottom:calc(30px + env(safe-area-inset-bottom));left:0;right:0;text-align:center;color:#8a8a8a;font-size:13px;opacity:0;transition:opacity 1s}
.vl-cont.show{opacity:1}
body.ville-active .corner-icons-right, body.ville-active #btn-links{display:none}
`;
    const styleEl = document.createElement("style");
    styleEl.textContent = css;
    document.head.appendChild(styleEl);
    const root = document.createElement("div");
    root.id = "ville-screen";
    root.innerHTML =
        '<canvas id="ville-canvas"></canvas>' +
        '<button class="btn secondary small back-btn" id="ville-back">← Menu</button>' +
        '<div class="vl-hud"><div class="money-pill">💰 <span id="ville-money">0</span> $</div><div class="money-pill" id="ville-event"></div></div>' +
        '<div class="vl-msg" id="ville-msg"></div>' +
        '<div class="vl-pop" id="ville-pop"></div>' +
        '<div class="vl-choices" id="ville-choices"></div>' +
        '<div class="vl-touch" id="ville-touch">' +
            '<div class="grp"><button data-k="left">◀</button><button data-k="right">▶</button></div>' +
            '<div class="grp"><button data-k="atk" class="hide">⚔️</button><button data-k="dash">⚡</button><button data-k="e">E</button><button data-k="jump">▲</button></div>' +
        '</div>' +
        '<div class="vl-credits" id="ville-credits"><div class="vl-roll" id="ville-roll"></div><div class="vl-cont" id="ville-cont"></div></div>';
    document.body.appendChild(root);
    const canvas = root.querySelector("#ville-canvas");
    const ctx = canvas.getContext("2d");
    const $ = (id) => document.getElementById(id);
    const msgEl = $("ville-msg"), popEl = $("ville-pop"), choicesEl = $("ville-choices"), touchEl = $("ville-touch");
    const creditsEl = $("ville-credits"), rollEl = $("ville-roll"), contEl = $("ville-cont");
    const backBtn = $("ville-back"), moneyEl = $("ville-money"), eventEl = $("ville-event");
    const isTouch = ("ontouchstart" in window) || (window.matchMedia && matchMedia("(pointer: coarse)").matches);
    touchEl.classList.toggle("show", !!isTouch);

    function resize() {
        V.dpr = window.devicePixelRatio || 1;
        const w = root.clientWidth || innerWidth, h = root.clientHeight || innerHeight;
        canvas.width = Math.round(w * V.dpr);
        canvas.height = Math.round(h * V.dpr);
        V.sc = Math.min(h / 540, w / 760);
        V.VW = w / V.sc;
        V.VH = h / V.sc;
        V.GY = Math.min(V.VH - 95, 445 + (V.VH - 540) * 0.5);
    }
    window.addEventListener("resize", () => { if (V.on) resize(); });

    let msgTimer = 0;
    function showMsg(text, secs) {
        msgEl.textContent = text;
        msgEl.classList.add("show");
        msgTimer = secs == null ? 4 : secs; // 0 = reste affiché
    }
    function hideMsg() { msgEl.classList.remove("show"); msgTimer = 0; }

    function closePop() { popEl.classList.remove("show"); popEl.innerHTML = ""; V.lock = false; }
    // Petite fenêtre (pas plein écran) : titre + liste de boutons
    function openPop(title, bodyHtml, buttons, opts) {
        opts = opts || {};
        popEl.innerHTML = '<button class="vl-x" aria-label="Fermer">✕</button><h3></h3>' + (bodyHtml || "") + '<div class="vl-col"></div>';
        popEl.querySelector("h3").textContent = title;
        const col = popEl.querySelector(".vl-col");
        (buttons || []).forEach((b) => {
            const btn = document.createElement("button");
            btn.className = "btn small" + (b.secondary ? " secondary" : "");
            btn.textContent = b.label;
            btn.addEventListener("click", () => { closePop(); b.act(); });
            col.appendChild(btn);
        });
        popEl.querySelector(".vl-x").addEventListener("click", () => { closePop(); if (opts.onClose) opts.onClose(); });
        popEl.classList.add("show");
        V.lock = true;
        V.popButtons = buttons || [];
    }

    let choiceCb = null;
    function showChoices(opts, cb) {
        choicesEl.innerHTML = "";
        opts.forEach((label, i) => {
            const btn = document.createElement("button");
            btn.className = "btn small";
            btn.textContent = (i + 1) + ". " + label;
            btn.addEventListener("click", () => pickChoice(i));
            choicesEl.appendChild(btn);
        });
        choicesEl.classList.add("show");
        choiceCb = cb;
    }
    function pickChoice(i) {
        if (!choiceCb) return;
        const cb = choiceCb;
        choiceCb = null;
        choicesEl.classList.remove("show");
        cb(i);
    }

    /* ============ AUDIO ============ */
    const aFeet = $("sfx-footsteps"), aTires = $("sfx-tires"), aBoss = $("boss-music");
    if (aFeet) aFeet.volume = 0.55;
    if (aBoss) aBoss.volume = 0.5;
    function playA(a, fromStart) {
        if (!a) return;
        try { if (fromStart) a.currentTime = 0; const p = a.play(); if (p && p.catch) p.catch(() => {}); } catch (e) { /* jamais bloquant */ }
    }
    function pauseA(a) { if (a && !a.paused) a.pause(); }
    let feetOn = false;
    function footsteps(on) {
        if (on === feetOn) return;
        feetOn = on;
        if (on) playA(aFeet); else pauseA(aFeet);
    }
    function pauseWorldMusic() { Object.values(B.musicTracks).forEach((t) => { if (!t.paused) t.pause(); }); }

    /* ============ ENTRÉES ============ */
    const K = { left: false, right: false, down: false };
    let pJump = false, pDash = false, pE = false, pAtk = false, holdJump = false, pAny = false;
    function typing() {
        const tag = document.activeElement && document.activeElement.tagName;
        return tag === "INPUT" || tag === "TEXTAREA";
    }
    window.addEventListener("keydown", (e) => {
        if (!V.on || V.paused || typing()) return;
        const c = e.code, k = (e.key || "").toLowerCase();
        let used = true;
        if (c === "KeyA" || c === "ArrowLeft" || k === "q") K.left = true;
        else if (c === "KeyD" || c === "ArrowRight" || k === "d") K.right = true;
        else if (c === "KeyS" || c === "ArrowDown") K.down = true;
        else if (c === "KeyW" || c === "ArrowUp" || c === "Space" || k === "z") { if (!e.repeat) pJump = true; holdJump = true; }
        else if (c === "KeyX") { if (!e.repeat) pDash = true; }
        else if (c === "KeyE") { if (!e.repeat) pE = true; }
        else if (c === "KeyF") { if (!e.repeat) pAtk = true; }
        else if (c === "Digit1" || c === "Numpad1") { if (choiceCb) pickChoice(0); else if (V.popButtons && V.popButtons[0] && popEl.classList.contains("show")) { const b = V.popButtons[0]; closePop(); b.act(); } }
        else if (c === "Digit2" || c === "Numpad2") { if (choiceCb) pickChoice(1); else if (V.popButtons && V.popButtons[1] && popEl.classList.contains("show")) { const b = V.popButtons[1]; closePop(); b.act(); } }
        else if (c === "Escape") { if (popEl.classList.contains("show")) closePop(); }
        else if (c === "Enter") { /* sert juste de "touche quelconque" */ }
        else used = false;
        if (!e.repeat) pAny = true;
        if (used) e.preventDefault();
    });
    window.addEventListener("keyup", (e) => {
        const c = e.code, k = (e.key || "").toLowerCase();
        if (c === "KeyA" || c === "ArrowLeft" || k === "q") K.left = false;
        if (c === "KeyD" || c === "ArrowRight" || k === "d") K.right = false;
        if (c === "KeyS" || c === "ArrowDown") K.down = false;
        if (c === "KeyW" || c === "ArrowUp" || c === "Space" || k === "z") holdJump = false;
    });
    window.addEventListener("blur", () => { K.left = K.right = K.down = false; holdJump = false; });
    canvas.addEventListener("pointerdown", (e) => {
        e.preventDefault();
        pAny = true;
        if (e.button === 0 || e.pointerType !== "mouse") {
            if (P.sword) pAtk = true;
            V.tap = true;
        }
    });
    touchEl.querySelectorAll("button").forEach((btn) => {
        const k = btn.dataset.k;
        const down = (e) => {
            e.preventDefault();
            btn.classList.add("on");
            pAny = true;
            if (k === "left") K.left = true;
            else if (k === "right") K.right = true;
            else if (k === "jump") { pJump = true; holdJump = true; }
            else if (k === "dash") pDash = true;
            else if (k === "e") { pE = true; V.tap = true; }
            else if (k === "atk") pAtk = true;
        };
        const up = (e) => {
            btn.classList.remove("on");
            if (k === "left") K.left = false;
            else if (k === "right") K.right = false;
            else if (k === "jump") holdJump = false;
        };
        btn.addEventListener("pointerdown", down);
        btn.addEventListener("pointerup", up);
        btn.addEventListener("pointercancel", up);
        btn.addEventListener("pointerleave", up);
    });
    const atkBtn = touchEl.querySelector('[data-k="atk"]');
    const dashBtn = touchEl.querySelector('[data-k="dash"]');
    backBtn.addEventListener("click", () => {
        if (V.mode === "credits") return;
        exitToMenu();
    });

    /* ============ SPRITES DE LA TROUSSE ============ */
    const invCache = {};
    // Même skin que le joueur mais couleurs inversées (1re rencontre du mode histoire)
    function invertedSprite() {
        const id = S().equippedSkin;
        if (invCache[id]) return invCache[id];
        const off = document.createElement("canvas");
        off.width = off.height = 160;
        const oc = off.getContext("2d");
        B.paintTrousse(oc, 80, 80, 156, 0);
        try {
            const img = oc.getImageData(0, 0, 160, 160);
            const d = img.data;
            for (let i = 0; i < d.length; i += 4) { d[i] = 255 - d[i]; d[i + 1] = 255 - d[i + 1]; d[i + 2] = 255 - d[i + 2]; }
            oc.putImageData(img, 0, 0);
        } catch (e) { /* canvas "tainted" (fichier ouvert en local) : on garde tel quel */ }
        if (B.trousseImg.complete) invCache[id] = off;
        return off;
    }
    // variant : "player" (skin équipé), "inverted", "base" (skin de base)
    function drawTrousseV(c, x, y, size, rot, dir, variant) {
        c.save();
        c.translate(x, y);
        if (dir < 0) c.scale(-1, 1);
        if (variant === "inverted") {
            c.rotate(rot || 0);
            c.drawImage(invertedSprite(), -size / 2, -size / 2, size, size);
        } else if (variant === "base") {
            c.rotate(rot || 0);
            if (B.trousseImg.complete) c.drawImage(B.trousseImg, -size / 2, -size / 2, size, size);
        } else {
            B.paintTrousse(c, 0, 0, size, rot || 0);
        }
        c.restore();
    }
    // Épée tenue sur le côté de la trousse. angle : 0 = lame vers le haut.
    function drawSword(c, x, y, dir, angle, s) {
        s = s || 1;
        c.save();
        c.translate(x, y);
        c.scale(dir * s, s);
        c.rotate(angle);
        c.fillStyle = "#6b3f1f"; c.fillRect(-3, -2, 6, 14);
        c.fillStyle = "#ffd23f"; c.fillRect(-11, -5, 22, 5); c.fillRect(-3, 11, 6, 4);
        const g = c.createLinearGradient(-4, 0, 4, 0);
        g.addColorStop(0, "#9aa3b3"); g.addColorStop(0.5, "#f4f7fb"); g.addColorStop(1, "#8b94a4");
        c.fillStyle = g;
        c.beginPath(); c.moveTo(-4, -5); c.lineTo(-4, -60); c.lineTo(0, -70); c.lineTo(4, -60); c.lineTo(4, -5); c.closePath(); c.fill();
        c.fillStyle = "rgba(120,255,170,0.55)"; c.fillRect(-1, -58, 2, 50);
        c.restore();
    }

    /* ============ BULLES, INVITES, OBSCURITÉ ============ */
    function wrapLines(c, text, maxW) {
        const words = String(text).split(" ");
        const lines = [];
        let line = "";
        words.forEach((w) => {
            const t = line ? line + " " + w : w;
            if (c.measureText(t).width > maxW && line) { lines.push(line); line = w; } else line = t;
        });
        if (line) lines.push(line);
        return lines;
    }
    function bubble(c, x, y, text, o) {
        o = o || {};
        const size = o.big ? 30 : 16;
        c.save();
        c.font = "800 " + size + "px 'Segoe UI', Arial, sans-serif";
        const lines = wrapLines(c, text, o.big ? 520 : 250);
        const lh = size * 1.3;
        const w = Math.max(...lines.map((l) => c.measureText(l).width)) + 24;
        const h = lines.length * lh + 16;
        let bx = clamp(x - w / 2, 8, V.VW - w - 8);
        const by = y - h - 14;
        c.fillStyle = o.dark ? "rgba(10,10,16,0.92)" : "rgba(255,255,255,0.96)";
        c.strokeStyle = o.dark ? "#b3202a" : "rgba(0,0,0,0.75)";
        c.lineWidth = 2.5;
        rr(c, bx, by, w, h, 12); c.fill(); c.stroke();
        c.beginPath(); c.moveTo(x - 8, by + h - 1); c.lineTo(x, by + h + 12); c.lineTo(x + 8, by + h - 1); c.closePath(); c.fill();
        c.fillStyle = o.dark ? "#ff5a5a" : "#161a24";
        c.textAlign = "center";
        c.textBaseline = "top";
        lines.forEach((l, i) => c.fillText(l, bx + w / 2, by + 8 + i * lh));
        c.restore();
    }
    function promptPill(c, x, y, label) {
        c.save();
        c.font = "800 14px 'Segoe UI', Arial, sans-serif";
        const w = c.measureText(label).width + 50;
        const bx = clamp(x - w / 2, 6, V.VW - w - 6), by = y - 30;
        const pulse = 0.85 + 0.15 * Math.sin(V.t * 5);
        c.globalAlpha = pulse;
        c.fillStyle = "rgba(20,24,36,0.9)";
        c.strokeStyle = "rgba(255,210,63,0.9)"; c.lineWidth = 2;
        rr(c, bx, by, w, 30, 15); c.fill(); c.stroke();
        c.fillStyle = "#ffd23f";
        rr(c, bx + 6, by + 5, 22, 20, 5); c.fill();
        c.fillStyle = "#161a24"; c.textAlign = "center"; c.textBaseline = "middle";
        c.fillText("E", bx + 17, by + 15.5);
        c.fillStyle = "#fff"; c.textAlign = "left";
        c.fillText(label, bx + 36, by + 15.5);
        c.restore();
    }
    // Obscurité avec des "trous" de lumière (pièce noire, coupure de courant)
    const darkCanvas = document.createElement("canvas");
    function darkness(c, alpha, holes) {
        if (darkCanvas.width !== canvas.width || darkCanvas.height !== canvas.height) {
            darkCanvas.width = canvas.width; darkCanvas.height = canvas.height;
        }
        const dc = darkCanvas.getContext("2d");
        const k = V.sc * V.dpr;
        dc.setTransform(1, 0, 0, 1, 0, 0);
        dc.clearRect(0, 0, darkCanvas.width, darkCanvas.height);
        dc.globalCompositeOperation = "source-over";
        dc.fillStyle = "rgba(2,3,8," + alpha + ")";
        dc.fillRect(0, 0, darkCanvas.width, darkCanvas.height);
        dc.globalCompositeOperation = "destination-out";
        holes.forEach((hl) => {
            const g = dc.createRadialGradient(hl.x * k, hl.y * k, 0, hl.x * k, hl.y * k, hl.r * k);
            g.addColorStop(0, "rgba(0,0,0," + (hl.a == null ? 1 : hl.a) + ")");
            g.addColorStop(0.6, "rgba(0,0,0," + (hl.a == null ? 0.7 : hl.a * 0.7) + ")");
            g.addColorStop(1, "rgba(0,0,0,0)");
            dc.fillStyle = g;
            dc.beginPath(); dc.arc(hl.x * k, hl.y * k, hl.r * k, 0, Math.PI * 2); dc.fill();
        });
        dc.globalCompositeOperation = "source-over";
        c.save();
        c.setTransform(1, 0, 0, 1, 0, 0);
        c.drawImage(darkCanvas, 0, 0);
        c.restore();
    }
    // Effet "glitch" : bandes décalées, barres de couleur, grain
    function glitchFX(amount) {
        if (amount <= 0.01) return;
        const cw = canvas.width, ch = canvas.height;
        ctx.save();
        ctx.setTransform(1, 0, 0, 1, 0, 0);
        const n = Math.floor(3 + amount * 16);
        for (let i = 0; i < n; i++) {
            const sh = Math.random() * ch * 0.07 * amount + 2;
            const sy = Math.random() * ch;
            const off = (Math.random() - 0.5) * cw * 0.14 * amount;
            ctx.drawImage(canvas, 0, sy, cw, sh, off, sy, cw, sh);
        }
        ctx.globalCompositeOperation = "lighter";
        const cols = ["rgba(255,0,90,0.22)", "rgba(0,255,200,0.2)", "rgba(90,90,255,0.24)"];
        for (let j = 0; j < amount * 8; j++) {
            ctx.fillStyle = cols[j % 3];
            ctx.fillRect(Math.random() * cw, Math.random() * ch, Math.random() * cw * 0.5, Math.random() * ch * 0.025 + 2);
        }
        ctx.globalCompositeOperation = "source-over";
        ctx.fillStyle = "rgba(255,255,255," + 0.05 * amount + ")";
        for (let j = 0; j < 40 * amount; j++) ctx.fillRect(Math.random() * cw, Math.random() * ch, 3 * V.dpr, 3 * V.dpr);
        ctx.restore();
    }

    /* ============ PARTICULES ============ */
    function spawn(x, alt, n, o) {
        for (let i = 0; i < n; i++) {
            V.parts.push({
                x: x + (Math.random() - 0.5) * (o.spreadX || 10), alt: alt + (Math.random() - 0.5) * (o.spreadY || 10),
                vx: (Math.random() - 0.5) * (o.vx || 100), va: (o.up || 60) * (0.4 + Math.random()),
                life: o.life * (0.6 + Math.random() * 0.6), max: o.life, color: o.color || "255,255,255",
                size: o.size || 4, g: o.g == null ? 300 : o.g, grow: o.grow || 0, square: !!o.square,
            });
        }
        if (V.parts.length > 500) V.parts.splice(0, V.parts.length - 500);
    }
    function updateParts(dt) {
        V.parts.forEach((p) => { p.x += p.vx * dt; p.alt += p.va * dt; p.va -= p.g * dt; p.life -= dt; p.size += p.grow * dt; });
        V.parts = V.parts.filter((p) => p.life > 0);
    }
    function drawParts(c) {
        V.parts.forEach((p) => {
            c.fillStyle = "rgba(" + p.color + "," + c01(p.life / p.max) + ")";
            const x = p.x - V.camX, y = V.GY - p.alt;
            if (p.square) c.fillRect(x - p.size / 2, y - p.size / 2, p.size, p.size);
            else { c.beginPath(); c.arc(x, y, Math.max(0.5, p.size), 0, Math.PI * 2); c.fill(); }
        });
    }

    /* ============ COROUTINES (séquences scriptées) ============
       Une séquence est un générateur : "yield 1.5" attend 1,5 s, "yield () => cond"
       attend que la condition soit vraie. Une seule séquence à la fois. */
    let co = null, coWait = 0, coCond = null;
    function run(gen) { co = gen; coWait = 0; coCond = null; stepCo(); }
    function stopCo() { co = null; coCond = null; coWait = 0; }
    function stepCo() {
        let guard = 0;
        while (co && guard++ < 200) {
            if (coWait > 0) return;
            if (coCond) { if (!coCond()) return; coCond = null; }
            const r = co.next();
            if (r.done) { co = null; return; }
            if (typeof r.value === "number") coWait = r.value;
            else if (typeof r.value === "function") coCond = r.value;
        }
    }
    function tickCo(dt) {
        if (!co) return;
        if (coWait > 0) { coWait -= dt; if (coWait > 0) return; coWait = 0; }
        stepCo();
    }
    const busy = () => !!co;

    /* ============ TRANSITIONS ============ */
    let trans = null;
    const MID = { iris: 0.5, fade: 0.5, glitch: 0.86, blur: 0.62, irisHold: 1, glitchQuick: 0.5 };
    function startTrans(kind, dur, mid, center) {
        trans = { kind, dur, t: 0, mid, midDone: false, center };
    }
    function updateTrans(dt) {
        if (!trans) return;
        trans.t += dt;
        if (!trans.midDone && trans.t >= trans.dur * MID[trans.kind]) {
            trans.midDone = true;
            if (trans.mid) trans.mid();
        }
        if (trans && trans.t >= trans.dur && trans.kind !== "irisHold") {
            if (trans.kind === "blur") canvas.style.filter = "";
            trans = null;
        }
    }
    const transBusy = () => !!trans;
    function drawTrans(c) {
        if (!trans) return;
        const p = c01(trans.t / trans.dur);
        const w = V.VW, h = V.VH;
        c.save();
        c.setTransform(V.dpr * V.sc, 0, 0, V.dpr * V.sc, 0, 0);
        if (trans.kind === "iris" || trans.kind === "irisHold") {
            let k;
            if (trans.kind === "irisHold") k = 1 - eIO(p);
            else k = p < 0.5 ? 1 - eIO(p * 2) : eIO((p - 0.5) * 2);
            const cx = trans.center ? trans.center.x : w / 2, cy = trans.center ? trans.center.y : h / 2;
            const maxR = Math.hypot(Math.max(cx, w - cx), Math.max(cy, h - cy)) + 10;
            c.fillStyle = "#000";
            c.beginPath();
            c.rect(0, 0, w, h);
            c.arc(cx, cy, Math.max(0.001, maxR * k), 0, Math.PI * 2, true);
            c.fill();
        } else if (trans.kind === "fade") {
            c.fillStyle = "rgba(0,0,0," + (p < 0.5 ? p * 2 : (1 - p) * 2) + ")";
            c.fillRect(0, 0, w, h);
        } else if (trans.kind === "glitch" || trans.kind === "glitchQuick") {
            const m = MID[trans.kind];
            if (p < m) {
                c.restore();
                glitchFX(Math.min(1.4, (p / m) * 1.6));
                c.save();
                c.setTransform(V.dpr * V.sc, 0, 0, V.dpr * V.sc, 0, 0);
                c.fillStyle = "rgba(0,0,0," + c01((p / m - 0.6) / 0.4) + ")";
            } else {
                c.fillStyle = "rgba(0,0,0," + (1 - c01((p - m) / (1 - m))) + ")";
            }
            c.fillRect(0, 0, w, h);
        } else if (trans.kind === "blur") {
            canvas.style.filter = p < 0.62 ? "blur(" + (c01(p / 0.45) * 14) + "px)" : "";
            const a = p < 0.62 ? c01((p - 0.3) / 0.28) : 1 - c01((p - 0.7) / 0.3);
            c.fillStyle = "rgba(0,0,0," + a + ")";
            c.fillRect(0, 0, w, h);
        }
        c.restore();
    }

    /* ============ JOUEUR ============ */
    function resetPlayer(x, dir) {
        Object.assign(P, { x, alt: 0, vx: 0, va: 0, dir: dir || 1, ground: true, dashT: 0, rot: 0, scale: 1, alpha: 1, visible: true, frozen: false, bang: 0, say: "", sayT: 0, swing: 0, hurt: 0 });
    }
    function platforms() { return (V.level && V.level.plats) ? V.level.plats() : []; }
    function bounds() {
        const lv = V.level;
        let b = lv.bounds ? lv.bounds() : [30, lv.w - 30];
        return b;
    }
    function updatePlayer(dt) {
        const frozen = P.frozen || V.lock || transBusy();
        const move = frozen ? 0 : (K.right ? 1 : 0) - (K.left ? 1 : 0);
        if (move) P.dir = move;
        P.dashCd = Math.max(0, P.dashCd - dt);
        if (!frozen && pDash && P.dashCd <= 0 && V.allowDash) {
            P.dashT = DASH_T; P.dashCd = DASH_CD;
            S().villeDashes++;
            B.beep(520, 0.12, "sawtooth", 0.05);
        }
        if (P.dashT > 0) {
            P.dashT -= dt;
            P.vx = P.dir * DASH_V;
            P.va = Math.max(P.va, 0) * 0.5;
            if (Math.random() < 0.8) spawn(P.x - P.dir * 20, P.alt + 14, 1, { life: 0.3, color: "255,255,255", size: 5, g: 0, vx: 20, up: 5 });
        } else if (!P.autoWalk) {
            P.vx = lerp(P.vx, move * SPEED, Math.min(1, dt * 14));
        }
        P.coyote = P.ground ? 0.1 : Math.max(0, P.coyote - dt);
        if (!frozen && pJump && P.coyote > 0) {
            P.va = JUMP; P.ground = false; P.coyote = 0;
            S().villeJumps++;
        }
        if (P.dashT <= 0) {
            P.va -= GRAV * dt;
            if (!holdJump && P.va > 0) P.va -= GRAV * dt * 0.9; // saut plus court si on relâche
        }
        const prevAlt = P.alt, prevX = P.x;
        P.x += P.vx * dt;
        P.alt += P.va * dt;
        const [minX, maxX] = bounds();
        P.x = clamp(P.x, minX, maxX);
        P.ground = false;
        if (P.alt <= 0) { P.alt = 0; P.va = 0; P.ground = true; }
        else if (P.va <= 0 && !K.down) {
            for (const pl of platforms()) {
                if (prevAlt >= pl.alt - 1 && P.alt <= pl.alt && Math.abs(P.x - pl.x) <= pl.w / 2 + 8) {
                    P.alt = pl.alt; P.va = 0; P.ground = true; break;
                }
            }
        }
        const moved = Math.abs(P.x - prevX);
        S().villeWalked += moved;
        P.walk += moved * 0.045;
        footsteps(P.ground && moved > 1.2 && P.visible && P.alpha > 0.2 && V.mode === "play");
        P.hurt = Math.max(0, P.hurt - dt);
        P.swing = Math.max(0, P.swing - dt);
        P.swingCd = Math.max(0, P.swingCd - dt);
        if (P.sayT > 0) { P.sayT -= dt; if (P.sayT <= 0) P.say = ""; }
    }
    function playerScreen() {
        return { x: P.x - V.camX, y: V.GY - P.alt - TS * P.scale * 0.19 };
    }
    function drawPlayer(c) {
        if (!P.visible || P.alpha <= 0.01) return;
        if (P.hurt > 0 && Math.floor(P.hurt * 14) % 2) return;
        const size = TS * P.scale;
        const moving = P.ground && Math.abs(P.vx) > 40;
        const bob = moving ? Math.abs(Math.sin(P.walk)) * 4 : 0;
        const tilt = P.ground ? Math.sin(P.walk) * 0.07 * Math.min(1, Math.abs(P.vx) / SPEED) : clamp(-P.va / 4000, -0.18, 0.18);
        const sx = P.x - V.camX, gy = V.GY - P.alt;
        const cy = gy - size * 0.19 - bob;
        c.save();
        c.globalAlpha = P.alpha * 0.3;
        c.fillStyle = "#000";
        c.beginPath(); c.ellipse(sx, gy + 1, size * 0.34, 4, 0, 0, Math.PI * 2); c.fill();
        c.globalAlpha = P.alpha;
        drawTrousseV(c, sx, cy, size, P.rot + tilt, P.dir, P.invert ? "inverted" : "player");
        if (P.sword) {
            let ang = 0.35;
            if (P.swing > 0) { const q = 1 - P.swing / 0.22; ang = -0.9 + q * 2.6; }
            drawSword(c, sx + P.dir * size * 0.4, cy + 4, P.dir, ang, 1);
            if (P.swing > 0) {
                c.strokeStyle = "rgba(200,255,220,0.5)"; c.lineWidth = 6;
                c.beginPath();
                const a0 = P.dir > 0 ? -Math.PI / 2 - 0.9 : -Math.PI / 2 + 0.9;
                c.arc(sx + P.dir * size * 0.4, cy + 4, 62, Math.min(a0, a0 + P.dir * 2.4), Math.max(a0, a0 + P.dir * 2.4));
                c.stroke();
            }
        }
        c.restore();
        if (P.bang > 0.01) {
            c.save();
            c.globalAlpha = P.bang;
            c.textAlign = "center";
            c.font = "900 54px 'Segoe UI', Arial, sans-serif";
            c.fillStyle = "#ffcf3f"; c.strokeStyle = "rgba(40,25,0,0.85)"; c.lineWidth = 5;
            c.strokeText("!", sx, cy - 42);
            c.fillText("!", sx, cy - 42);
            c.restore();
        }
    }

    /* ============ NIVEAUX ============ */
    const LV = {}; // rempli par ville-levels.js
    function setLevel(id, x, dir) {
        const lv = LV[id];
        if (!lv) return;
        V.level = lv;
        V.camLock = null;
        V.parts = [];
        V.npcs = [];
        resetPlayer(x, dir);
        if (lv.enter) lv.enter();
        snapCam();
        if (lv.story) { pauseWorldMusic(); }
        else { pauseA(aBoss); B.applyWorldMusic(); }
        backBtn.style.display = lv.story ? "none" : "";
    }
    function snapCam() { V.camX = camTarget(); }
    function camTarget() {
        const lv = V.level;
        if (V.camLock != null) return V.camLock;
        if (lv.w <= V.VW) return (lv.w - V.VW) / 2;
        return clamp(P.x - V.VW * 0.42, 0, lv.w - V.VW);
    }

    /* ============ BOUCLE ============ */
    function loop(now) {
        if (!V.on || V.paused) { V.raf = 0; return; }
        const dt = V.last ? Math.min(0.033, (now - V.last) / 1000) : 0.016;
        V.last = now;
        V.t += dt;
        V.dt = dt;
        try { update(dt); render(); } catch (err) { console.error("ville.js :", err); }
        pJump = pDash = pE = pAtk = pAny = false;
        V.tap = false;
        V.raf = requestAnimationFrame(loop);
    }
    function startLoop() {
        if (V.raf) return;
        V.last = 0;
        V.raf = requestAnimationFrame(loop);
    }
    function update(dt) {
        updateTrans(dt);
        tickCo(dt);
        if (msgTimer > 0) { msgTimer -= dt; if (msgTimer <= 0) hideMsg(); }
        V.shake = Math.max(V.story && V.story.quake ? 5 : 0, V.shake - dt * 30);
        V.glitch = Math.max(0, V.glitch - dt * 2.5);
        if (aBoss) aBoss.muted = !!S().musicMuted;
        if (V.mode === "play") {
            updatePlayer(dt);
            if (V.level.update) V.level.update(dt);
            // interactions : l'objet le plus proche dont l'invite est visible
            V.near = null;
            if (!P.frozen && !V.lock && !transBusy() && !busy()) {
                let best = Infinity;
                (V.level.interacts || []).forEach((it) => {
                    const d = Math.abs(P.x - it.x);
                    if (d <= (it.r || 70) && d < best && (!it.when || it.when())) { best = d; V.near = it; }
                });
                if (V.near && pE) V.near.act();
            }
            V.camX = lerp(V.camX, camTarget(), Math.min(1, dt * 8));
        } else if (V.mode === "arrival") updateArrival(dt);
        else if (V.mode === "stairs") updateStairs(dt);
        else if (V.mode === "credits") updateCredits(dt);
        updateParts(dt);
        updateHud();
    }
    function render() {
        const c = ctx;
        c.setTransform(1, 0, 0, 1, 0, 0);
        c.clearRect(0, 0, canvas.width, canvas.height);
        const sx = (Math.random() - 0.5) * V.shake * V.dpr, sy = (Math.random() - 0.5) * V.shake * V.dpr;
        c.setTransform(V.dpr * V.sc, 0, 0, V.dpr * V.sc, sx, sy);
        if (V.mode === "play") {
            const lv = V.level;
            lv.draw(c);
            if (lv.drawMid) lv.drawMid(c);
            V.npcs.forEach((n) => drawNpc(c, n));
            drawPlayer(c);
            drawParts(c);
            if (lv.front) lv.front(c);
            if (lv.overlay) lv.overlay(c);
            if (V.near && !P.frozen) {
                const lbl = typeof V.near.label === "function" ? V.near.label() : V.near.label;
                promptPill(c, V.near.x - V.camX, V.GY - (V.near.pa || 150), lbl);
            }
            V.npcs.forEach((n) => { if (n.say) bubble(c, n.x - V.camX, V.GY - n.alt - (n.size || TS) * 0.45, n.say, { big: n.big, dark: n.dark }); });
            if (P.say) { const ps = playerScreen(); bubble(c, ps.x, ps.y - TS * 0.35, P.say); }
            if (lv.hud) lv.hud(c);
            drawDashHud(c);
        } else if (V.mode === "arrival") drawArrival(c);
        else if (V.mode === "stairs") drawStairs(c);
        else if (V.mode === "credits") { c.fillStyle = "#000"; c.fillRect(0, 0, V.VW, V.VH); }
        glitchFX(V.glitch);
        drawTrans(c);
    }
    function drawNpc(c, n) {
        if (n.alpha <= 0.01) return;
        const size = n.size || TS;
        c.save();
        c.globalAlpha = n.alpha == null ? 1 : n.alpha;
        if (n.glow) { c.shadowColor = n.glow; c.shadowBlur = 26; }
        const x = n.x - V.camX, y = V.GY - n.alt - size * 0.19;
        drawTrousseV(c, x, y, size, n.rot || 0, n.dir || -1, n.variant);
        if (n.flash > 0) {
            c.globalCompositeOperation = "lighter";
            c.globalAlpha = n.flash;
            drawTrousseV(c, x, y, size, n.rot || 0, n.dir || -1, n.variant);
        }
        c.restore();
    }
    function drawDashHud(c) {
        if (!V.allowDash) return;
        const ready = P.dashCd <= 0;
        if (dashBtn) dashBtn.classList.toggle("cd", !ready);
        if (isTouch) return;
        const x = 26, y = V.VH - 26;
        c.save();
        c.fillStyle = "rgba(20,24,36,0.7)";
        c.beginPath(); c.arc(x, y, 16, 0, Math.PI * 2); c.fill();
        c.strokeStyle = ready ? "#ffd23f" : "rgba(255,210,63,0.9)";
        c.lineWidth = 3;
        c.beginPath(); c.arc(x, y, 16, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (1 - P.dashCd / DASH_CD)); c.stroke();
        c.font = "14px sans-serif"; c.textAlign = "center"; c.textBaseline = "middle";
        c.globalAlpha = ready ? 1 : 0.45;
        c.fillText("⚡", x, y + 1);
        c.globalAlpha = 1;
        c.fillStyle = "rgba(255,255,255,0.75)"; c.font = "700 11px 'Segoe UI', Arial"; c.textAlign = "left";
        c.fillText(L("X : dash", "X: dash"), x + 24, y + 1);
        c.restore();
    }
    let lastMoney = null, lastEvText = null;
    function updateHud() {
        if (S().money !== lastMoney) { lastMoney = S().money; moneyEl.textContent = lastMoney; }
        const ev = activeEvent();
        const txt = ev && V.mode !== "credits" ? EVENTS[ev.type].emoji + " " + EVENTS[ev.type].name[S().lang === "en" ? 1 : 0] + " · " + L("jusqu'à ", "until ") + fmtTime(ev.end) : "";
        if (txt !== lastEvText) {
            lastEvText = txt;
            eventEl.textContent = txt;
            eventEl.style.display = txt ? "" : "none";
        }
        if (atkBtn) atkBtn.classList.toggle("hide", !P.sword);
    }

    /* ============ CINÉMATIQUE D'ARRIVÉE (nuages + descente vers l'aéroport) ============ */
    const AR = { t: 0, CLOUDS: 2.3, DESCENT: 7.2, irisStarted: false };
    function startArrival() {
        showRoot();
        B.endPlaneCrashScreen();
        resize();
        V.mode = "arrival";
        AR.t = 0; AR.irisStarted = false;
        V.parts = [];
        startLoop();
    }
    function updateArrival(dt) {
        AR.t += dt;
        if (!AR.irisStarted && AR.t >= AR.CLOUDS + AR.DESCENT) {
            AR.irisStarted = true;
            startTrans("iris", 2, () => arriveInCity(true));
        }
    }
    function drawArrival(c) {
        const w = V.VW, h = V.VH, t = AR.t;
        const sky = A.skyAt();
        const dp = eIO(c01((t - AR.CLOUDS) / AR.DESCENT));
        const horizon = lerp(h * 1.25, h * 0.6, dp);
        const scroll = t * 700;
        A.drawSky(c, w, h, horizon, sky, { camX: scroll * 0.2 });
        const lit = isOutage() ? 0 : sky.night;
        if (horizon < h + 40) {
            A.drawSkyline(c, w, horizon, scroll, 0.05, 71, { color: A.hazeColor(sky, 0.75), lit: lit * 0.8, minH: 40, maxH: 150, bw: 60 });
            // l'aéroport (tour de contrôle + piste) se rapproche à la fin
            const ax = w * 1.3 - dp * w * 0.75;
            c.fillStyle = A.hazeColor(sky, 0.55);
            c.fillRect(ax, horizon - 120, 16, 120);
            c.fillRect(ax - 16, horizon - 142, 48, 26);
            c.fillStyle = "rgba(160,220,255,0.7)"; c.fillRect(ax - 12, horizon - 138, 40, 12);
            A.drawSkyline(c, w, horizon + (h - horizon) * 0.25, scroll, 0.22, 83, { color: A.hazeColor(sky, 0.5), lit, minH: 80, maxH: 260, bw: 110, dayWindows: sky.night < 0.5 ? "rgba(255,255,255,0.12)" : null });
            c.fillStyle = A.hazeColor(sky, 0.32);
            c.fillRect(0, horizon + (h - horizon) * 0.25, w, h);
            // piste avec balisage lumineux
            c.fillStyle = "#3a3d44";
            c.fillRect(ax - 400, horizon + (h - horizon) * 0.25 - 6, 900, 10);
            for (let i = 0; i < 18; i++) {
                c.fillStyle = Math.floor(t * 4 + i) % 6 === 0 ? "#fff" : "#ffcf6a";
                c.fillRect(ax - 400 + i * 50, horizon + (h - horizon) * 0.25 - 3, 5, 3);
            }
        }
        // autres avions qui vont dans l'autre sens
        for (let i = 0; i < 3 && t > AR.CLOUDS; i++) {
            const span = w + 700;
            const px = ((w + 300 - (t * (330 + i * 90) + scroll * 0.25) - i * 420) % span + span) % span - 300;
            const py = horizon - 170 - i * 70;
            if (py > h) continue;
            A.drawAirliner(c, px, py, 0.16 + i * 0.04, { dir: -1, silhouette: A.hazeColor(sky, 0.85 - i * 0.1), lights: sky.night > 0.4 });
        }
        // notre avion, avec la trousse encastrée sur le flanc
        const bob = Math.sin(t * 1.3) * 6;
        const px = w * 0.42, py = lerp(h * 0.34, h * 0.44, dp) + bob;
        const rot = t > AR.CLOUDS ? 0.05 * Math.sin(Math.PI * dp) : 0;
        const s = 0.72;
        A.drawAirliner(c, px, py, s, { dir: 1, rot, lights: true });
        c.save();
        c.translate(px, py); c.rotate(rot);
        drawTrousseV(c, 70 * s, -2, 60 * s / 0.9, -0.1 + Math.sin(t * 9) * 0.02, 1, "player");
        c.restore();
        // buildings au premier plan, DEVANT l'avion et la trousse
        if (horizon < h * 1.1) {
            const fs = scroll * 1.6;
            for (let i = Math.floor(fs / 520) - 1; i <= Math.floor((fs + w) / 520) + 1; i++) {
                const bx = i * 520 + sr(i * 3.1) * 200 - fs;
                const bw = 150 + sr(i * 7.7) * 120;
                const bh = (h - horizon) * 0.9 + sr(i * 5.5) * h * 0.35;
                const top = h - bh * c01(dp * 1.4);
                c.fillStyle = A.hazeColor(sky, 0.22);
                c.fillRect(bx, top, bw, h - top + 10);
                for (let r = 0; r * 22 < h - top; r++) {
                    for (let k = 0; k * 20 < bw - 14; k++) {
                        const q = sr(i * 13 + r * 3.7 + k * 1.9);
                        if (lit > 0.1 && q < lit * 0.45) { c.fillStyle = "rgba(255,210,130,0.9)"; c.fillRect(bx + 10 + k * 20, top + 12 + r * 22, 9, 12); }
                        else if (lit <= 0.5 && q < 0.55) { c.fillStyle = "rgba(170,210,240,0.25)"; c.fillRect(bx + 10 + k * 20, top + 12 + r * 22, 9, 12); }
                    }
                }
            }
        }
        // traversée des nuages : ils défilent de droite à gauche au premier plan
        if (t < AR.CLOUDS + 0.4) {
            const fade = c01((AR.CLOUDS + 0.4 - t) / 0.7);
            c.save();
            c.globalAlpha = fade;
            c.fillStyle = sky.night > 0.5 ? "#cfd5e6" : "#eef3f8";
            for (let k = 0; k < 16; k++) {
                const cx = w + 200 + k * 260 - t * 1250 + sr(k * 4.1) * 100;
                const cy = h * (0.15 + sr(k * 2.7) * 0.75);
                A.cloudPuff(c, cx, cy, 110 + sr(k * 8.3) * 90);
            }
            c.restore();
            if (t < 0.55) { c.fillStyle = "rgba(238,243,248," + (1 - t / 0.55) + ")"; c.fillRect(0, 0, w, h); }
        }
    }
    // Fin de la cinématique (ou vol depuis le menu) : on pose la trousse dans l'aéroport
    function arriveInCity(first) {
        const s = S();
        const wasUnlocked = s.villeUnlocked;
        s.villeUnlocked = true;
        s.inVille = true;
        s.inPlage = false;
        s.currentWorld = "ville";
        if (!s.villeArrivedAt) s.villeArrivedAt = Date.now();
        const w = B.WORLDS.find((x) => x.id === "ville");
        if (w) w.unlocked = true;
        B.persist();
        B.applyWorldTheme();
        V.mode = "play";
        setLevel("city", 520, 1);
        if (!wasUnlocked) {
            showMsg(L("✈️ Bienvenue en Ville ! Q/D ou ←/→ pour bouger, Z/Espace pour sauter, X pour dasher.", "✈️ Welcome to the City! A/D or ←/→ to move, W/Space to jump, X to dash."), 7);
        } else if (first) {
            showMsg(L("✈️ Atterrissage... mouvementé.", "✈️ A... bumpy landing."), 3);
        }
        maybeVilleTutorial();
    }

    // Tutoriel du monde Ville (VILLE_TUTORIAL dans index.html) : une seule
    // fois, à la première entrée dans la Ville — y compris pour ceux qui y
    // étaient déjà avant qu'il existe. La Ville reste figée tant qu'il s'affiche.
    function maybeVilleTutorial() {
        if (S().villeTutorialSeen || !B.startVilleTutorial) return;
        V.lock = true;
        B.startVilleTutorial(() => { V.lock = false; });
    }

    /* ============ ESCALIERS (coupure de courant) : "Gère ton souffle" x7 ============ */
    const ST = { t: 0, rings: [], ringT: 0, target: 40, dest: "", pause: 0, climb: 0, done: false };
    const ST_RING_DUR = 1.15, ST_RING_START = 120, ST_COUNT = 7, ST_TOL = 14;
    function startStairs(dest) {
        V.mode = "stairs";
        Object.assign(ST, { t: 0, rings: [], ringT: -0.6, target: pickSt(), dest, pause: 0, climb: 0, done: false });
        S().villeStairs++;
        footsteps(false);
        showMsg(L("😮‍💨 Gère ton souffle ! Appuie (E / Espace / clic) quand les cercles se superposent.", "😮‍💨 Mind your breath! Press (E / Space / click) when the circles overlap."), 4);
    }
    function pickSt() { return [34, 48, 62][Math.floor(Math.random() * 3)]; }
    function stRadius() { return ST_RING_START * (1 - c01(ST.ringT / ST_RING_DUR)); }
    function updateStairs(dt) {
        ST.t += dt;
        if (ST.done) return;
        ST.pause = Math.max(0, ST.pause - dt);
        ST.climb += dt * (ST.pause > 0 ? 0.2 : 1);
        ST.ringT += dt;
        if (ST.ringT < 0) return;
        const press = pE || pJump || V.tap;
        if (press) resolveRing(Math.abs(stRadius() - ST.target) <= ST_TOL);
        else if (ST.ringT >= ST_RING_DUR) resolveRing(false);
    }
    function resolveRing(hit) {
        ST.rings.push(hit);
        if (hit) B.sfxCharge(); else { B.sfxError(); ST.pause = 0.6; }
        ST.ringT = -0.35;
        ST.target = pickSt();
        if (ST.rings.length >= ST_COUNT) {
            ST.done = true;
            hideMsg();
            const hits = ST.rings.filter(Boolean).length;
            showMsg(hits === ST_COUNT ? L("Souffle parfait ! 💨", "Perfect breathing! 💨") : L("Ouf... arrivée !", "Phew... made it!"), 2);
            run((function* () {
                yield 0.7;
                startTrans("iris", 1, () => goFloor(ST.dest));
            })());
        }
    }
    function drawStairs(c) {
        const w = V.VW, h = V.VH;
        c.fillStyle = "#3c3f46"; c.fillRect(0, 0, w, h);
        const flightH = 170;
        const scroll = ST.climb * 95;
        const cx = w / 2;
        for (let f = -2; f < 8; f++) {
            const baseY = h * 0.78 - f * flightH + (scroll % (flightH * 2)) - flightH;
            const right = (f + Math.floor(scroll / (flightH * 2)) * 2) % 2 === 0;
            c.fillStyle = "#5a5e67";
            for (let s = 0; s < 10; s++) {
                const sx = right ? cx - 180 + s * 36 : cx + 180 - s * 36;
                const sy = baseY - s * (flightH / 10);
                c.fillRect(right ? sx : sx - 36, sy - flightH / 10, 36, flightH / 10 + 1);
            }
            c.strokeStyle = "#9aa0aa"; c.lineWidth = 4;
            c.beginPath(); c.moveTo(cx - 180, baseY - (right ? 40 : flightH + 40)); c.lineTo(cx + 180, baseY - (right ? flightH + 40 : 40)); c.stroke();
            c.fillStyle = "rgba(255,255,255,0.14)";
            c.font = "900 64px 'Segoe UI', Arial"; c.textAlign = "center";
            c.fillText(String(Math.max(1, f + 2 + Math.floor(scroll / flightH))), right ? cx - 280 : cx + 280, baseY - 60);
        }
        const bob = ST.pause > 0 ? 0 : Math.abs(Math.sin(ST.t * 9)) * 5;
        const dir = Math.floor(scroll / flightH) % 2 === 0 ? 1 : -1;
        drawTrousseV(c, cx + Math.sin(ST.t * 0.8) * 30, h * 0.62 - bob, TS * 1.2, dir * -0.3, dir, P.invert ? "inverted" : "player");
        if (ST.pause > 0) bubble(c, cx, h * 0.52, "pfff...");
        // QTE
        if (!ST.done && ST.ringT >= 0) {
            const qx = w / 2, qy = h * 0.27;
            c.save();
            c.strokeStyle = "rgba(107,255,176,0.9)"; c.lineWidth = 4;
            c.beginPath(); c.arc(qx, qy, ST.target, 0, Math.PI * 2); c.stroke();
            c.strokeStyle = "rgba(255,255,255,0.95)"; c.lineWidth = 5;
            c.beginPath(); c.arc(qx, qy, Math.max(2, stRadius()), 0, Math.PI * 2); c.stroke();
            c.restore();
        }
        c.save();
        c.textAlign = "center"; c.fillStyle = "#fff";
        c.font = "900 24px 'Segoe UI', Arial"; c.shadowColor = "rgba(0,0,0,0.8)"; c.shadowBlur = 8;
        c.fillText(L("GÈRE TON SOUFFLE", "MIND YOUR BREATH"), w / 2, h * 0.08 + 20);
        c.restore();
        for (let i = 0; i < ST_COUNT; i++) {
            c.fillStyle = i < ST.rings.length ? (ST.rings[i] ? "#6bffb0" : "#ff6b6b") : "rgba(255,255,255,0.3)";
            c.beginPath(); c.arc(w / 2 - (ST_COUNT - 1) * 11 + i * 22, h * 0.08 + 44, 7, 0, Math.PI * 2); c.fill();
        }
    }

    /* ============ ÉTAGES / ASCENSEUR ============ */
    function openFloorPop(viaStairs) {
        openPop(L("Quel étage ?", "Which floor?"), "", [
            { label: L("1. Terrain de lancer", "1. Throwing field"), act: () => takeFloor("roof", viaStairs) },
            { label: L("2. Mode histoire", "2. Story mode"), act: () => takeFloor("story", viaStairs) },
        ]);
    }
    function takeFloor(dest, viaStairs) {
        if (V.level && V.level.goFloor) V.level.goFloor(dest, viaStairs);
    }
    function goFloor(dest) {
        if (dest === "roof") goRooftop();
        else { V.story = null; V.mode = "play"; setLevel("darkroom", 150, 1); }
    }
    function goRooftop() {
        V.mode = "play";
        hideRoot();
        footsteps(false);
        B.startRooftop();
    }

    /* ============ GÉNÉRIQUE ============ */
    const CR = { y: 0, speed: 34, ended: false, endT: 0, finalEl: null };
    function fmtDur(sec) {
        sec = Math.floor(sec || 0);
        const hh = Math.floor(sec / 3600), mm = Math.floor((sec % 3600) / 60), ss = sec % 60;
        return (hh ? hh + " h " : "") + mm + " min " + ss + " s";
    }
    function startCredits() {
        V.mode = "credits";
        trans = null;
        hideMsg();
        footsteps(false);
        const s = S();
        const yes = L("oui", "yes"), no = L("non", "no");
        const skinsOwned = B.SKINS.filter((k) => s.ownedSkins.includes(k.id)).length;
        const m = (px) => Math.round(px / TS * 0.6) + " m";
        const rows = [
            [L("Pseudo", "Nickname"), s.pseudo || "—"],
            [L("Temps de jeu", "Play time"), fmtDur(s.playTime)],
            [L("Lancers au total", "Total throws"), s.totalThrows],
            [L("Record — Cour d'école", "Record — Schoolyard"), s.bestDistance.toFixed(1) + " m"],
            [L("Record — Plage", "Record — Beach"), s.plageBestDistance.toFixed(1) + " m"],
            [L("Record — Toit de la Ville", "Record — City rooftop"), s.villeBestDistance.toFixed(1) + " m"],
            [L("Argent gagné depuis le début", "Money earned overall"), s.totalMoneyEarned + " $"],
            [L("Argent en poche", "Money in pocket"), s.money + " $"],
            [L("Argent offert à d'autres joueurs", "Money gifted to others"), (s.totalMoneyGifted || 0) + " $"],
            [L("Niveau de Puissance", "Power level"), s.puissanceLevel],
            [L("Niveau de Vitesse", "Speed level"), s.vitesseLevel],
            [L("Durabilité de la trousse", "Pencil case durability"), s.durability],
            [L("Skins possédés", "Skins owned"), skinsOwned + " / " + B.SKINS.length],
            [L("Traînées possédées", "Trails owned"), s.ownedTrails.length + " / " + B.TRAILS.length],
            [L("Succès débloqués", "Achievements unlocked"), s.unlockedAchievements.length + " / " + B.ACHIEVEMENTS.length],
            [L("Lancer parfait réussi", "Perfect throw landed"), s.hasPerfectThrow ? yes : no],
            [L("Envolée vers l'espace", "Launched into space"), s.hasTriggeredSpaceEgg ? yes : no],
            [L("QTE spatial parfait", "Perfect space QTE"), s.hasPerfectQte ? yes : no],
            [L("💩 trouvé", "💩 found"), s.hasFoundPoopEgg ? yes : no],
            [L("Monde Volcan débloqué", "Volcano world unlocked"), s.volcanUnlocked ? yes : no],
            [L("Lancers à la plage", "Beach throws"), s.plageThrows],
            [L("Rebonds sur un parasol", "Parasol bounces"), s.plageParasolBounces],
            [L("Serviettes trouvées", "Towels found"), s.plageTowelsFound],
            [L("Châteaux de sable écroulés", "Sandcastles crushed"), s.plageCastlesCrushed],
            [L("Argent gagné à la plage", "Money earned at the beach"), s.plageMoneyEarned + " $"],
            [L("Billet de bus acheté", "Bus ticket bought"), s.hasTakenBusBack ? yes : no],
            [L("Lancers sur le toit de la Ville", "City rooftop throws"), s.villeThrows],
            [L("Argent gagné en Ville", "Money earned in the City"), s.villeMoneyEarned + " $"],
            [L("Distance parcourue à pied", "Distance walked"), m(s.villeWalked)],
            [L("Sauts", "Jumps"), s.villeJumps],
            [L("Dashs", "Dashes"), s.villeDashes],
            [L("Percuté au passage piéton", "Hit at the crosswalk"), s.villeCrosswalkDeaths + L(" fois", " times")],
            [L("Étages montés à pied", "Stair climbs"), s.villeStairs],
            [L("Trajets en ascenseur", "Elevator rides"), s.villeElevator],
            [L("Glissades sur une flaque", "Puddle slides"), s.villePuddles],
            [L("Tentatives contre le boss", "Boss attempts"), s.villeBossAttempts],
            [L("Cœurs perdus", "Hearts lost"), s.villeHeartsLost],
            [L("Coups d'épée donnés", "Sword hits landed"), s.villeSwordHits],
            [L("Mode histoire terminé", "Story mode completed"), (s.villeStoryRuns + 1) + L(" fois", " times")],
        ];
        const esc = (t) => String(t).replace(/[&<>"]/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[ch]));
        const intro = [
            L("oh ", "oh ") + pseudo() + L(" tu as fini mon jeu ?", ", you finished my game?"),
            L("je ne pensais pas que ça allait arriver un jour...", "I didn't think that would ever happen..."),
            L("ce jeu a vécu de nombreuses choses mais tu es visiblement allé au bout de ces choses,", "this game went through a lot of things, but you clearly made it to the end of them,"),
            L("tu y es parvenu avec l'aide d'amis peut-être, qui sait.....", "maybe with the help of friends, who knows....."),
        ];
        rollEl.innerHTML =
            intro.map((l) => "<p>" + esc(l) + "</p>").join("") +
            "<h4>" + L("STATISTIQUES", "STATISTICS") + "</h4>" +
            rows.map((r) => '<div class="vl-stat"><span>' + esc(r[0]) + "</span><span>" + esc(r[1]) + "</span></div>").join("") +
            '<p class="vl-final">' + L("Merci d'avoir joué à mon jeu, merci...", "Thank you for playing my game, thank you...") + "</p>";
        CR.finalEl = rollEl.querySelector(".vl-final");
        CR.y = root.clientHeight + 20;
        CR.ended = false; CR.endT = 0;
        rollEl.style.transform = "translate(-50%," + CR.y + "px)";
        contEl.textContent = L("Appuie n'importe où pour continuer", "Tap anywhere to continue");
        contEl.classList.remove("show");
        creditsEl.classList.add("show");
        backBtn.style.display = "none";
        if (aBoss && !aBoss.paused) aBoss.volume = 0.3;
    }
    function updateCredits(dt) {
        if (!CR.ended) {
            CR.y -= CR.speed * dt;
            const target = root.clientHeight / 2 - (CR.finalEl.offsetTop + CR.finalEl.offsetHeight / 2);
            if (CR.y <= target) { CR.y = target; CR.ended = true; }
            rollEl.style.transform = "translate(-50%," + CR.y + "px)";
        } else {
            CR.endT += dt;
            if (CR.endT > 2) contEl.classList.add("show");
            if (CR.endT > 2 && (pAny || V.tap || pE || pJump)) endCredits();
        }
    }
    creditsEl.addEventListener("pointerdown", () => { pAny = true; });
    function endCredits() {
        const s = S();
        s.villeStoryDone = true;
        s.villeStoryRuns++;
        if (!s.villeStoryDoneAt) s.villeStoryDoneAt = Date.now();
        B.persist();
        creditsEl.classList.remove("show");
        pauseA(aBoss);
        if (aBoss) aBoss.volume = 0.5;
        V.story = null;
        V.mode = "play";
        setLevel("reception", 680, 1);
        startTrans("iris", 1.4, null);
        trans.t = 0.7; // on ne fait que l'ouverture
        trans.midDone = true;
    }

    /* ============ AFFICHAGE / SORTIES ============ */
    function showRoot() {
        B.hideAllScreens();
        root.classList.add("on");
        document.body.classList.add("ville-active");
        V.on = true;
        V.paused = false;
        resize();
    }
    function hideRoot() {
        root.classList.remove("on");
        document.body.classList.remove("ville-active");
        V.on = false;
        footsteps(false);
        closePop();
        choicesEl.classList.remove("show");
        choiceCb = null;
        hideMsg();
        canvas.style.filter = "";
        K.left = K.right = K.down = false;
    }
    function exitToMenu() {
        stopCo();
        trans = null;
        pauseA(aBoss);
        V.story = null;
        hideRoot();
        B.applyWorldMusic();
        B.showScreen("menu");
        B.buildWorldsRow();
    }
    function enterHub(where) {
        showRoot();
        stopCo();
        V.mode = "play";
        creditsEl.classList.remove("show");
        if (where === "airport") setLevel("city", 520, 1);
        else if (where === "elevator") { setLevel("reception", 680, 1); if (LV.reception.arriveByElevator) LV.reception.arriveByElevator(); }
        else setLevel("reception", 190, 1);
        startTrans("iris", 1.2, null);
        trans.t = 0.6; trans.midDone = true;
        startLoop();
        maybeVilleTutorial();
    }
    function flyIn() {
        showRoot();
        V.mode = "play";
        arriveInCity(true);
        startTrans("iris", 1.6, null);
        trans.t = 0.8; trans.midDone = true;
        startLoop();
    }
    function leaveVille(dest) {
        const s = S();
        s.inVille = false;
        s.currentWorld = dest;
        if (dest === "plage") s.inPlage = true;
        B.persist();
        exitToMenu();
        B.applyWorldTheme();
        B.refreshMenu();
        B.showToast("✈️ " + L("Bon vol !", "Have a nice flight!"));
    }
    function resumeFromShop() {
        root.classList.add("on");
        document.body.classList.add("ville-active");
        V.paused = false;
        V.on = true;
        startLoop();
    }
    function pauseForShop() {
        V.paused = true;
        root.classList.remove("on");
        document.body.classList.remove("ville-active");
        footsteps(false);
        K.left = K.right = false;
    }

    /* ============ API ============ */
    window.VilleCore = {
        B, A, S, L, V, P, K, LV, TS, SPEED, EVENTS, eIO, eOut, eIn, pseudo,
        keys: () => ({ pE, pJump, pAtk, pAny, tap: V.tap, holdJump }),
        activeEvent, upcomingEvents, isOutage, fmtTime, fmtDay,
        showMsg, hideMsg, openPop, closePop, showChoices, bubble, promptPill, darkness, glitchFX,
        drawTrousseV, drawSword, spawn, run, stopCo, busy, startTrans, transBusy, setLevel, snapCam,
        resetPlayer, playerScreen, startStairs, openFloorPop, goFloor, goRooftop, startCredits,
        leaveVille, exitToMenu, pauseForShop, footsteps, playA, pauseA, pauseWorldMusic,
        aBoss, aTires, bounds, platforms,
        getTrans: () => trans,
    };
    window.VilleWorld = {
        activeEvent,
        forceEvent(type, minutes) {
            if (!EVENTS[type]) { console.warn("Types : " + EVENT_KEYS.join(", ")); return; }
            forcedEvent = { type, start: Date.now(), end: Date.now() + minutes * 60000, forced: true };
            B.showToast(EVENTS[type].emoji + " " + EVENTS[type].name[0] + " (" + minutes + " min)");
        },
        drawRooftop: A.drawRooftop,
        drawWeather: A.drawWeather,
        drawAirliner: A.drawAirliner,
        startArrival, enterHub, flyIn, resumeFromShop,
    };
})();
