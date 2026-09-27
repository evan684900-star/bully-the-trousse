/* Bully the Trousse — Monde Ville : dessins partagés (ciel à l'heure réelle,
   silhouettes de buildings, avion de ligne, toit-terrain de lancer, météo,
   voitures, crabes en uniforme, portail...). Aucun état de jeu ici : juste des
   fonctions de dessin, utilisées par ville.js ET par index.html (toit). */
(function () {
    "use strict";
    const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
    const c01 = (v) => clamp(v, 0, 1);
    const lerp = (a, b, t) => a + (b - a) * t;
    function sr(n) { const x = Math.sin(n * 12.9898) * 43758.5453; return x - Math.floor(x); }
    function rr(c, x, y, w, h, r) {
        r = Math.min(r, w / 2, h / 2);
        c.beginPath();
        c.moveTo(x + r, y);
        c.arcTo(x + w, y, x + w, y + h, r);
        c.arcTo(x + w, y + h, x, y + h, r);
        c.arcTo(x, y + h, x, y, r);
        c.arcTo(x, y, x + w, y, r);
        c.closePath();
    }

    /* ---- Ciel piloté par l'heure réelle de l'appareil ---- */
    // [heure, couleur haut, couleur bas, nuit (0 = plein jour, 1 = pleine nuit)]
    const SKY_KEYS = [
        [0, [10, 14, 34], [26, 32, 62], 1],
        [5, [14, 20, 48], [40, 44, 82], 1],
        [6.4, [64, 78, 140], [245, 165, 115], 0.5],
        [8, [88, 164, 228], [190, 226, 255], 0],
        [17.6, [86, 160, 222], [205, 231, 255], 0],
        [19.3, [78, 82, 156], [255, 150, 90], 0.35],
        [20.7, [30, 34, 82], [110, 62, 112], 0.8],
        [22, [10, 14, 34], [26, 32, 62], 1],
        [24, [10, 14, 34], [26, 32, 62], 1],
    ];
    let skyCache = null, skyCacheAt = 0;
    function skyAt() {
        const now = Date.now();
        if (skyCache && now - skyCacheAt < 1000) return skyCache;
        const d = new Date(now);
        const hr = d.getHours() + d.getMinutes() / 60 + d.getSeconds() / 3600;
        let i = 0;
        while (i < SKY_KEYS.length - 2 && SKY_KEYS[i + 1][0] <= hr) i++;
        const a = SKY_KEYS[i], b = SKY_KEYS[i + 1];
        const t = c01((hr - a[0]) / (b[0] - a[0]));
        const mixArr = (x, y) => x.map((v, k) => Math.round(lerp(v, y[k], t)));
        const top = mixArr(a[1], b[1]), bottom = mixArr(a[2], b[2]);
        skyCache = {
            top: "rgb(" + top + ")", bottom: "rgb(" + bottom + ")", topArr: top, bottomArr: bottom,
            night: lerp(a[3], b[3], t), hour: hr,
        };
        skyCacheAt = now;
        return skyCache;
    }
    // Couleur d'un bâtiment lointain : teinte du ciel assombrie (brume)
    function hazeColor(sky, k, alpha) {
        const m = sky.bottomArr.map((v, i) => Math.round(lerp(v, sky.topArr[i], 0.3) * k));
        return "rgba(" + m.join(",") + "," + (alpha == null ? 1 : alpha) + ")";
    }

    function drawSky(c, w, h, horizonY, sky, opts) {
        opts = opts || {};
        const g = c.createLinearGradient(0, 0, 0, Math.max(10, horizonY));
        g.addColorStop(0, sky.top);
        g.addColorStop(1, sky.bottom);
        c.fillStyle = g;
        c.fillRect(0, 0, w, h);
        const t = performance.now() / 1000;
        // étoiles
        if (sky.night > 0.15) {
            c.fillStyle = "#fff";
            for (let i = 0; i < 70; i++) {
                const x = sr(i * 3.1) * w, y = sr(i * 7.7) * horizonY * 0.9;
                c.globalAlpha = sky.night * (0.35 + 0.65 * Math.abs(Math.sin(t * (0.6 + sr(i) * 1.8) + i)));
                c.fillRect(x, y, 1.6, 1.6);
            }
            c.globalAlpha = 1;
        }
        // soleil (6 h -> 20 h) ou lune
        const hr = sky.hour;
        if (hr >= 5.8 && hr <= 20.4) {
            const p = (hr - 5.8) / 14.6;
            const x = w * (0.08 + 0.84 * p) - (opts.camX || 0) * 0.01;
            const y = horizonY - Math.sin(p * Math.PI) * horizonY * 0.78 + 20;
            const glow = c.createRadialGradient(x, y, 4, x, y, 90);
            glow.addColorStop(0, "rgba(255,240,190,0.55)");
            glow.addColorStop(1, "rgba(255,200,120,0)");
            c.fillStyle = glow;
            c.fillRect(x - 90, y - 90, 180, 180);
            c.fillStyle = p < 0.12 || p > 0.88 ? "#ffb070" : "#fff2c4";
            c.beginPath(); c.arc(x, y, 22, 0, Math.PI * 2); c.fill();
        }
        if (sky.night > 0.3) {
            const p = ((hr + 24 - 19.5) % 24) / 11;
            const x = w * (0.1 + 0.8 * c01(p));
            const y = horizonY - Math.sin(c01(p) * Math.PI) * horizonY * 0.7 + 30;
            c.globalAlpha = c01((sky.night - 0.3) / 0.4);
            c.fillStyle = "#f3f1e6";
            c.beginPath(); c.arc(x, y, 17, 0, Math.PI * 2); c.fill();
            c.fillStyle = sky.top;
            c.beginPath(); c.arc(x + 7, y - 4, 15, 0, Math.PI * 2); c.fill();
            c.globalAlpha = 1;
        }
        // quelques nuages qui dérivent
        if (!opts.noClouds) {
            c.fillStyle = sky.night > 0.5 ? "rgba(120,130,170,0.18)" : "rgba(255,255,255,0.55)";
            for (let i = 0; i < 5; i++) {
                const span = w + 400;
                const x = ((sr(i * 4.4) * span + t * (8 + i * 3) - (opts.camX || 0) * 0.03) % span + span) % span - 200;
                const y = horizonY * (0.12 + sr(i * 9.1) * 0.4);
                const s = 0.6 + sr(i * 2.2) * 0.8;
                cloudPuff(c, x, y, 60 * s);
            }
        }
    }
    function cloudPuff(c, x, y, r) {
        c.beginPath();
        c.arc(x, y, r, 0, Math.PI * 2);
        c.arc(x + r * 0.9, y + r * 0.15, r * 0.75, 0, Math.PI * 2);
        c.arc(x - r * 0.85, y + r * 0.2, r * 0.7, 0, Math.PI * 2);
        c.arc(x + r * 0.2, y - r * 0.45, r * 0.65, 0, Math.PI * 2);
        c.fill();
    }

    // Rangée de buildings en silhouette (parallaxe), fenêtres allumées la nuit
    function drawSkyline(c, w, baseY, camX, par, seed, o) {
        const bw = o.bw || 90;
        const off = camX * par;
        const i0 = Math.floor(off / bw) - 1, i1 = Math.floor((off + w) / bw) + 1;
        for (let i = i0; i <= i1; i++) {
            const x = i * bw - off;
            const r = sr(i * 1.7 + seed);
            const hgt = o.minH + r * (o.maxH - o.minH);
            const ww = bw * (0.72 + 0.28 * sr(i * 2.9 + seed));
            const top = baseY - hgt;
            c.fillStyle = o.color;
            c.fillRect(x, top, ww, hgt + (o.extra || 4));
            const kind = sr(i * 5.3 + seed);
            if (kind > 0.72) { c.fillRect(x + ww * 0.45, top - 26, 2, 26); }
            else if (kind > 0.55) { c.fillRect(x + ww * 0.2, top - 10, ww * 0.25, 10); }
            else if (kind < 0.12) { c.beginPath(); c.moveTo(x, top); c.lineTo(x + ww / 2, top - 22); c.lineTo(x + ww, top); c.fill(); }
            if (kind > 0.72 && o.lit > 0.2 && Math.floor(performance.now() / 700 + i) % 2) {
                c.fillStyle = "#ff4040";
                c.fillRect(x + ww * 0.45 - 1, top - 28, 4, 4);
            }
            if (o.lit > 0.05 || o.dayWindows) {
                const cols = Math.max(2, Math.floor((ww - 8) / 11));
                const rows = Math.floor((hgt - 12) / 15);
                for (let row = 0; row < rows; row++) {
                    for (let col = 0; col < cols; col++) {
                        const q = sr(i * 31.7 + row * 7.3 + col * 3.1 + seed);
                        if (o.lit > 0.05 && q < o.lit * 0.5) {
                            c.fillStyle = q < 0.08 ? "rgba(170,210,255,0.85)" : "rgba(255,214,130,0.85)";
                            c.fillRect(x + 5 + col * 11, top + 8 + row * 15, 5, 7);
                        } else if (o.dayWindows && q < 0.7) {
                            c.fillStyle = o.dayWindows;
                            c.fillRect(x + 5 + col * 11, top + 8 + row * 15, 5, 7);
                        }
                    }
                }
            }
        }
    }

    /* ---- Avion de ligne vu de profil (nez vers la droite, dir = -1 pour l'inverser) ---- */
    function drawAirliner(c, x, y, s, o) {
        o = o || {};
        const dir = o.dir || 1;
        const sil = o.silhouette;
        c.save();
        c.translate(x, y);
        if (o.rot) c.rotate(o.rot);
        c.scale(s * dir, s);
        const accent = sil || "#e8503f", wingC = sil || "#b9bfca", farWing = sil || "#98a0ad";
        // aile lointaine (derrière le fuselage)
        c.fillStyle = farWing;
        c.beginPath(); c.moveTo(30, -18); c.lineTo(-70, -78); c.lineTo(-40, -80); c.lineTo(84, -20); c.closePath(); c.fill();
        // dérive
        c.fillStyle = accent;
        c.beginPath(); c.moveTo(-200, -30); c.lineTo(-258, -130); c.lineTo(-218, -130); c.lineTo(-136, -30); c.closePath(); c.fill();
        // stabilisateur lointain
        c.fillStyle = farWing;
        c.beginPath(); c.moveTo(-212, -12); c.lineTo(-260, -42); c.lineTo(-238, -44); c.lineTo(-178, -14); c.closePath(); c.fill();
        // fuselage
        let fill = sil;
        if (!sil) {
            const g = c.createLinearGradient(0, -36, 0, 36);
            g.addColorStop(0, "#ffffff"); g.addColorStop(0.55, "#edf0f5"); g.addColorStop(1, "#bfc6d2");
            fill = g;
        }
        c.fillStyle = fill;
        c.beginPath();
        c.moveTo(-245, -34); c.lineTo(175, -34);
        c.bezierCurveTo(232, -34, 262, -14, 266, 6);
        c.bezierCurveTo(262, 26, 236, 34, 190, 34);
        c.lineTo(-215, 34);
        c.bezierCurveTo(-250, 30, -272, 6, -275, -14);
        c.bezierCurveTo(-270, -30, -258, -34, -245, -34);
        c.closePath();
        c.fill();
        if (!sil) {
            c.save();
            c.clip();
            c.fillStyle = accent; c.fillRect(-300, 11, 600, 7);
            c.fillStyle = "#2a3b6e"; c.fillRect(-300, 18, 600, 4);
            c.restore();
            c.fillStyle = "#3a4a66";
            for (let wx = -205; wx <= 150; wx += 15) { rr(c, wx, -16, 8, 10, 3); c.fill(); }
            c.fillStyle = "#26334d";
            c.beginPath(); c.moveTo(206, -21); c.lineTo(238, -17); c.lineTo(247, -8); c.lineTo(211, -9); c.closePath(); c.fill();
            c.strokeStyle = "rgba(80,90,110,0.55)"; c.lineWidth = 1.5;
            rr(c, 168, -24, 14, 38, 4); c.stroke();
            rr(c, -192, -24, 14, 38, 4); c.stroke();
            c.save();
            c.translate(-206, -74); c.rotate(-1.05);
            c.fillStyle = "#fff"; c.font = "900 17px 'Segoe UI', Arial, sans-serif";
            c.fillText("BULLY", -26, 6);
            c.restore();
        }
        // aile proche + réacteur
        c.fillStyle = wingC;
        c.beginPath(); c.moveTo(58, 8); c.lineTo(-85, 84); c.lineTo(-48, 88); c.lineTo(104, 16); c.closePath(); c.fill();
        c.fillStyle = sil || "#8d95a2";
        rr(c, -6, 34, 72, 24, 12); c.fill();
        if (!sil) {
            c.fillStyle = "#2b2f38";
            c.beginPath(); c.ellipse(64, 46, 5, 11, 0, 0, Math.PI * 2); c.fill();
            c.fillStyle = "rgba(255,255,255,0.35)"; c.fillRect(2, 37, 54, 3);
        }
        c.fillStyle = wingC;
        c.beginPath(); c.moveTo(-205, 4); c.lineTo(-262, 30); c.lineTo(-238, 32); c.lineTo(-170, 6); c.closePath(); c.fill();
        if (o.lights) {
            const on = Math.floor(performance.now() / 450) % 2;
            c.fillStyle = on ? "#ff3b3b" : "rgba(255,59,59,0.3)";
            c.beginPath(); c.arc(-68, 86, 5, 0, Math.PI * 2); c.fill();
            c.fillStyle = on ? "#ffffff" : "rgba(255,255,255,0.3)";
            c.beginPath(); c.arc(-240, -130, 4, 0, Math.PI * 2); c.fill();
        }
        c.restore();
    }

    /* ---- Toit-terrain de lancer (dessiné par index.html à la place du décor habituel) ---- */
    function drawRooftopProp(c, kind, x, gY, seed, night, outage) {
        c.save();
        switch (kind) {
            case "ac": {
                c.fillStyle = "#b9bec6"; rr(c, x - 40, gY - 46, 80, 46, 4); c.fill();
                c.fillStyle = "#9ea4ad"; c.fillRect(x - 40, gY - 8, 80, 8);
                c.strokeStyle = "#7d838c"; c.lineWidth = 2;
                for (let i = -30; i <= -8; i += 5) { c.beginPath(); c.moveTo(x + i, gY - 38); c.lineTo(x + i, gY - 14); c.stroke(); }
                c.fillStyle = "#6e747d"; c.beginPath(); c.arc(x + 16, gY - 26, 14, 0, Math.PI * 2); c.fill();
                c.save(); c.translate(x + 16, gY - 26); c.rotate(performance.now() / 120 * (outage ? 0 : 1));
                c.fillStyle = "#b9bec6";
                for (let k = 0; k < 3; k++) { c.rotate(Math.PI * 2 / 3); c.fillRect(-2, -12, 4, 12); }
                c.restore();
                break;
            }
            case "vent": {
                c.fillStyle = "#8f959e"; c.fillRect(x - 7, gY - 54, 14, 54);
                c.fillStyle = "#737982"; rr(c, x - 16, gY - 64, 32, 12, 4); c.fill();
                c.fillStyle = "#a6acb4"; c.fillRect(x + 22, gY - 30, 10, 30);
                c.fillStyle = "#737982"; c.fillRect(x + 19, gY - 34, 16, 6);
                break;
            }
            case "tank": {
                c.strokeStyle = "#4a3a2c"; c.lineWidth = 5;
                c.beginPath(); c.moveTo(x - 30, gY); c.lineTo(x - 24, gY - 70); c.moveTo(x + 30, gY); c.lineTo(x + 24, gY - 70);
                c.moveTo(x - 27, gY - 30); c.lineTo(x + 27, gY - 45); c.stroke();
                c.fillStyle = "#8a5a35"; rr(c, x - 38, gY - 146, 76, 80, 8); c.fill();
                c.fillStyle = "rgba(0,0,0,0.18)";
                for (let k = -30; k <= 30; k += 12) c.fillRect(x + k, gY - 146, 3, 80);
                c.fillStyle = "#5a3a22"; c.beginPath(); c.moveTo(x - 42, gY - 146); c.lineTo(x, gY - 172); c.lineTo(x + 42, gY - 146); c.fill();
                break;
            }
            case "antenna": {
                c.strokeStyle = "#8b9099"; c.lineWidth = 3;
                c.beginPath(); c.moveTo(x, gY); c.lineTo(x, gY - 150); c.stroke();
                c.lineWidth = 2;
                for (let k = 0; k < 4; k++) { c.beginPath(); c.moveTo(x - 16 + k * 3, gY - 60 - k * 22); c.lineTo(x + 16 - k * 3, gY - 60 - k * 22); c.stroke(); }
                c.fillStyle = outage ? "#552222" : (Math.floor(performance.now() / 600) % 2 && night > 0.2 ? "#ff3030" : "#aa2222");
                c.beginPath(); c.arc(x, gY - 152, 4, 0, Math.PI * 2); c.fill();
                break;
            }
            case "dish": {
                c.fillStyle = "#7f858e"; c.fillRect(x - 3, gY - 40, 6, 40);
                c.save(); c.translate(x, gY - 48); c.rotate(-0.5);
                c.fillStyle = "#dfe3e8"; c.beginPath(); c.ellipse(0, 0, 26, 10, 0, 0, Math.PI * 2); c.fill();
                c.strokeStyle = "#7f858e"; c.lineWidth = 2; c.beginPath(); c.moveTo(0, 0); c.lineTo(0, -22); c.stroke();
                c.restore();
                break;
            }
            case "skylight": {
                c.fillStyle = "#7c828b"; c.fillRect(x - 46, gY - 12, 92, 12);
                c.fillStyle = night > 0.4 && !outage ? "rgba(255,210,130,0.85)" : "rgba(150,200,235,0.75)";
                c.beginPath(); c.moveTo(x - 42, gY - 12); c.lineTo(x - 20, gY - 38); c.lineTo(x + 42, gY - 38); c.lineTo(x + 42, gY - 12); c.fill();
                c.strokeStyle = "#5e646c"; c.lineWidth = 2;
                for (let k = -20; k <= 42; k += 16) { c.beginPath(); c.moveTo(x + k, gY - 38); c.lineTo(x + k - 4, gY - 12); c.stroke(); }
                break;
            }
            case "solar": {
                for (let k = 0; k < 3; k++) {
                    const px = x - 60 + k * 42;
                    c.fillStyle = "#6f757e"; c.fillRect(px + 16, gY - 18, 3, 18);
                    c.fillStyle = "#23406e";
                    c.beginPath(); c.moveTo(px, gY - 14); c.lineTo(px + 10, gY - 40); c.lineTo(px + 44, gY - 40); c.lineTo(px + 34, gY - 14); c.fill();
                    c.strokeStyle = "rgba(160,200,255,0.4)"; c.lineWidth = 1;
                    c.beginPath(); c.moveTo(px + 5, gY - 27); c.lineTo(px + 39, gY - 27); c.stroke();
                }
                break;
            }
            case "chimney": {
                c.fillStyle = "#8a4b3c"; c.fillRect(x - 14, gY - 70, 28, 70);
                c.fillStyle = "#6e3a2e"; c.fillRect(x - 18, gY - 76, 36, 8);
                c.fillStyle = "rgba(0,0,0,0.12)";
                for (let k = 0; k < 6; k++) c.fillRect(x - 14, gY - 64 + k * 11, 28, 2);
                break;
            }
            case "pigeon": {
                const bob = Math.sin(performance.now() / 180 + seed * 10) * 1.5;
                c.fillStyle = "#8e94a3"; c.beginPath(); c.ellipse(x, gY - 9 + bob, 10, 7, 0, 0, Math.PI * 2); c.fill();
                c.fillStyle = "#6d7384"; c.beginPath(); c.arc(x + 8, gY - 16 + bob, 5, 0, Math.PI * 2); c.fill();
                c.fillStyle = "#e0a030"; c.fillRect(x + 12, gY - 16 + bob, 4, 2);
                c.fillStyle = "#c05050"; c.fillRect(x - 2, gY - 3, 2, 3); c.fillRect(x + 3, gY - 3, 2, 3);
                break;
            }
        }
        c.restore();
    }
    const ROOF_PROPS = ["ac", "vent", "ac", "dish", "skylight", "antenna", "solar", "chimney", "tank", "vent", "pigeon", "ac"];

    function drawRooftop(c, w, h, camX, gY, puddlesInRange) {
        const sky = skyAt();
        const ev = window.VilleWorld ? window.VilleWorld.activeEvent() : null;
        const type = ev ? ev.type : "";
        const outage = type === "coupure";
        drawSky(c, w, h, gY - 30, sky, { camX });
        if (type === "pluie") { c.fillStyle = "rgba(70,80,100,0.45)"; c.fillRect(0, 0, w, gY); }
        if (type === "canicule") { c.fillStyle = "rgba(255,150,60,0.16)"; c.fillRect(0, 0, w, gY); }
        const lit = outage ? 0 : sky.night;
        drawSkyline(c, w, gY - 26, camX, 0.04, 11, { color: hazeColor(sky, 0.72), lit: lit * 0.8, minH: 60, maxH: 190, bw: 70 });
        drawSkyline(c, w, gY - 6, camX, 0.16, 23, { color: hazeColor(sky, 0.5), lit, minH: 90, maxH: 290, bw: 120, dayWindows: sky.night < 0.5 ? "rgba(255,255,255,0.12)" : null });
        // surface du toit
        const dark = 1 - sky.night * 0.45;
        const g = c.createLinearGradient(0, gY, 0, h);
        g.addColorStop(0, "rgb(" + [96, 100, 108].map((v) => Math.round(v * dark)).join(",") + ")");
        g.addColorStop(1, "rgb(" + [58, 60, 66].map((v) => Math.round(v * dark)).join(",") + ")");
        c.fillStyle = g;
        c.fillRect(0, gY, w, h - gY);
        // gravier
        c.fillStyle = "rgba(255,255,255,0.08)";
        const cell = 16;
        for (let i = Math.floor(camX / cell); i <= Math.floor((camX + w) / cell); i++) {
            for (let k = 0; k < 3; k++) {
                const gx = i * cell + sr(i * 3.3 + k) * cell - camX;
                const gy = gY + 10 + sr(i * 7.1 + k * 2.2) * (h - gY - 12);
                c.fillRect(gx, gy, 2, 2);
            }
        }
        // jointures entre buildings voisins + rebord
        const seam = 1100;
        for (let i = Math.floor(camX / seam); i <= Math.floor((camX + w) / seam) + 1; i++) {
            const sx = i * seam - camX;
            if (i <= 0) continue;
            c.fillStyle = "rgba(0,0,0,0.35)";
            c.fillRect(sx - 3, gY, 6, h - gY);
            c.fillStyle = "#9aa0aa";
            c.fillRect(sx - 8, gY - 16, 16, 16);
        }
        c.fillStyle = "rgba(210,214,222," + (0.9 * dark) + ")";
        c.fillRect(0, gY - 3, w, 6);
        // accessoires du toit
        const slot = 250;
        for (let i = Math.floor((camX - 200) / slot); i <= Math.floor((camX + w + 200) / slot); i++) {
            if (i < 2) continue;
            const r = sr(i * 3.37);
            if (r < 0.3) continue;
            const kind = ROOF_PROPS[Math.floor(sr(i * 9.1) * ROOF_PROPS.length)];
            drawRooftopProp(c, kind, i * slot + sr(i * 1.3) * 120 - camX, gY, i, sky.night, outage);
        }
        // repères peints tous les 100 m (SCALE = 12 px/m côté jeu)
        c.fillStyle = "rgba(255,255,255,0.35)";
        c.font = "800 15px 'Segoe UI', Arial, sans-serif";
        c.textAlign = "center";
        for (let m = Math.max(100, Math.floor(camX / 1200) * 100); m * 12 < camX + w + 60; m += 100) {
            const sx = m * 12 - camX;
            c.fillText(m + " m", sx, gY + 32);
            c.fillRect(sx - 1, gY + 6, 2, 10);
        }
        c.textAlign = "left";
        // flaques (fuite d'eau / pluie)
        if (puddlesInRange) {
            const list = puddlesInRange(camX - 200, camX + w + 200);
            const t = performance.now() / 1000;
            list.forEach((pd) => {
                const sx = pd.cx - camX;
                c.fillStyle = "rgba(70,140,215,0.8)";
                c.beginPath(); c.ellipse(sx, gY + 12, pd.hw, 9, 0, 0, Math.PI * 2); c.fill();
                c.fillStyle = "rgba(200,235,255,0.55)";
                c.beginPath(); c.ellipse(sx - pd.hw * 0.3, gY + 9, pd.hw * 0.35, 2.5, 0, 0, Math.PI * 2); c.fill();
                if (type === "pluie") {
                    c.strokeStyle = "rgba(220,240,255,0.5)"; c.lineWidth = 1;
                    const rp = (t * 1.3 + sr(pd.cx)) % 1;
                    c.beginPath(); c.ellipse(sx + pd.hw * 0.2, gY + 12, 6 + rp * 22, 1.5 + rp * 4, 0, 0, Math.PI * 2);
                    c.globalAlpha = 1 - rp; c.stroke(); c.globalAlpha = 1;
                }
                if (type === "fuite") {
                    // tuyau percé qui goutte au-dessus de la flaque
                    c.fillStyle = "#7d838c"; c.fillRect(sx - 4, gY - 20, 8, 20);
                    const dp = (t * 2 + sr(pd.cx) * 3) % 1;
                    c.fillStyle = "rgba(120,190,255,0.9)";
                    c.beginPath(); c.arc(sx + 6, gY - 16 + dp * 26, 2.5, 0, Math.PI * 2); c.fill();
                }
            });
        }
    }

    function drawWeather(c, w, h, gY) {
        const ev = window.VilleWorld ? window.VilleWorld.activeEvent() : null;
        if (!ev) return;
        const t = performance.now() / 1000;
        if (ev.type === "pluie") {
            c.save();
            c.strokeStyle = "rgba(200,220,255,0.45)";
            c.lineWidth = 1.3;
            c.beginPath();
            for (let i = 0; i < 140; i++) {
                const x = ((sr(i * 1.7) * (w + 200) - t * 260) % (w + 200) + w + 200) % (w + 200) - 100;
                const y = ((sr(i * 5.3) * h + t * 900) % h);
                c.moveTo(x, y); c.lineTo(x - 6, y + 18);
            }
            c.stroke();
            c.restore();
        } else if (ev.type === "canicule") {
            c.save();
            c.strokeStyle = "rgba(255,255,255,0.08)";
            c.lineWidth = 2;
            for (let k = 0; k < 6; k++) {
                c.beginPath();
                const y = gY - 20 - k * 16;
                for (let x = 0; x <= w; x += 20) c.lineTo(x, y + Math.sin(x / 40 + t * 3 + k) * 3);
                c.stroke();
            }
            c.restore();
        } else if (ev.type === "coupure") {
            c.fillStyle = "rgba(0,0,10,0.12)";
            c.fillRect(0, 0, w, h);
        }
    }

    /* ---- Petits éléments réutilisés par ville.js ---- */
    function star(c, x, y, r) {
        c.beginPath();
        for (let i = 0; i < 10; i++) {
            const a = -Math.PI / 2 + i * Math.PI / 5;
            const rad = i % 2 ? r * 0.45 : r;
            c.lineTo(x + Math.cos(a) * rad, y + Math.sin(a) * rad);
        }
        c.closePath();
        c.fill();
    }
    // Casquettes/accessoires par-dessus le crabe de la plage (drawCrab du jeu)
    function drawCrabOutfit(c, x, y, size, dir, kind) {
        const k = size / 34;
        c.save();
        c.translate(x, y);
        if (kind === "police" || kind === "airline") {
            c.fillStyle = kind === "police" ? "#1d2b52" : "#b8322a";
            rr(c, -9 * k, -14 * k, 18 * k, 7 * k, 2 * k); c.fill();
            c.fillStyle = kind === "police" ? "#111a33" : "#7a1f19";
            c.beginPath(); c.ellipse(dir * 3 * k, -7.5 * k, 12 * k, 2.4 * k, 0, 0, Math.PI * 2); c.fill();
            c.fillStyle = "#ffd23f";
            c.beginPath(); c.arc(0, -11 * k, 1.8 * k, 0, Math.PI * 2); c.fill();
            if (kind === "police") { c.fillStyle = "#ffd23f"; star(c, 6 * k * dir, 2 * k, 3 * k); }
        } else if (kind === "bowtie") {
            c.fillStyle = "#c0392b";
            c.beginPath(); c.moveTo(0, -7 * k); c.lineTo(-6 * k, -10 * k); c.lineTo(-6 * k, -4 * k); c.closePath(); c.fill();
            c.beginPath(); c.moveTo(0, -7 * k); c.lineTo(6 * k, -10 * k); c.lineTo(6 * k, -4 * k); c.closePath(); c.fill();
            c.beginPath(); c.arc(0, -7 * k, 1.6 * k, 0, Math.PI * 2); c.fill();
        } else if (kind === "sunglasses") {
            c.fillStyle = "#111";
            c.fillRect(-9 * k, -19 * k, 18 * k, 4 * k);
        }
        c.restore();
    }

    // Voiture vue de face (fromFront = phares) ou de dos (feux rouges). (x, y) = sol.
    function drawCarFacing(c, x, y, s, color, fromFront, night) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        c.fillStyle = "rgba(0,0,0,0.3)";
        c.beginPath(); c.ellipse(0, 0, 54, 8, 0, 0, Math.PI * 2); c.fill();
        c.fillStyle = "#15171c";
        c.fillRect(-46, -16, 16, 16); c.fillRect(30, -16, 16, 16);
        c.fillStyle = color;
        rr(c, -50, -56, 100, 44, 12); c.fill();
        c.fillStyle = shade(color, 0.8);
        rr(c, -36, -84, 72, 32, 12); c.fill();
        c.fillStyle = fromFront ? "#1c2433" : "#2a3140";
        rr(c, -30, -80, 60, 22, 6); c.fill();
        c.fillStyle = "rgba(255,255,255,0.18)";
        c.fillRect(-26, -78, 18, 4);
        c.fillStyle = "#e9ecf1"; c.fillRect(-12, -26, 24, 9);
        c.fillStyle = "#22262e"; c.fillRect(-50, -16, 100, 5);
        c.fillStyle = fromFront ? "#fff6d0" : "#ff3030";
        rr(c, -44, -44, 16, 10, 4); c.fill();
        rr(c, 28, -44, 16, 10, 4); c.fill();
        if (fromFront) {
            c.fillStyle = "#2c3038";
            for (let k = -20; k <= 16; k += 6) c.fillRect(k, -40, 3, 8);
        }
        c.restore();
        if (night && fromFront) {
            const g = c.createRadialGradient(x, y - 38 * s, 4, x, y - 38 * s, 120 * s);
            g.addColorStop(0, "rgba(255,245,200,0.4)");
            g.addColorStop(1, "rgba(255,245,200,0)");
            c.fillStyle = g;
            c.fillRect(x - 120 * s, y - 158 * s, 240 * s, 240 * s);
        }
    }
    function shade(hex, k) {
        const n = parseInt(hex.slice(1), 16);
        const r = Math.round(((n >> 16) & 255) * k), g = Math.round(((n >> 8) & 255) * k), b = Math.round((n & 255) * k);
        return "rgb(" + r + "," + g + "," + b + ")";
    }

    // Portail rond : anneau de pierre grise, halo et tourbillon verts.
    // broken = anneau fêlé, éteint (fumée/étincelles gérées par ville.js).
    function drawPortal(c, x, y, R, t, broken, power) {
        power = power == null ? 1 : power;
        c.save();
        if (!broken && power > 0) {
            const halo = c.createRadialGradient(x, y, R * 0.6, x, y, R * 1.7);
            halo.addColorStop(0, "rgba(90,255,140," + 0.45 * power + ")");
            halo.addColorStop(1, "rgba(90,255,140,0)");
            c.fillStyle = halo;
            c.fillRect(x - R * 1.8, y - R * 1.8, R * 3.6, R * 3.6);
            const core = c.createRadialGradient(x, y, 0, x, y, R);
            core.addColorStop(0, "rgba(210,255,220," + 0.95 * power + ")");
            core.addColorStop(0.5, "rgba(60,220,110," + 0.8 * power + ")");
            core.addColorStop(1, "rgba(10,90,40," + 0.9 * power + ")");
            c.fillStyle = core;
            c.beginPath(); c.arc(x, y, R * 0.9, 0, Math.PI * 2); c.fill();
            c.lineWidth = 3;
            for (let i = 0; i < 7; i++) {
                c.strokeStyle = "rgba(200,255,210," + (0.25 + 0.1 * (i % 3)) * power + ")";
                const rad = R * (0.2 + i * 0.1);
                const a0 = t * (1.2 + i * 0.3) * (i % 2 ? 1 : -1) + i;
                c.beginPath(); c.arc(x, y, rad, a0, a0 + 2.2); c.stroke();
            }
        } else {
            c.fillStyle = "#15181d";
            c.beginPath(); c.arc(x, y, R * 0.9, 0, Math.PI * 2); c.fill();
        }
        // anneau de pierre
        c.lineWidth = R * 0.22;
        c.strokeStyle = "#8d9097";
        if (broken) {
            c.beginPath(); c.arc(x, y, R, 0.5, Math.PI * 2 - 0.2); c.stroke();
            c.strokeStyle = "#6f737a";
            c.beginPath(); c.arc(x + 6, y + 8, R, 0.05, 0.38); c.stroke();
        } else {
            c.beginPath(); c.arc(x, y, R, 0, Math.PI * 2); c.stroke();
        }
        c.strokeStyle = "#5f636a"; c.lineWidth = 2;
        for (let i = 0; i < 12; i++) {
            const a = i * Math.PI / 6;
            if (broken && a > 0.05 && a < 0.5) continue;
            c.beginPath();
            c.moveTo(x + Math.cos(a) * R * 0.89, y + Math.sin(a) * R * 0.89);
            c.lineTo(x + Math.cos(a) * R * 1.11, y + Math.sin(a) * R * 1.11);
            c.stroke();
        }
        if (broken) {
            c.strokeStyle = "#2b2e33"; c.lineWidth = 2.5;
            c.beginPath(); c.moveTo(x - R * 0.7, y - R * 0.75); c.lineTo(x - R * 0.55, y - R * 0.5); c.lineTo(x - R * 0.7, y - R * 0.3); c.stroke();
            c.beginPath(); c.moveTo(x + R * 0.9, y + R * 0.3); c.lineTo(x + R * 0.7, y + R * 0.45); c.stroke();
        }
        c.restore();
    }

    window.VilleArt = {
        sr, rr, clamp, c01, lerp, skyAt, hazeColor, drawSky, cloudPuff, drawSkyline, drawAirliner,
        drawRooftop, drawWeather, drawRooftopProp, star, drawCrabOutfit, drawCarFacing, shade, drawPortal,
    };
})();
