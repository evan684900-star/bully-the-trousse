/* Bully the Trousse — Monde Ville : les niveaux jouables.
     city      : aéroport (portique de sécurité, crabes policiers) + rue (passage piéton) + la Tour
     reception : réception chaleureuse (boutique, ascenseur, escaliers, tableau des annonces)
     darkroom  : pièce noire du mode histoire (interrupteur, portail)
     glitch    : monde bugé, les trois rencontres, le combat de boss et la fuite
   S'appuie sur window.VilleCore (ville.js) et window.VilleArt (ville-art.js). */
(function () {
    "use strict";
    const C = window.VilleCore;
    if (!C) return;
    const { B, A, S, L, V, P, LV, TS } = C;
    const { sr, rr, clamp, c01, lerp } = A;
    const eIO = C.eIO, eOut = C.eOut;
    const inView = (x, w) => x + w > V.camX - 50 && x - w < V.camX + V.VW + 50;

    // Déplace la trousse toute seule jusqu'à tx (séquences scriptées)
    function walkTo(tx, speed) {
        const d = tx - P.x;
        if (Math.abs(d) < 5) { P.x = tx; P.vx = 0; P.autoWalk = false; return true; }
        P.autoWalk = true;
        P.dir = Math.sign(d);
        P.vx = Math.sign(d) * (speed || 210);
        return false;
    }
    function hold(dur, fn) {
        let t = 0;
        return () => { fn(); t += V.dt; return t >= dur; };
    }
    function glitchSfx() {
        for (let i = 0; i < 5; i++) setTimeout(() => B.beep(80 + Math.random() * 900, 0.07, "square", 0.05), i * 60);
    }
    function* violentGlitch(atPeak) {
        glitchSfx();
        yield hold(0.45, () => { V.glitch = Math.max(V.glitch, 1.5); V.shake = Math.max(V.shake, 16); });
        atPeak();
        glitchSfx();
        yield hold(0.5, () => { V.glitch = Math.max(V.glitch, 1.7); V.shake = Math.max(V.shake, 20); });
    }
    function irisTo(fn, dur) {
        const ps = C.playerScreen();
        C.startTrans("iris", dur || 1, fn, { x: ps.x, y: ps.y });
    }

    /* =====================================================================
       NIVEAU 1 : AÉROPORT + RUE
       ===================================================================== */
    const SKINSHOP_X = 3320; // "La Trousserie", boutique de skins + cosmétiques
    const AIR_END = 2900, SEC_X = 1610, CROSS_A = 3900, CROSS_B = 4260, TOWER_A = 4880, TOWER_DOOR = 5150, CITY_W = 5520, RESPAWN = 3050;
    const city = { secFlash: 0, lastX: 0, door: 0, cars: [], carT: 0, dying: false, killer: null, bip: 0 };
    const CAR_COLORS = ["#d64541", "#2f6fb5", "#f1c40f", "#ecf0f1", "#27ae60", "#34495e", "#e67e22"];
    function lightState() {
        const t = V.t % 16;
        if (t < 6.5) return { ped: "green", left: 8 - t };
        if (t < 8) return { ped: "blink", left: 8 - t };
        return { ped: "red", left: 16 - t };
    }
    function openDepartures() {
        const s = S();
        const list = [{ id: "cour", label: "🏫 " + L("Cour d'école", "Schoolyard") }];
        if (s.volcanUnlocked) list.push({ id: "volcans", label: "🌋 " + L("Volcans", "Volcanoes") });
        if (s.plageUnlocked) list.push({ id: "plage", label: "🏖️ " + L("Plage", "Beach") });
        C.openPop(L("✈️ Départs", "✈️ Departures"),
            "<p>" + L("Où veux-tu t'envoler ? Tu pourras revenir en Ville depuis la carte « Ville » du menu.", "Where do you want to fly? You can come back to the City from the “City” card in the menu.") + "</p>",
            list.map((d) => ({ label: d.label, act: () => { P.frozen = true; irisTo(() => C.leaveVille(d.id), 1); } })));
    }
    LV.city = {
        w: CITY_W,
        enter() { city.cars = []; city.dying = false; city.killer = null; city.lastX = P.x; city.door = 0; },
        bounds: () => [40, CITY_W - 40],
        interacts: [
            { x: 260, r: 95, pa: 170, label: () => L("Prendre un vol", "Catch a flight"), act: openDepartures },
            { x: SKINSHOP_X, r: 80, pa: 200, label: () => L("Entrer à La Trousserie", "Enter La Trousserie"), act: () => {
                if (C.isOutage()) { C.showMsg(L("⚡ Coupure de courant : La Trousserie est fermée.", "⚡ Power outage: La Trousserie is closed."), 3); B.sfxError(); return; }
                V.lock = true;
                B.openVilleSkinShop(() => { V.lock = false; });
            } },
            { x: TOWER_DOOR, r: 85, pa: 190, label: () => L("Appuyez sur E pour entrer", "Press E to enter"), act: () => {
                P.frozen = true;
                irisTo(() => C.setLevel("reception", 190, 1), 1);
            } },
        ],
        update(dt) {
            // portique de sécurité : bip + voyant vert quand on passe dessous
            if ((city.lastX < SEC_X) !== (P.x < SEC_X)) {
                city.secFlash = 1.2; city.bip = 1;
                B.beep(1320, 0.12, "sine", 0.08);
                setTimeout(() => B.beep(1760, 0.14, "sine", 0.08), 130);
            }
            city.lastX = P.x;
            city.secFlash = Math.max(0, city.secFlash - dt);
            city.bip = Math.max(0, city.bip - dt * 0.8);
            city.door = lerp(city.door, Math.abs(P.x - (AIR_END - 40)) < 190 ? 1 : 0, Math.min(1, dt * 5));
            // circulation perpendiculaire au passage piéton
            const ls = lightState();
            city.carT -= dt;
            if (ls.ped === "red" && ls.left > 1.2 && ls.left < 7.4 && city.carT <= 0) {
                const lane = Math.random() < 0.5 ? -1 : 1;
                city.cars.push({ lane, u: lane < 0 ? 0 : 1.2, speed: 0.8 + Math.random() * 0.3, color: CAR_COLORS[Math.floor(Math.random() * CAR_COLORS.length)] });
                city.carT = 0.9 + Math.random() * 1.1;
            }
            city.cars.forEach((car) => { car.u += (car.lane < 0 ? 1 : -1) * car.speed * dt; });
            city.cars = city.cars.filter((car) => car.u > -0.05 && car.u < 1.25);
            if (city.killer) city.killer.u += 2.6 * dt;
            // le joueur s'engage alors que le bonhomme est rouge : percuté
            if (!city.dying && ls.ped === "red" && P.x > CROSS_A + 25 && P.x < CROSS_B - 25 && !C.transBusy()) {
                city.dying = true;
                C.run(crosswalkDeath());
            }
        },
        draw(c) { drawCityBack(c); },
        drawMid(c) { drawCars(c, false); },
        front(c) { drawCityFront(c); drawCars(c, true); },
    };
    function* crosswalkDeath() {
        P.frozen = true;
        C.playA(C.aTires, true);
        city.killer = { lane: -1, u: 0.45, color: "#c0392b", killer: true, x: P.x };
        yield () => city.killer.u >= 0.92;
        V.shake = 24;
        B.sfxCrash();
        P.va = 520; P.vx = 0; P.rot = 0.8;
        S().villeCrosswalkDeaths++;
        B.persist();
        C.startTrans("blur", 1.8, () => {
            city.killer = null;
            city.cars = [];
            C.resetPlayer(RESPAWN, 1);
            C.snapCam();
            V.t += 16 - (V.t % 16); // on repart au vert
        });
        yield () => !C.transBusy();
        city.dying = false;
        P.frozen = false;
        C.showMsg(L("🚦 Attends que le bonhomme passe au vert...", "🚦 Wait for the green man..."), 3);
    }
    function roadGeom() {
        const cx = (CROSS_A + CROSS_B) / 2 - V.camX;
        return { cx, hz: V.GY - 130, near: V.VH + 90 };
    }
    function carPos(car) {
        const g = roadGeom();
        const u = c01(car.u);
        const y = lerp(g.hz, g.near, Math.pow(u, 1.6));
        const s = lerp(0.12, 1.55, u);
        const x = (car.killer ? car.x - V.camX : g.cx + car.lane * lerp(8, 95, u));
        return { x, y, s };
    }
    function drawCars(c, front) {
        const sky = A.skyAt();
        const list = city.killer ? city.cars.concat([city.killer]) : city.cars;
        list.slice().sort((a, b) => a.u - b.u).forEach((car) => {
            const p = carPos(car);
            if ((p.y > V.GY + 40) !== front) return;
            A.drawCarFacing(c, p.x, p.y, p.s, car.color, car.lane < 0, sky.night > 0.35);
        });
        // feu vert piéton : une voiture attend au loin
        if (!front && lightState().ped !== "red") {
            const p = carPos({ lane: -1, u: 0.3 });
            A.drawCarFacing(c, p.x, p.y, p.s, "#2f6fb5", true, sky.night > 0.35);
        }
    }

    const FACADES = ["#c9a27e", "#b5846b", "#d8c3a5", "#9fa8b3", "#c7b299", "#8c9aa6", "#a8927a"];
    const SHOPS = [["CAFÉ", "#6d4c41"], ["BOULANGERIE", "#c77d2e"], ["PHARMACIE ✚", "#2e8b57"], ["FLEURISTE", "#c2477a"], ["LIBRAIRIE", "#3b5998"], ["PRESSING", "#607d8b"], ["KEBAB", "#b03a2e"]];
    function drawFacade(c, x0, x1, seedBase, sky) {
        const lit = C.isOutage() ? 0 : sky.night;
        const GY = V.GY;
        for (let bx = x0; bx < x1; bx += 280) {
            const i = Math.floor(bx / 280) + seedBase;
            const w = Math.min(280, x1 - bx);
            if (!inView(bx + w / 2, w)) continue;
            const sx = bx - V.camX;
            const hgt = 330 + sr(i * 2.1) * 170;
            const col = FACADES[Math.floor(sr(i * 4.3) * FACADES.length)];
            c.fillStyle = col;
            c.fillRect(sx, GY - hgt, w + 1, hgt);
            c.fillStyle = "rgba(0,0,0,0.12)";
            c.fillRect(sx, GY - hgt, w, 10);
            c.fillRect(sx + w - 3, GY - hgt, 3, hgt);
            // fenêtres
            for (let r = 0; r < 6; r++) {
                const wy = GY - hgt + 30 + r * 52;
                if (wy > GY - 140) break;
                for (let k = 0; k < 4; k++) {
                    const wx = sx + 22 + k * 64;
                    if (wx + 34 > sx + w) break;
                    const on = sr(i * 17 + r * 5.1 + k * 2.3) < lit * 0.6;
                    c.fillStyle = on ? "rgba(255,212,130,0.95)" : (sky.night > 0.5 ? "#2a3144" : "rgba(160,200,230,0.8)");
                    c.fillRect(wx, wy, 34, 38);
                    c.fillStyle = "rgba(0,0,0,0.25)";
                    c.fillRect(wx - 3, wy + 38, 40, 4);
                    c.fillRect(wx + 16, wy, 2, 38);
                }
            }
            // boutique au rez-de-chaussée
            const shop = SHOPS[Math.floor(sr(i * 6.7) * SHOPS.length)];
            c.fillStyle = shop[1];
            c.fillRect(sx + 10, GY - 132, w - 20, 26);
            c.fillStyle = "#fff"; c.font = "900 15px 'Segoe UI', Arial"; c.textAlign = "center";
            c.fillText(shop[0], sx + w / 2, GY - 114);
            for (let k = 0; k < Math.floor((w - 20) / 20); k++) {
                c.fillStyle = k % 2 ? "#f4f1ea" : shop[1];
                c.beginPath(); c.moveTo(sx + 10 + k * 20, GY - 106); c.lineTo(sx + 30 + k * 20, GY - 106); c.lineTo(sx + 25 + k * 20, GY - 92); c.lineTo(sx + 15 + k * 20, GY - 92); c.fill();
            }
            c.fillStyle = lit > 0.3 ? "rgba(255,220,150,0.9)" : "rgba(170,205,230,0.85)";
            c.fillRect(sx + 20, GY - 86, w - 110, 70);
            c.fillStyle = "#3b2f2a"; c.fillRect(sx + w - 78, GY - 90, 44, 90);
            c.fillStyle = "#ffd23f"; c.fillRect(sx + w - 42, GY - 46, 4, 4);
            c.textAlign = "left";
        }
    }
    function drawLamp(c, x, big, sky) {
        const GY = V.GY;
        const k = big ? 1.35 : 1;
        c.fillStyle = big ? "#1d2027" : "#3a3f48";
        c.fillRect(x - 4 * k, GY - 250 * k, 8 * k, 250 * k);
        c.fillRect(x - 4 * k, GY - 250 * k, 40 * k, 6 * k);
        c.fillRect(x + 28 * k, GY - 250 * k, 16 * k, 14 * k);
        if (sky.night > 0.3 && !C.isOutage()) {
            const g = c.createRadialGradient(x + 36 * k, GY - 234 * k, 2, x + 36 * k, GY - 234 * k, 150 * k);
            g.addColorStop(0, "rgba(255,225,150,0.55)");
            g.addColorStop(1, "rgba(255,225,150,0)");
            c.fillStyle = g;
            c.fillRect(x - 120 * k, GY - 390 * k, 310 * k, 400 * k);
            c.fillStyle = "#fff3c4"; c.fillRect(x + 30 * k, GY - 238 * k, 12 * k, 5 * k);
        }
    }
    function drawCrabGuard(c, x, kind, dir, size) {
        const y = V.GY - size * 0.3;
        B.drawCrab(c, x - V.camX, y, size, V.t + x * 0.01, dir);
        A.drawCrabOutfit(c, x - V.camX, y, size, dir, kind);
    }
    function drawCityBack(c) {
        const sky = A.skyAt();
        const GY = V.GY, w = V.VW, h = V.VH;
        const lit = C.isOutage() ? 0 : sky.night;
        A.drawSky(c, w, h, GY - 60, sky, { camX: V.camX });
        A.drawSkyline(c, w, GY - 170, V.camX, 0.12, 5, { color: A.hazeColor(sky, 0.7), lit: lit * 0.8, minH: 80, maxH: 260, bw: 90 });
        A.drawSkyline(c, w, GY - 90, V.camX, 0.35, 9, { color: A.hazeColor(sky, 0.5), lit, minH: 120, maxH: 360, bw: 140, dayWindows: sky.night < 0.5 ? "rgba(255,255,255,0.14)" : null });
        // rue perpendiculaire qui file vers l'horizon (d'où viennent les voitures)
        const g = roadGeom();
        c.fillStyle = "#3a3d43";
        c.beginPath();
        c.moveTo(CROSS_A - V.camX, GY); c.lineTo(g.cx - 16, g.hz); c.lineTo(g.cx + 16, g.hz); c.lineTo(CROSS_B - V.camX, GY);
        c.closePath(); c.fill();
        c.strokeStyle = "rgba(255,255,255,0.6)"; c.lineWidth = 2; c.setLineDash([10, 12]);
        c.beginPath(); c.moveTo(g.cx, g.hz); c.lineTo(g.cx, GY); c.stroke(); c.setLineDash([]);
        // façades
        drawFacade(c, AIR_END, CROSS_A - 60, 100, sky);
        drawFacade(c, CROSS_B + 60, TOWER_A, 200, sky);
        drawSkinShop(c, sky);
        drawTower(c, sky);
        for (let x = AIR_END + 250; x < TOWER_A; x += 440) {
            if (x > CROSS_A - 120 && x < CROSS_B + 120) continue;
            if (inView(x, 60)) drawLamp(c, x - V.camX, false, sky);
        }
        // trottoir
        const sw = c.createLinearGradient(0, GY, 0, h);
        sw.addColorStop(0, "#b9b8b0"); sw.addColorStop(1, "#8e8d86");
        c.fillStyle = sw;
        c.fillRect(Math.max(0, AIR_END - V.camX), GY, w, h - GY);
        c.fillStyle = "rgba(0,0,0,0.12)";
        for (let x = Math.floor(V.camX / 60) * 60; x < V.camX + w; x += 60) {
            if (x < AIR_END) continue;
            c.fillRect(x - V.camX, GY, 2, h - GY);
        }
        c.fillStyle = "#d9d8d0"; c.fillRect(Math.max(0, AIR_END - V.camX), GY - 2, w, 6);
        // chaussée + passage piéton
        c.fillStyle = "#3a3d43";
        c.fillRect(CROSS_A - V.camX, GY, CROSS_B - CROSS_A, h - GY);
        c.fillStyle = "#f2f2ee";
        const depth = h - GY;
        [0.06, 0.26, 0.5, 0.78].forEach((f, i) => {
            const y0 = GY + depth * f, hh = depth * (0.09 + i * 0.03);
            c.fillRect(CROSS_A - V.camX + 16, y0, CROSS_B - CROSS_A - 32, hh);
        });
        // feux
        const ls = lightState();
        drawPedLight(c, CROSS_A - 50 - V.camX, ls);
        drawCarLight(c, CROSS_B + 50 - V.camX, ls);
        // quelques objets de rue
        if (inView(3700, 40)) drawBench(c, 3700 - V.camX);
        if (inView(3800, 40)) drawBin(c, 3800 - V.camX);
        if (inView(4480, 40)) drawHydrant(c, 4480 - V.camX);
        if (inView(4700, 60)) drawBusStop(c, 4700 - V.camX);
        // intérieur de l'aéroport par-dessus, pour x < AIR_END
        if (V.camX < AIR_END) {
            c.save();
            c.beginPath(); c.rect(0, 0, AIR_END - V.camX, h); c.clip();
            drawAirport(c, sky);
            c.restore();
        }
    }
    // Vitrine de La Trousserie : trousses exposées, enseigne, porte vitrée
    function drawSkinShop(c, sky) {
        if (!inView(SKINSHOP_X, 150)) return;
        const GY = V.GY, x = SKINSHOP_X - V.camX;
        const lit = C.isOutage() ? 0 : 1;
        c.fillStyle = "#2c3e50"; c.fillRect(x - 140, GY - 200, 280, 200);
        c.fillStyle = "#8e44ad"; c.fillRect(x - 146, GY - 206, 292, 40);
        c.fillStyle = "#ffd23f"; c.font = "900 20px Georgia, serif"; c.textAlign = "center";
        c.fillText("👕 La Trousserie", x, GY - 179);
        for (let k = 0; k < 12; k++) { c.fillStyle = k % 2 ? "#f4f1ea" : "#8e44ad"; c.beginPath(); c.moveTo(x - 140 + k * 23.3, GY - 166); c.lineTo(x - 116.7 + k * 23.3, GY - 166); c.lineTo(x - 121 + k * 23.3, GY - 150); c.lineTo(x - 135 + k * 23.3, GY - 150); c.fill(); }
        c.fillStyle = lit ? "rgba(255,236,190,0.95)" : "rgba(60,70,90,0.9)"; c.fillRect(x - 128, GY - 142, 170, 110);
        c.fillStyle = "#6d4c41"; c.fillRect(x - 128, GY - 60, 170, 8);
        ["classique", "doree", "glacee", "feu"].forEach((id, i) => {
            const skin = B.getSkin(id);
            c.save();
            c.translate(x - 104 + i * 42, GY - 78);
            if (B.trousseImg.complete) {
                const f = B.getFilteredSkinCanvas ? B.getFilteredSkinCanvas(B.trousseImg, skin.filter === "rainbow" ? "none" : skin.filter) : B.trousseImg;
                c.drawImage(f, -20, -20, 40, 40);
            }
            c.restore();
        });
        c.fillStyle = "#15151a"; c.fillRect(x - 100, GY - 136, 30, 4); c.fillRect(x - 94, GY - 150, 18, 14);
        c.fillStyle = "#ffd23f"; c.beginPath(); c.moveTo(x - 30, GY - 124); c.lineTo(x - 24, GY - 138); c.lineTo(x - 18, GY - 128); c.lineTo(x - 12, GY - 138); c.lineTo(x - 6, GY - 124); c.fill();
        c.fillStyle = "rgba(170,205,230,0.7)"; c.fillRect(x + 58, GY - 142, 70, 142);
        c.strokeStyle = "#1c2833"; c.lineWidth = 4; c.strokeRect(x + 58, GY - 142, 70, 142);
        c.fillStyle = "#ffd23f"; c.fillRect(x + 64, GY - 76, 5, 16);
        c.fillStyle = "#e74c3c"; rr(c, x + 70, GY - 128, 48, 18, 4); c.fill();
        c.fillStyle = "#fff"; c.font = "900 11px 'Segoe UI'"; c.fillText(lit ? L("OUVERT", "OPEN") : L("FERMÉ", "CLOSED"), x + 94, GY - 115);
        c.textAlign = "left";
    }
    function drawTower(c, sky) {
        if (!inView((TOWER_A + CITY_W) / 2, (CITY_W - TOWER_A) / 2 + 40)) return;
        const GY = V.GY, x0 = TOWER_A - V.camX, w = CITY_W - TOWER_A + 20;
        const lit = C.isOutage() ? 0 : sky.night;
        c.fillStyle = "#4a5568";
        c.fillRect(x0, -20, w, GY + 20);
        for (let r = 0; r * 46 < GY - 180; r++) {
            for (let k = 0; k * 58 < w - 40; k++) {
                const on = sr(r * 3.3 + k * 7.1 + 50) < lit * 0.55;
                c.fillStyle = on ? "rgba(255,214,140,0.95)" : (sky.night > 0.5 ? "#232a3a" : "rgba(170,210,240,0.7)");
                c.fillRect(x0 + 24 + k * 58, GY - 210 - r * 46, 40, 32);
            }
        }
        c.fillStyle = "#c9b28a"; c.fillRect(x0, GY - 170, w, 170);
        const dx = TOWER_DOOR - V.camX;
        c.fillStyle = "#7a1f2b"; c.fillRect(dx - 110, GY - 176, 220, 30);
        c.fillStyle = "#ffd23f"; c.font = "900 15px 'Segoe UI', Arial"; c.textAlign = "center";
        c.fillText(L("LA TOUR — RÉCEPTION", "THE TOWER — LOBBY"), dx, GY - 156);
        c.fillStyle = "rgba(255,210,140,0.95)"; c.fillRect(dx - 60, GY - 140, 120, 140);
        c.fillStyle = "#5b3b1c"; c.fillRect(dx - 64, GY - 144, 128, 6); c.fillRect(dx - 2, GY - 140, 4, 140);
        c.fillStyle = "rgba(255,255,255,0.25)"; c.fillRect(dx - 50, GY - 130, 12, 110);
        [-95, 95].forEach((o) => { c.fillStyle = "#6d4c41"; c.fillRect(dx + o - 16, GY - 34, 32, 34); c.fillStyle = "#2e7d32"; c.beginPath(); c.arc(dx + o, GY - 50, 24, 0, Math.PI * 2); c.fill(); });
        c.fillStyle = "#e53935"; c.fillRect(CITY_W - 60 - V.camX, GY - 70, 12, 70);
        for (let k = 0; k < 4; k++) { c.fillStyle = k % 2 ? "#fff" : "#e53935"; c.fillRect(CITY_W - 100 - V.camX, GY - 70 + k * 12, 90, 12); }
        c.textAlign = "left";
    }
    function drawPedLight(c, x, ls) {
        const GY = V.GY;
        c.fillStyle = "#2b2f36"; c.fillRect(x - 4, GY - 210, 8, 210);
        c.fillStyle = "#1a1c20"; rr(c, x - 24, GY - 270, 48, 90, 8); c.fill();
        const red = ls.ped === "red";
        const greenOn = ls.ped === "green" || (ls.ped === "blink" && Math.floor(V.t * 4) % 2);
        c.fillStyle = red ? "#ff3b3b" : "#401010"; rr(c, x - 17, GY - 262, 34, 36, 6); c.fill();
        c.fillStyle = greenOn ? "#3ddc84" : "#0f3020"; rr(c, x - 17, GY - 222, 34, 36, 6); c.fill();
        c.font = "22px sans-serif"; c.textAlign = "center"; c.textBaseline = "middle";
        c.globalAlpha = red ? 1 : 0.25; c.fillText("🧍", x, GY - 243);
        c.globalAlpha = greenOn ? 1 : 0.25; c.fillText("🚶", x, GY - 203);
        c.globalAlpha = 1;
        c.fillStyle = "#1a1c20"; rr(c, x - 16, GY - 176, 32, 20, 5); c.fill();
        c.fillStyle = red ? "#ff6b6b" : "#6bffb0"; c.font = "800 13px 'Segoe UI', Arial";
        c.fillText(String(Math.ceil(ls.left)), x, GY - 165);
        c.textBaseline = "alphabetic"; c.textAlign = "left";
    }
    function drawCarLight(c, x, ls) {
        const GY = V.GY;
        c.fillStyle = "#2b2f36"; c.fillRect(x - 4, GY - 240, 8, 240);
        c.fillStyle = "#1a1c20"; rr(c, x - 16, GY - 330, 32, 90, 8); c.fill();
        const carsGo = ls.ped === "red";
        c.fillStyle = !carsGo ? "#ff3b3b" : "#401010"; c.beginPath(); c.arc(x, GY - 314, 10, 0, Math.PI * 2); c.fill();
        c.fillStyle = ls.ped === "blink" ? "#ffb52e" : "#40300a"; c.beginPath(); c.arc(x, GY - 286, 10, 0, Math.PI * 2); c.fill();
        c.fillStyle = carsGo ? "#3ddc84" : "#0f3020"; c.beginPath(); c.arc(x, GY - 258, 10, 0, Math.PI * 2); c.fill();
    }
    function drawBench(c, x) {
        const GY = V.GY;
        c.fillStyle = "#6d4c41"; c.fillRect(x - 50, GY - 40, 100, 8); c.fillRect(x - 50, GY - 70, 100, 8);
        c.fillStyle = "#333"; c.fillRect(x - 44, GY - 40, 6, 40); c.fillRect(x + 38, GY - 40, 6, 40);
    }
    function drawBin(c, x) {
        const GY = V.GY;
        c.fillStyle = "#2e7d32"; rr(c, x - 18, GY - 56, 36, 56, 5); c.fill();
        c.fillStyle = "#1b5e20"; c.fillRect(x - 21, GY - 60, 42, 8);
    }
    function drawHydrant(c, x) {
        const GY = V.GY;
        c.fillStyle = "#c62828"; rr(c, x - 11, GY - 48, 22, 48, 6); c.fill();
        c.fillRect(x - 17, GY - 34, 34, 8);
        c.beginPath(); c.arc(x, GY - 48, 11, Math.PI, 0); c.fill();
    }
    function drawBusStop(c, x) {
        const GY = V.GY;
        c.fillStyle = "rgba(180,215,235,0.5)"; c.fillRect(x - 70, GY - 150, 140, 150);
        c.fillStyle = "#37474f"; c.fillRect(x - 76, GY - 156, 152, 10); c.fillRect(x - 74, GY - 150, 5, 150); c.fillRect(x + 69, GY - 150, 5, 150);
        c.fillStyle = "#1565c0"; rr(c, x + 80, GY - 200, 34, 34, 17); c.fill();
        c.fillStyle = "#fff"; c.font = "900 16px 'Segoe UI'"; c.textAlign = "center"; c.fillText("BUS", x + 97, GY - 177);
        c.fillStyle = "#37474f"; c.fillRect(x + 95, GY - 166, 4, 166);
        c.textAlign = "left";
    }

    // Intérieur de l'aéroport : baies vitrées sur le tarmac, tapis à bagages,
    // tableau des vols, sièges, portique de sécurité et douaniers crabes.
    function drawAirport(c, sky) {
        const GY = V.GY, h = V.VH, cam = V.camX;
        const x1 = AIR_END - cam;
        // mur
        const wg = c.createLinearGradient(0, 0, 0, GY);
        wg.addColorStop(0, "#d9e1ea"); wg.addColorStop(1, "#c3ceda");
        c.fillStyle = wg; c.fillRect(0, 0, x1, GY);
        // baies vitrées : ciel réel + tarmac + avions garés
        const wTop = Math.max(40, GY - 390), wBot = GY - 150;
        c.save();
        c.beginPath();
        for (let x = 0; x < AIR_END; x += 190) {
            if (x > 1380 && x < 1900) continue;
            if (!inView(x + 80, 100)) continue;
            c.rect(x + 12 - cam, wTop, 166, wBot - wTop);
        }
        c.clip();
        A.drawSky(c, V.VW, h, wBot, sky, { camX: cam, noClouds: false });
        A.drawSkyline(c, V.VW, wBot - 40, cam, 0.1, 3, { color: A.hazeColor(sky, 0.6), lit: sky.night * 0.8, minH: 20, maxH: 90, bw: 50 });
        c.fillStyle = "#5f6670"; c.fillRect(0, wBot - 40, V.VW, 40);
        c.fillStyle = "#e8d06a";
        for (let x = -((cam * 0.6) % 80); x < V.VW; x += 80) c.fillRect(x, wBot - 20, 40, 3);
        [300, 1300, 2300].forEach((px, i) => {
            A.drawAirliner(c, px - cam * 0.6, wBot - 58, 0.34, { dir: i % 2 ? -1 : 1, lights: sky.night > 0.4 });
        });
        const tk = (V.t * 140) % 3600;
        A.drawAirliner(c, tk - 400 - cam * 0.6 + 800, wBot - 70 - Math.max(0, tk - 1400) * 0.12, 0.28, { dir: 1, rot: tk > 1400 ? -0.12 : 0, lights: true });
        c.restore();
        c.fillStyle = "#8b96a3";
        for (let x = 0; x < AIR_END; x += 190) {
            if (x > 1380 && x < 1900) continue;
            if (!inView(x + 80, 100)) continue;
            c.fillRect(x + 6 - cam, wTop - 6, 178, 8); c.fillRect(x + 6 - cam, wBot - 2, 178, 8);
            c.fillRect(x + 6 - cam, wTop, 8, wBot - wTop); c.fillRect(x + 176 - cam, wTop, 8, wBot - wTop);
            c.fillRect(x + 92 - cam, wTop, 4, wBot - wTop);
        }
        // plafond + néons
        c.fillStyle = "#56616e"; c.fillRect(0, 0, x1, Math.max(0, wTop - 26));
        for (let x = Math.floor(cam / 220) * 220; x < Math.min(AIR_END, cam + V.VW + 220); x += 220) {
            c.fillStyle = "#f4f8ff"; c.fillRect(x - cam, wTop - 30, 90, 6);
        }
        // sol carrelé brillant
        const fg = c.createLinearGradient(0, GY, 0, h);
        fg.addColorStop(0, "#c2cad3"); fg.addColorStop(1, "#98a2ad");
        c.fillStyle = fg; c.fillRect(0, GY, x1, h - GY);
        c.fillStyle = "rgba(255,255,255,0.18)"; c.fillRect(0, GY + 10, x1, 4);
        c.fillStyle = "rgba(0,0,0,0.08)";
        for (let x = Math.floor(cam / 80) * 80; x < Math.min(AIR_END, cam + V.VW); x += 80) c.fillRect(x - cam, GY, 2, h - GY);
        c.fillStyle = "#7d8894"; c.fillRect(0, GY - 3, x1, 5);
        // panneaux suspendus
        hangSign(c, 260, wTop, "✈ " + L("DÉPARTS", "DEPARTURES"), "#1d3b6e");
        hangSign(c, 700, wTop, L("ARRIVÉES", "ARRIVALS") + " ↓", "#1d3b6e");
        hangSign(c, 1500, wTop, L("CONTRÔLE DE SÉCURITÉ", "SECURITY CHECK"), "#6e1d2b");
        hangSign(c, 2680, wTop, L("SORTIE", "EXIT") + " →", "#1b5e20");
        // comptoir des départs + agent crabe
        if (inView(260, 140)) {
            const x = 260 - cam;
            drawCrabGuardAt(c, x + 10, "airline", 1, 52);
            c.fillStyle = "#3f5d8a"; c.fillRect(x - 120, GY - 90, 240, 90);
            c.fillStyle = "#e9eef5"; c.fillRect(x - 126, GY - 96, 252, 10);
            c.fillStyle = "#ffd23f"; c.font = "900 13px 'Segoe UI'"; c.textAlign = "center"; c.fillText("BULLY AIR", x, GY - 44);
            c.fillStyle = "#12161f"; c.fillRect(x - 80, GY - 230, 160, 70);
            c.fillStyle = "#ffcf3f"; c.font = "700 11px 'Consolas', monospace"; c.textAlign = "left";
            const rows = [[L("COUR D'ÉCOLE", "SCHOOLYARD"), "OK"], [L("VOLCANS", "VOLCANOES"), S().volcanUnlocked ? "OK" : "--"], [L("PLAGE", "BEACH"), S().plageUnlocked ? "OK" : "--"], [L("LUNE", "MOON"), L("ANNULÉ", "CANCELLED")]];
            rows.forEach((r, i) => { c.fillText(r[0], x - 72, GY - 212 + i * 15); c.fillText(r[1], x + 38, GY - 212 + i * 15); });
        }
        // tapis à bagages
        if (inView(720, 200)) {
            const x = 720 - cam;
            c.fillStyle = "#2d3137"; rr(c, x - 170, GY - 46, 340, 30, 15); c.fill();
            c.fillStyle = "#555b64"; c.fillRect(x - 160, GY - 44, 320, 6);
            for (let k = 0; k < 5; k++) {
                const bx = x - 160 + ((V.t * 40 + k * 70) % 320);
                c.fillStyle = ["#c0392b", "#2980b9", "#8e44ad", "#16a085", "#f39c12"][k];
                rr(c, bx - 16, GY - 70, 32, 26, 4); c.fill();
                c.fillStyle = "#222"; c.fillRect(bx - 5, GY - 76, 10, 6);
            }
        }
        // tableau des vols
        if (inView(1050, 120)) {
            const x = 1050 - cam;
            c.fillStyle = "#12161f"; c.fillRect(x - 100, GY - 300, 200, 110);
            c.fillStyle = "#7d8894"; c.fillRect(x - 3, GY - 190, 6, 190);
            c.fillStyle = "#ffcf3f"; c.font = "700 11px 'Consolas', monospace";
            const now = new Date();
            ["BT 101", "BT 207", "BT 314", "BT 420", "BT 999"].forEach((f, i) => {
                const tt = new Date(now.getTime() + (i + 1) * 17 * 60000);
                c.fillText(f + "  " + String(tt.getHours()).padStart(2, "0") + ":" + String(tt.getMinutes()).padStart(2, "0") + "  " + (i === 4 ? "???" : L("À L'HEURE", "ON TIME")), x - 90, GY - 280 + i * 18);
            });
        }
        // sièges
        for (let k = 0; k < 6; k++) {
            const sx = 1150 + k * 42;
            if (!inView(sx, 30)) continue;
            c.fillStyle = "#2a5d9f"; rr(c, sx - cam - 17, GY - 64, 34, 40, 5); c.fill();
            c.fillStyle = "#1f4a80"; c.fillRect(sx - cam - 19, GY - 30, 38, 10);
            c.fillStyle = "#555"; c.fillRect(sx - cam - 2, GY - 20, 4, 20);
        }
        if (inView(1236, 40)) { const y = GY - 50; B.drawCrab(c, 1236 - cam, y, 36, 0, 1); A.drawCrabOutfit(c, 1236 - cam, y, 36, 1, "sunglasses"); }
        // plantes
        [480, 1360, 2200].forEach((x) => { if (inView(x, 40)) drawPlant(c, x - cam, 1); });
        // scanner à rayons X + portique
        if (inView(SEC_X, 260)) {
            const x = SEC_X - cam;
            c.fillStyle = "#9aa3ad"; c.fillRect(x - 200, GY - 80, 120, 50);
            c.fillStyle = "#6c7680"; c.fillRect(x - 230, GY - 36, 180, 10);
            c.fillStyle = "#2d3137"; c.fillRect(x - 170, GY - 76, 60, 36);
            c.fillStyle = "#1f2227"; for (let k = 0; k < 6; k++) c.fillRect(x - 168 + k * 10, GY - 76, 6, 30);
            c.fillStyle = "#c9ced4"; rr(c, x - 226, GY - 52, 40, 16, 3); c.fill();
            // portique (la trousse passe dessous)
            c.fillStyle = "#b8c0c8";
            c.fillRect(x - 46, GY - 210, 14, 210); c.fillRect(x + 32, GY - 210, 14, 210); c.fillRect(x - 46, GY - 222, 92, 16);
            const ok = city.secFlash > 0;
            c.fillStyle = ok ? "#3ddc84" : "#7b2b2b";
            c.beginPath(); c.arc(x, GY - 230, 8, 0, Math.PI * 2); c.fill();
            if (ok) { c.fillStyle = "rgba(61,220,132,0.18)"; c.fillRect(x - 32, GY - 206, 64, 206); }
        }
        // douaniers crabes, juste après le portique
        drawCrabGuard(c, 1770, "police", -1, 60);
        drawCrabGuard(c, 1900, "police", -1, 60);
        drawCrabGuard(c, 2080, "police", 1, 56);
        // portes coulissantes de sortie
        if (inView(AIR_END - 40, 140)) {
            const x = AIR_END - 40 - cam;
            c.fillStyle = "#6f7a86"; c.fillRect(x - 90, GY - 230, 180, 12);
            const o = city.door * 70;
            c.fillStyle = "rgba(170,210,235,0.55)";
            c.fillRect(x - 80 - o, GY - 218, 78, 218); c.fillRect(x + 2 + o, GY - 218, 78, 218);
            c.strokeStyle = "#6f7a86"; c.lineWidth = 4;
            c.strokeRect(x - 80 - o, GY - 218, 78, 218); c.strokeRect(x + 2 + o, GY - 218, 78, 218);
            c.fillStyle = "#1b5e20"; rr(c, x - 36, GY - 262, 72, 24, 4); c.fill();
            c.fillStyle = "#fff"; c.font = "900 13px 'Segoe UI'"; c.textAlign = "center"; c.fillText(L("SORTIE", "EXIT"), x, GY - 245);
        }
        c.textAlign = "left";
    }
    function drawCrabGuardAt(c, sx, kind, dir, size) {
        const y = V.GY - 96;
        B.drawCrab(c, sx, y, size, V.t, dir);
        A.drawCrabOutfit(c, sx, y, size, dir, kind);
    }
    function hangSign(c, x, wTop, text, col) {
        if (!inView(x, 150)) return;
        const sx = x - V.camX, y = Math.max(8, wTop - 22);
        c.font = "900 14px 'Segoe UI', Arial";
        const w = c.measureText(text).width + 30;
        c.fillStyle = "#4b5563"; c.fillRect(sx - w / 2 + 12, 0, 3, y); c.fillRect(sx + w / 2 - 15, 0, 3, y);
        c.fillStyle = col; rr(c, sx - w / 2, y, w, 30, 5); c.fill();
        c.fillStyle = "#ffd23f"; c.textAlign = "center"; c.fillText(text, sx, y + 20);
        c.textAlign = "left";
    }
    function drawPlant(c, x, k) {
        const GY = V.GY;
        c.fillStyle = "#8d5a3b"; c.fillRect(x - 18 * k, GY - 42 * k, 36 * k, 42 * k);
        c.fillStyle = "#2e7d32";
        for (let i = 0; i < 5; i++) {
            c.beginPath();
            c.ellipse(x + (i - 2) * 9 * k, GY - 62 * k - Math.abs(i - 2) * -6 * k, 9 * k, 26 * k, (i - 2) * 0.35, 0, Math.PI * 2);
            c.fill();
        }
    }
    function drawCityFront(c) {
        const sky = A.skyAt();
        const GY = V.GY, h = V.VH, cam = V.camX;
        // piliers au premier plan dans l'aéroport
        [1180, 2380].forEach((x) => {
            if (!inView(x, 40)) return;
            c.fillStyle = "#3f4752"; c.fillRect(x - 26 - cam, 0, 52, h);
            c.fillStyle = "rgba(255,255,255,0.08)"; c.fillRect(x - 20 - cam, 0, 8, h);
        });
        // poteaux et cordons de la file d'attente
        for (let x = 1380; x <= 1540; x += 80) {
            if (!inView(x, 40)) continue;
            c.fillStyle = "#c9a227"; c.fillRect(x - cam - 3, GY + 20 - 70, 6, 70);
            c.fillStyle = "#555"; c.beginPath(); c.ellipse(x - cam, GY + 22, 12, 4, 0, 0, Math.PI * 2); c.fill();
            if (x < 1540) {
                c.strokeStyle = "#b71c1c"; c.lineWidth = 4;
                c.beginPath(); c.moveTo(x - cam, GY - 44); c.quadraticCurveTo(x + 40 - cam, GY - 20, x + 80 - cam, GY - 44); c.stroke();
            }
        }
        // premier plan de la rue : grands lampadaires et un arbre
        if (inView(3060, 80)) drawLamp(c, 3060 - cam, true, sky);
        if (inView(4620, 80)) drawLamp(c, 4620 - cam, true, sky);
        if (inView(3560, 120)) {
            const x = 3560 - cam;
            c.fillStyle = "#4e342e"; c.fillRect(x - 10, GY - 150, 20, 170);
            c.fillStyle = "#256d2e";
            [[0, -210, 70], [-50, -170, 52], [50, -170, 52], [0, -150, 60]].forEach((b) => { c.beginPath(); c.arc(x + b[0], GY + b[1], b[2], 0, Math.PI * 2); c.fill(); });
            c.fillStyle = "#5d4037"; c.fillRect(x - 44, GY + 12, 88, 26);
        }
        // bulles des douaniers
        if (Math.abs(P.x - 1830) < 180) bubble(1770, L("Contrôle terminé. Bienvenue en Ville !", "Check complete. Welcome to the City!"));
        else if (Math.abs(P.x - 2080) < 150) bubble(2080, L("Circulez, circulez...", "Move along, move along..."));
        if (city.bip > 0) {
            c.save(); c.globalAlpha = c01(city.bip * 2);
            c.fillStyle = "#3ddc84"; c.font = "900 22px 'Segoe UI'"; c.textAlign = "center";
            c.fillText("BIP ✅", SEC_X - cam, GY - 250 - (1 - city.bip) * 30);
            c.restore();
        }
        function bubble(x, t) { C.bubble(c, x - cam, GY - 110, t); }
    }

    /* =====================================================================
       NIVEAU 2 : RÉCEPTION
       ===================================================================== */
    const RW = 1800, R_EXIT = 110, R_STAIRS = 440, R_ELEV = 680, R_BOARD = 930, R_DESK = 1330;
    const R = { elev: 0 };
    function* elevatorSeq(dest) {
        P.frozen = true;
        S().villeElevator++;
        yield () => walkTo(R_ELEV);
        yield () => (R.elev = Math.min(1, R.elev + V.dt * 2.2)) >= 1;
        yield () => (P.alpha = Math.max(0, P.alpha - V.dt * 3)) <= 0;
        yield () => (R.elev = Math.max(0, R.elev - V.dt * 2.2)) <= 0;
        yield 0.25;
        C.startTrans("iris", 1, () => C.goFloor(dest));
    }
    function* stairsSeq(dest) {
        P.frozen = true;
        yield () => walkTo(R_STAIRS);
        yield () => (P.alpha = Math.max(0, P.alpha - V.dt * 3)) <= 0;
        C.startTrans("iris", 0.8, () => C.startStairs(dest));
    }
    LV.reception = {
        w: RW,
        enter() { R.elev = 0; },
        bounds: () => [60, RW - 50],
        arriveByElevator() {
            C.run((function* () {
                P.frozen = true; P.alpha = 0; R.elev = 1;
                yield 0.35;
                yield () => (P.alpha = Math.min(1, P.alpha + V.dt * 3)) >= 1;
                yield () => walkTo(R_ELEV + 90, 180);
                yield () => (R.elev = Math.max(0, R.elev - V.dt * 2.2)) <= 0;
                P.frozen = false;
            })());
        },
        goFloor(dest, viaStairs) { C.run(viaStairs ? stairsSeq(dest) : elevatorSeq(dest)); },
        interacts: [
            { x: R_EXIT, r: 70, pa: 200, label: () => L("Sortir", "Go outside"), act: () => { P.frozen = true; irisTo(() => C.setLevel("city", TOWER_DOOR - 10, -1), 1); } },
            { x: R_STAIRS, r: 60, pa: 210, when: () => C.isOutage(), label: () => L("Prendre les escaliers", "Take the stairs"), act: () => C.openFloorPop(true) },
            // n'apparaît qu'après être passé à la réception, et seulement une fois arrêté devant
            { x: R_ELEV, r: 55, pa: 230, when: () => S().villeReceptionDone && Math.abs(P.vx) < 25,
              label: () => C.isOutage() ? L("⚡ Hors service", "⚡ Out of order") : L("Appeler l'ascenseur", "Call the elevator"),
              act: () => {
                  if (C.isOutage()) { C.showMsg(L("⚡ Coupure de courant : l'ascenseur ne marche pas. Prends les escaliers !", "⚡ Power outage: the elevator is down. Take the stairs!"), 3); B.sfxError(); return; }
                  C.openFloorPop(false);
              } },
            { x: R_BOARD, r: 70, pa: 250, label: () => L("Lire les annonces", "Read the notices"), act: openBoard },
            { x: R_DESK, r: 110, pa: 60, label: () => L("Appuie sur E", "Press E"), act: () => {
                if (!S().villeReceptionDone) { S().villeReceptionDone = true; B.persist(); }
                if (B.openVilleShop("hub")) C.pauseForShop();
            } },
        ],
        draw(c) { drawReception(c); },
        front(c) {
            const GY = V.GY, h = V.VH, cam = V.camX;
            [820, 1580].forEach((x) => {
                if (!inView(x, 40)) return;
                const g = c.createLinearGradient(x - 28 - cam, 0, x + 28 - cam, 0);
                g.addColorStop(0, "#6b4220"); g.addColorStop(0.5, "#a0703f"); g.addColorStop(1, "#5a3718");
                c.fillStyle = g; c.fillRect(x - 28 - cam, 0, 56, h);
                c.fillStyle = "#c9a227"; c.fillRect(x - 32 - cam, GY - 10, 64, 10); c.fillRect(x - 32 - cam, 40, 64, 8);
            });
            if (inView(560, 40)) { c.save(); c.translate(0, 40); drawPlant(c, 560 - cam, 1.3); c.restore(); }
            if (Math.abs(P.x - R_DESK) < 150 && !S().villeReceptionDone) C.bubble(c, R_DESK - cam + 10, GY - 150, L("Bienvenue à La Tour ! Approchez, approchez.", "Welcome to The Tower! Come closer."));
        },
        overlay(c) {
            if (!C.isOutage()) return;
            const ps = C.playerScreen();
            const blink = Math.floor(V.t * 2) % 2;
            C.darkness(c, 0.82, [
                { x: ps.x, y: ps.y, r: 170, a: 0.95 },
                { x: R_EXIT - V.camX, y: V.GY - 250, r: 70, a: 0.8 },
                { x: R_STAIRS - V.camX, y: V.GY - 240, r: 80, a: 0.9 },
                { x: R_ELEV - V.camX, y: V.GY - 250, r: blink ? 40 : 20, a: 0.6 },
            ]);
        },
    };
    function openBoard() {
        const list = C.upcomingEvents();
        const now = Date.now();
        const idx = S().lang === "en" ? 1 : 0;
        const rows = list.length ? list.map((e) => {
            const info = C.EVENTS[e.type];
            const cur = now >= e.start && now < e.end;
            return '<div class="row' + (cur ? " now" : "") + '">' + info.emoji + " " + info.name[idx] +
                (cur ? " — " + L("EN COURS", "NOW") : "") +
                "<small>" + C.fmtDay(e.start) + " · " + C.fmtTime(e.start) + " → " + C.fmtTime(e.end) + "</small>" +
                "<small>" + info.fx[idx] + "</small></div>";
        }).join("") : "<p>" + L("Rien de prévu pour l'instant.", "Nothing planned for now.") + "</p>";
        C.openPop(L("📌 Tableau des annonces", "📌 Notice board"),
            "<p>" + L("Évènements des 5 prochains jours (heure de ton appareil) :", "Events for the next 5 days (your device's time):") + '</p><div class="vl-board">' + rows + "</div>", []);
    }
    function drawReception(c) {
        const GY = V.GY, h = V.VH, w = V.VW, cam = V.camX;
        const sky = A.skyAt();
        const wg = c.createLinearGradient(0, 0, 0, GY);
        wg.addColorStop(0, "#f6dfa6"); wg.addColorStop(1, "#e7bf73");
        c.fillStyle = wg; c.fillRect(0, 0, w, GY);
        // poutres du plafond
        const ceil = Math.max(0, GY - 440);
        c.fillStyle = "#5a3718"; c.fillRect(0, 0, w, ceil + 16);
        for (let x = Math.floor(cam / 160) * 160; x < cam + w + 160; x += 160) { c.fillStyle = "#6b4220"; c.fillRect(x - cam, ceil, 26, 30); }
        c.fillStyle = "#c9a227"; c.fillRect(0, ceil + 16, w, 4);
        // papier peint à motifs
        c.fillStyle = "rgba(160,110,40,0.12)";
        for (let x = Math.floor(cam / 60) * 60; x < cam + w; x += 60) {
            for (let y = ceil + 50; y < GY - 150; y += 60) { c.beginPath(); c.arc(x - cam + ((y / 60) % 2) * 30, y, 5, 0, Math.PI * 2); c.fill(); }
        }
        // lambris
        c.fillStyle = "#8b5a2b"; c.fillRect(0, GY - 130, w, 130);
        c.fillStyle = "#7a4d23";
        for (let x = Math.floor(cam / 120) * 120; x < cam + w; x += 120) { c.strokeStyle = "#6b4220"; c.lineWidth = 3; c.strokeRect(x - cam + 12, GY - 116, 96, 96); }
        c.fillStyle = "#c9a227"; c.fillRect(0, GY - 134, w, 5);
        // sol en parquet
        const fl = c.createLinearGradient(0, GY, 0, h);
        fl.addColorStop(0, "#b67a40"); fl.addColorStop(1, "#8a5528");
        c.fillStyle = fl; c.fillRect(0, GY, w, h - GY);
        c.strokeStyle = "rgba(60,30,10,0.25)"; c.lineWidth = 2;
        for (let r = 0; r < 6; r++) {
            const y = GY + 10 + r * r * 6 + r * 10;
            if (y > h) break;
            c.beginPath(); c.moveTo(0, y); c.lineTo(w, y); c.stroke();
            for (let x = Math.floor(cam / 140) * 140 + (r % 2) * 70; x < cam + w; x += 140) { c.beginPath(); c.moveTo(x - cam, y); c.lineTo(x - cam, y + 10 + r * 8); c.stroke(); }
        }
        // tapis devant la réception
        if (inView(R_DESK, 260)) {
            c.fillStyle = "#a8322a"; c.fillRect(R_DESK - 240 - cam, GY + 14, 480, Math.min(60, h - GY - 20));
            c.strokeStyle = "#e0a030"; c.lineWidth = 3; c.strokeRect(R_DESK - 228 - cam, GY + 22, 456, Math.min(44, h - GY - 36));
        }
        // porte d'entrée + fenêtre sur la rue (ciel réel)
        if (inView(R_EXIT, 80)) {
            const x = R_EXIT - cam;
            c.fillStyle = "#5a3718"; c.fillRect(x - 56, GY - 230, 112, 230);
            c.fillStyle = sky.top; c.fillRect(x - 44, GY - 218, 88, 120);
            c.fillStyle = "#6b4220"; c.fillRect(x - 44, GY - 98, 88, 98);
            c.fillStyle = "#ffd23f"; c.beginPath(); c.arc(x + 32, GY - 70, 5, 0, Math.PI * 2); c.fill();
            c.fillStyle = "#1b5e20"; rr(c, x - 36, GY - 262, 72, 22, 4); c.fill();
            c.fillStyle = "#fff"; c.font = "900 12px 'Segoe UI'"; c.textAlign = "center"; c.fillText(L("SORTIE", "EXIT"), x, GY - 246); c.textAlign = "left";
        }
        if (inView(270, 100)) {
            const x = 270 - cam;
            c.save(); c.beginPath(); c.rect(x - 70, GY - 330, 140, 170); c.clip();
            A.drawSky(c, w, h, GY - 160, sky, { camX: cam });
            A.drawSkyline(c, w, GY - 160, cam, 0.3, 17, { color: A.hazeColor(sky, 0.5), lit: C.isOutage() ? 0 : sky.night, minH: 40, maxH: 150, bw: 50 });
            c.restore();
            c.strokeStyle = "#5a3718"; c.lineWidth = 10; c.strokeRect(x - 70, GY - 330, 140, 170);
            c.lineWidth = 4; c.beginPath(); c.moveTo(x, GY - 330); c.lineTo(x, GY - 160); c.moveTo(x - 70, GY - 245); c.lineTo(x + 70, GY - 245); c.stroke();
            c.fillStyle = "#a8322a"; c.fillRect(x - 86, GY - 344, 20, 200); c.fillRect(x + 66, GY - 344, 20, 200);
        }
        // escaliers
        if (inView(R_STAIRS, 80)) {
            const x = R_STAIRS - cam;
            c.fillStyle = "#6b4220"; c.fillRect(x - 50, GY - 210, 100, 210);
            c.fillStyle = "#3a2410"; c.fillRect(x - 40, GY - 200, 80, 200);
            c.fillStyle = "#8b5a2b";
            for (let k = 0; k < 6; k++) c.fillRect(x - 40 + k * 13, GY - 30 - k * 28, 80 - k * 13, 8);
            c.fillStyle = "#2e3b4e"; rr(c, x - 44, GY - 244, 88, 24, 4); c.fill();
            c.fillStyle = "#fff"; c.font = "900 12px 'Segoe UI'"; c.textAlign = "center"; c.fillText(L("ESCALIERS", "STAIRS"), x, GY - 228); c.textAlign = "left";
        }
        // ascenseur
        if (inView(R_ELEV, 100)) {
            const x = R_ELEV - cam;
            c.fillStyle = "#c9a227"; c.fillRect(x - 70, GY - 240, 140, 240);
            c.fillStyle = "#2b2f36"; c.fillRect(x - 58, GY - 228, 116, 228);
            c.fillStyle = "rgba(255,230,170,0.8)"; c.fillRect(x - 54, GY - 224, 108, 224);
            const o = R.elev * 54;
            const g = c.createLinearGradient(x - 58, 0, x + 58, 0);
            g.addColorStop(0, "#8e959e"); g.addColorStop(0.5, "#d9dde2"); g.addColorStop(1, "#8e959e");
            c.fillStyle = g;
            c.fillRect(x - 58 - o, GY - 228, 58, 228); c.fillRect(x + o, GY - 228, 58, 228);
            c.fillStyle = "rgba(0,0,0,0.3)"; c.fillRect(x - 1, GY - 228, 2, 228 * (1 - R.elev));
            c.fillStyle = "#1a1c20"; rr(c, x - 30, GY - 274, 60, 26, 5); c.fill();
            const out = C.isOutage();
            c.fillStyle = out ? "#552222" : "#ff9a3c"; c.font = "900 14px 'Consolas', monospace"; c.textAlign = "center";
            c.fillText(out ? "--" : (R.elev > 0 ? "0" : "▲ 1"), x, GY - 256);
            c.fillStyle = "#1a1c20"; rr(c, x + 80, GY - 130, 20, 40, 4); c.fill();
            c.fillStyle = out ? "#333" : "#ffd23f"; c.beginPath(); c.arc(x + 90, GY - 118, 5, 0, Math.PI * 2); c.fill(); c.beginPath(); c.arc(x + 90, GY - 102, 5, 0, Math.PI * 2); c.fill();
            c.textAlign = "left";
        }
        // tableau des annonces
        if (inView(R_BOARD, 90)) {
            const x = R_BOARD - cam;
            c.fillStyle = "#5a3718"; c.fillRect(x - 86, GY - 300, 172, 130);
            c.fillStyle = "#c79a5b"; c.fillRect(x - 78, GY - 292, 156, 114);
            const notes = ["#fff59d", "#ffffff", "#b3e5fc", "#ffccbc", "#dcedc8"];
            notes.forEach((col, i) => {
                c.save(); c.translate(x - 56 + (i % 3) * 52, GY - 262 + Math.floor(i / 3) * 50); c.rotate((sr(i * 3) - 0.5) * 0.3);
                c.fillStyle = col; c.fillRect(-20, -18, 40, 36);
                c.fillStyle = "rgba(0,0,0,0.35)"; for (let k = 0; k < 3; k++) c.fillRect(-14, -8 + k * 8, 28, 2);
                c.fillStyle = "#c62828"; c.beginPath(); c.arc(0, -16, 3, 0, Math.PI * 2); c.fill();
                c.restore();
            });
            c.fillStyle = "#3a2410"; c.font = "900 12px 'Segoe UI'"; c.textAlign = "center"; c.fillText(L("ANNONCES", "NOTICES"), x, GY - 306); c.textAlign = "left";
            if (C.activeEvent()) { c.fillStyle = "#ff3b3b"; c.beginPath(); c.arc(x + 80, GY - 296, 8, 0, Math.PI * 2); c.fill(); }
        }
        // horloge à l'heure réelle
        if (inView(1110, 50)) {
            const x = 1110 - cam, y = GY - 290;
            const d = new Date();
            c.fillStyle = "#5a3718"; c.beginPath(); c.arc(x, y, 34, 0, Math.PI * 2); c.fill();
            c.fillStyle = "#fff8e6"; c.beginPath(); c.arc(x, y, 28, 0, Math.PI * 2); c.fill();
            c.strokeStyle = "#3a2410"; c.lineCap = "round";
            const hA = ((d.getHours() % 12) + d.getMinutes() / 60) / 12 * Math.PI * 2 - Math.PI / 2;
            const mA = (d.getMinutes() + d.getSeconds() / 60) / 60 * Math.PI * 2 - Math.PI / 2;
            c.lineWidth = 4; c.beginPath(); c.moveTo(x, y); c.lineTo(x + Math.cos(hA) * 15, y + Math.sin(hA) * 15); c.stroke();
            c.lineWidth = 2.5; c.beginPath(); c.moveTo(x, y); c.lineTo(x + Math.cos(mA) * 22, y + Math.sin(mA) * 22); c.stroke();
            c.lineCap = "butt";
        }
        // réception : crabe réceptionniste derrière le comptoir
        if (inView(R_DESK, 200)) {
            const x = R_DESK - cam;
            c.fillStyle = "#c9a227"; c.font = "900 22px Georgia, serif"; c.textAlign = "center";
            c.fillText(L("RÉCEPTION", "RECEPTION"), x, GY - 250);
            c.fillStyle = "#5a3718"; c.fillRect(x - 90, GY - 236, 180, 70);
            for (let k = 0; k < 8; k++) { c.fillStyle = "#c9a227"; c.fillRect(x - 80 + k * 21, GY - 222, 4, 10); c.fillRect(x - 81 + k * 21, GY - 212, 6, 12); }
            const cy = GY - 128;
            B.drawCrab(c, x + 10, cy, 60, V.t, -1);
            A.drawCrabOutfit(c, x + 10, cy, 60, -1, "bowtie");
            const dg = c.createLinearGradient(0, GY - 110, 0, GY);
            dg.addColorStop(0, "#9b6534"); dg.addColorStop(1, "#6b4220");
            c.fillStyle = dg; c.fillRect(x - 150, GY - 110, 300, 110);
            c.fillStyle = "#e9e4da"; c.fillRect(x - 158, GY - 118, 316, 12);
            c.strokeStyle = "#c9a227"; c.lineWidth = 3; c.strokeRect(x - 136, GY - 96, 272, 80);
            c.fillStyle = "#c9a227"; c.beginPath(); c.arc(x - 90, GY - 124, 9, Math.PI, 0); c.fill(); c.fillRect(x - 100, GY - 125, 20, 3);
            c.fillStyle = "#2b2f36"; c.fillRect(x + 60, GY - 160, 54, 38); c.fillRect(x + 83, GY - 122, 8, 6);
            c.fillStyle = "#4fc3f7"; c.fillRect(x + 64, GY - 156, 46, 30);
            c.textAlign = "left";
        }
        // coin salon
        if (inView(1680, 140)) {
            const x = 1680 - cam;
            c.fillStyle = "#7b3f1d"; rr(c, x - 90, GY - 80, 180, 60, 14); c.fill();
            c.fillStyle = "#8d4a24"; rr(c, x - 100, GY - 112, 200, 44, 14); c.fill();
            c.fillStyle = "#5a2d12"; c.fillRect(x - 84, GY - 22, 10, 22); c.fillRect(x + 74, GY - 22, 10, 22);
            drawPlant(c, x + 130, 1.1);
        }
        // suspensions (lumière chaude)
        if (!C.isOutage()) {
            [350, 900, 1330, 1700].forEach((lx) => {
                if (!inView(lx, 200)) return;
                const x = lx - cam, y = ceil + 110;
                c.strokeStyle = "#3a2410"; c.lineWidth = 2; c.beginPath(); c.moveTo(x, ceil + 20); c.lineTo(x, y - 16); c.stroke();
                c.fillStyle = "#c9a227"; c.beginPath(); c.moveTo(x - 26, y); c.lineTo(x - 12, y - 18); c.lineTo(x + 12, y - 18); c.lineTo(x + 26, y); c.fill();
                const g = c.createRadialGradient(x, y + 4, 2, x, y + 4, 230);
                g.addColorStop(0, "rgba(255,220,140,0.45)"); g.addColorStop(1, "rgba(255,220,140,0)");
                c.fillStyle = g; c.fillRect(x - 230, y - 20, 460, 460);
            });
        }
    }

    /* =====================================================================
       NIVEAU 3 : PIÈCE NOIRE (mode histoire)
       ===================================================================== */
    const DW = 1100, D_DOOR = 90, D_SWITCH = 1010, D_PORTAL = 550, D_PORTAL_ALT = 175, D_R = 118;
    function newStory() {
        return { light: false, stage: 0, answer1: "", answer2: "", escaped: false, quake: false, fight: null, boss: null, buildRise: 0, bgPortal: null, escape: null, sword: null, AW: 480, introDone: false };
    }
    LV.darkroom = {
        w: DW, story: true,
        enter() {
            if (!V.story) V.story = newStory();
            V.allowDash = true;
            if (!V.story.light) C.showMsg(L("Il fait tout noir... Trouve l'interrupteur.", "It's pitch dark... Find the light switch."), 5);
        },
        bounds: () => [50, DW - 50],
        interacts: [
            { x: D_DOOR, r: 70, pa: 210, label: () => L("Sortir", "Leave"), act: () => {
                P.frozen = true;
                if (V.story.escaped) {
                    // l'écran reste noir : générique
                    const ps = C.playerScreen();
                    C.startTrans("irisHold", 1.4, () => C.startCredits(), { x: ps.x, y: ps.y });
                } else {
                    irisTo(() => { C.setLevel("reception", R_ELEV, 1); LV.reception.arriveByElevator(); }, 1);
                }
            } },
            { x: D_SWITCH, r: 60, pa: 170, when: () => !V.story.light, label: () => L("Interrupteur", "Light switch"), act: () => {
                V.story.light = true;
                C.hideMsg();
                B.beep(1200, 0.04, "square", 0.08);
                setTimeout(() => B.beep(60, 0.5, "sawtooth", 0.05), 80);
            } },
            { x: D_PORTAL, r: 70, pa: 330, when: () => V.story.light, label: () => V.story.escaped ? L("Examiner le portail", "Examine the portal") : L("Entrer dans le portail", "Enter the portal"), act: () => {
                if (V.story.escaped) { C.showMsg(L("ça a l'air plutôt inutile maintenant...", "it looks rather useless now..."), 3.5); return; }
                C.run(enterPortalSeq());
            } },
        ],
        update(dt) {
            if (V.story.escaped && Math.random() < dt * 14) {
                const a = Math.random() * Math.PI * 2;
                C.spawn(D_PORTAL + Math.cos(a) * D_R, D_PORTAL_ALT + Math.sin(a) * D_R, 1, Math.random() < 0.6
                    ? { life: 1.6, color: "110,110,115", size: 6, grow: 10, g: -40, up: 30, vx: 30 }
                    : { life: 0.35, color: "255,210,90", size: 2, g: 500, up: 160, vx: 260 });
            }
        },
        draw(c) {
            const GY = V.GY, w = V.VW, h = V.VH, cam = V.camX, st = V.story;
            c.fillStyle = "#6f737b"; c.fillRect(0, 0, w, GY);
            c.fillStyle = "rgba(0,0,0,0.1)";
            for (let i = 0; i < 12; i++) { c.beginPath(); c.ellipse(sr(i) * DW - cam, GY * (0.2 + sr(i * 3) * 0.6), 40 + sr(i * 5) * 60, 20 + sr(i * 7) * 30, 0, 0, Math.PI * 2); c.fill(); }
            c.fillStyle = "#4b4e55"; c.fillRect(0, GY, w, h - GY);
            c.fillStyle = "#3a3c42"; c.fillRect(0, GY - 4, w, 6);
            const ceil = Math.max(0, GY - 450);
            c.fillStyle = "#55585f"; c.fillRect(0, 0, w, ceil + 10);
            // porte métallique
            const dx = D_DOOR - cam;
            c.fillStyle = "#3d4148"; c.fillRect(dx - 50, GY - 220, 100, 220);
            c.fillStyle = "#565b63"; c.fillRect(dx - 42, GY - 212, 84, 212);
            c.fillStyle = "#b0b5bd"; c.fillRect(dx + 26, GY - 110, 10, 18);
            // interrupteur (petite LED qui aide à le trouver)
            const sx = D_SWITCH - cam;
            c.fillStyle = "#d9d6cc"; rr(c, sx - 12, GY - 150, 24, 36, 4); c.fill();
            c.fillStyle = st.light ? "#888" : "#555"; c.fillRect(sx - 4, GY - 144 + (st.light ? 0 : 12), 8, 12);
            c.fillStyle = st.light ? "#3ddc84" : "#ff5a3c"; c.beginPath(); c.arc(sx, GY - 120, 2.5, 0, Math.PI * 2); c.fill();
            // lampe unique au plafond
            const lx = DW / 2 - cam;
            c.strokeStyle = "#222"; c.lineWidth = 2; c.beginPath(); c.moveTo(lx, ceil); c.lineTo(lx, ceil + 60); c.stroke();
            c.fillStyle = st.light ? "#fff3b0" : "#555"; c.beginPath(); c.arc(lx, ceil + 70, 11, 0, Math.PI * 2); c.fill();
            if (st.light) {
                c.save();
                c.globalCompositeOperation = "lighter";
                const g = c.createRadialGradient(lx, ceil + 70, 6, lx, ceil + 70, GY - ceil + 80);
                g.addColorStop(0, "rgba(255,225,120,0.45)"); g.addColorStop(0.5, "rgba(255,210,100,0.14)"); g.addColorStop(1, "rgba(255,200,90,0)");
                c.fillStyle = g;
                c.beginPath(); c.moveTo(lx - 12, ceil + 70); c.lineTo(lx - 520, h); c.lineTo(lx + 520, h); c.lineTo(lx + 12, ceil + 70); c.fill();
                c.restore();
            }
            A.drawPortal(c, D_PORTAL - cam, GY - D_PORTAL_ALT, D_R, V.t, st.escaped, st.light ? 1 : 0.08);
        },
        overlay(c) {
            if (V.story.light) return;
            const ps = C.playerScreen();
            C.darkness(c, 0.97, [
                { x: ps.x, y: ps.y, r: 95, a: 0.85 },
                { x: D_SWITCH - V.camX, y: V.GY - 120, r: 22, a: 0.9 },
                { x: D_PORTAL - V.camX, y: V.GY - D_PORTAL_ALT, r: 60, a: 0.12 },
            ]);
        },
    };
    function* enterPortalSeq() {
        P.frozen = true;
        yield () => walkTo(D_PORTAL);
        let k = 0;
        yield () => {
            k += V.dt / 0.8;
            P.alt = lerp(0, D_PORTAL_ALT - 20, eIO(c01(k)));
            P.rot += V.dt * 8 * k;
            P.scale = 1 - 0.8 * c01(k);
            return k >= 1;
        };
        P.visible = false;
        C.startTrans("glitch", 3, () => { C.setLevel("glitch", 380, 1); LV.glitch.arrive(); });
    }

    /* =====================================================================
       NIVEAU 4 : MONDE BUGÉ + BOSS
       ===================================================================== */
    const GW = 5600, E1 = 1700, E2 = 3200, AC = 4700, RET_X = 140;
    const BOSS_HP = 60, CIRCLE_T = 1.5;
    LV.glitch = {
        w: GW, story: true,
        enter() {
            const st = V.story;
            P.invert = st.stage >= 2 && st.stage < 3;
            V.npcs = [];
        },
        arrive() {
            const st = V.story;
            if (!st.introDone) { C.run(introSeq()); return; }
            C.resetPlayer(300, 1);
            C.snapCam();
            P.invert = st.stage >= 2 && st.stage < 3;
        },
        bounds() {
            const st = V.story;
            if (st.fight || (st.stage >= 3.5 && st.stage < 5)) return [AC - st.AW + 46, AC + st.AW - 46];
            return [60, GW - 60];
        },
        plats() {
            const st = V.story;
            if (!(st.buildRise > 0.5 && st.stage < 5)) return [];
            const AW = st.AW;
            return [
                { x: AC, alt: 110, w: 190 },
                { x: AC - AW * 0.55, alt: 220, w: 170 },
                { x: AC + AW * 0.55, alt: 220, w: 170 },
                { x: AC, alt: 330, w: 170 },
            ];
        },
        interacts: [],
        update(dt) {
            const st = V.story;
            if (Math.random() < dt * 0.3) V.glitch = Math.max(V.glitch, 0.22);
            if (!C.busy() && !C.transBusy()) {
                if (st.stage === 1 && P.x > E1 - 240) C.run(enc1());
                else if (st.stage === 2 && P.x > E2 - 240) C.run(enc2());
                else if (st.stage === 3 && P.x > AC - Math.min(V.VW, 1040) / 2 * 0.55) C.run(enc3());
                else if (st.stage < 3.5 && st.stage >= 1 && P.x < RET_X + 60) {
                    P.frozen = true;
                    C.startTrans("glitchQuick", 1.2, () => C.setLevel("darkroom", D_PORTAL + 110, 1));
                }
            }
            if (st.fight) updateFight(dt);
            if (st.escape) updateEscape(dt);
            if (st.bgPortal) st.bgPortal.t = (st.bgPortal.t || 0) + dt;
            if (st.fight || (st.stage >= 3.5 && st.stage < 5)) V.camLock = AC - V.VW / 2;
            else V.camLock = null;
        },
        draw(c) { drawGlitchWorld(c); },
        drawMid(c) { drawFightMid(c); },
        front(c) { drawGlitchFront(c); },
        overlay(c) {
            c.fillStyle = "rgba(0,0,0,0.06)";
            for (let y = 0; y < V.VH; y += 4) c.fillRect(0, y, V.VW, 1);
        },
        hud(c) { drawFightHud(c); },
    };
    function* introSeq() {
        const st = V.story;
        P.frozen = true;
        P.rot = Math.PI; // allongée sur le dos
        yield 1.3;
        P.bang = 1;
        B.beep(880, 0.08, "square", 0.08);
        yield 0.8;
        P.va = 480;
        let k = 0;
        yield () => { k += V.dt / 0.5; P.rot = lerp(Math.PI, 0, eOut(c01(k))); return k >= 1; };
        P.rot = 0;
        yield () => (P.bang = Math.max(0, P.bang - V.dt * 2)) <= 0;
        let closed = false;
        C.openPop(L("🧭 Commandes", "🧭 Controls"),
            "<p>" + L("💻 Conseil : ce passage se joue bien mieux sur ordinateur (les commandes tactiles restent disponibles).", "💻 Tip: this part plays much better on a computer (touch controls are still available).") + "</p>" +
            '<div class="vl-keys">' +
            "<b>Q / D · ← →</b><span>" + L("se déplacer", "move") + "</span>" +
            "<b>Z · Espace · ↑</b><span>" + L("sauter", "jump") + "</span>" +
            "<b>S · ↓</b><span>" + L("descendre d'une plateforme", "drop through a platform") + "</span>" +
            "<b>X</b><span>" + L("dash (recharge 3 s)", "dash (3 s cooldown)") + "</span>" +
            "<b>E</b><span>" + L("interagir", "interact") + "</span>" +
            "<b>" + L("Clic gauche", "Left click") + "</b><span>" + L("attaquer (quand tu as une arme)", "attack (once you have a weapon)") + "</span>" +
            "</div><p>" + L("À gauche : retour à la pièce du portail. À droite : ...l'inconnu.", "Left: back to the portal room. Right: ...the unknown.") + "</p>",
            [{ label: L("C'est parti", "Let's go"), act: () => { closed = true; } }], { onClose: () => { closed = true; } });
        yield () => closed;
        st.introDone = true;
        st.stage = 1;
        P.frozen = false;
    }
    function* fadeNpc(n, to, speed) {
        yield () => { n.alpha = to > n.alpha ? Math.min(to, n.alpha + V.dt * (speed || 3)) : Math.max(to, n.alpha - V.dt * (speed || 3)); return n.alpha === to; };
    }
    function* enc1() {
        const st = V.story;
        st.stage = 1.5;
        P.frozen = true; P.vx = 0;
        const n = { x: E1, alt: 0, dir: -1, variant: "inverted", alpha: 0, say: "" };
        V.npcs = [n];
        V.glitch = 0.7;
        yield* fadeNpc(n, 1);
        yield 0.6;
        let pick = -1;
        const o1 = [L("Qui es-tu ?", "Who are you?"), L("Tu es... moi ?", "Are you... me?")];
        C.showChoices(o1, (i) => { pick = i; });
        yield () => pick >= 0;
        st.answer1 = o1[pick];
        P.say = o1[pick]; P.sayT = 2;
        yield 2.1;
        n.say = "...";
        yield 1.8;
        n.say = "";
        pick = -1;
        const o2 = [L("Hein ? Réponds !", "Huh? Answer me!"), L("Tu parles ?", "Can you even talk?")];
        C.showChoices(o2, (i) => { pick = i; });
        yield () => pick >= 0;
        st.answer2 = o2[pick];
        P.say = o2[pick]; P.sayT = 2;
        yield 2.1;
        n.say = "...";
        yield 1.6;
        n.say = "";
        yield* violentGlitch(() => { P.invert = true; V.npcs = []; });
        st.stage = 2;
        P.frozen = false;
    }
    function* enc2() {
        const st = V.story;
        st.stage = 2.5;
        P.frozen = true; P.vx = 0;
        const n = { x: E2, alt: 0, dir: -1, variant: "base", alpha: 0, say: "" };
        V.npcs = [n];
        V.glitch = 0.7;
        yield* fadeNpc(n, 1);
        yield 0.5;
        // mot pour mot ce que le joueur lui a dit : ses deux réponses, dans l'ordre
        n.say = st.answer1;
        yield 2.6;
        n.say = "";
        yield 0.4;
        n.say = st.answer2;
        yield 2.6;
        n.say = "";
        yield 1.1; // le joueur ne répond rien
        yield* violentGlitch(() => { P.invert = false; V.npcs = []; });
        st.stage = 3;
        P.frozen = false;
    }
    function* enc3() {
        const st = V.story;
        st.stage = 3.5;
        st.AW = Math.min(V.VW, 1040) / 2;
        P.frozen = true; P.vx = 0;
        const n = { x: AC + 70, alt: 0, dir: -1, variant: "player", alpha: 0, say: "", rot: 0, size: TS };
        V.npcs = [n];
        yield* fadeNpc(n, 1);
        yield 0.5;
        // mêmes 4 choix qu'à la première rencontre ; l'autre ne répond rien,
        // puis lâche seulement : « Et maintenant qui est qui ? »
        const rounds = [
            [L("Qui es-tu ?", "Who are you?"), L("Tu es... moi ?", "Are you... me?")],
            [L("Hein ? Réponds !", "Huh? Answer me!"), L("Tu parles ?", "Can you even talk?")],
        ];
        for (const opts of rounds) {
            let pick = -1;
            C.showChoices(opts, (i) => { pick = i; });
            yield () => pick >= 0;
            P.say = opts[pick]; P.sayT = 2;
            yield 2.2;
        }
        n.say = L("Et maintenant qui est qui ?", "And now, who is who?");
        yield 2.8;
        n.say = "";
        // un portail grandit en arrière-plan et l'aspire doucement
        st.bgPortal = { x: AC + 70, alt: 170, R: 0, t: 0 };
        B.beep(90, 1.2, "sine", 0.08);
        yield hold(1.2, () => { st.bgPortal.R = lerp(st.bgPortal.R, 140, Math.min(1, V.dt * 3)); });
        let k = 0;
        yield () => {
            k += V.dt / 2.4;
            const e = eIO(c01(k));
            n.alt = lerp(0, st.bgPortal.alt - 10, e);
            n.rot += V.dt * (2 + k * 10);
            n.size = TS * (1 - 0.9 * e);
            if (Math.random() < 0.5) C.spawn(n.x, n.alt + 12, 1, { life: 0.6, color: "120,255,160", size: 3, g: 0, up: 40, vx: 80 });
            return k >= 1;
        };
        V.npcs = [];
        yield 2; // disparue
        // elle ressort, trois fois plus grande, un building de chaque côté
        V.glitch = 1.3; V.shake = 18;
        glitchSfx();
        const boss = { x: AC + 70, alt: 30, baseAlt: 30, dir: -1, variant: "player", alpha: 1, say: "", rot: 0, size: TS * 3, glow: "rgba(255,40,70,0.95)", flash: 0, hurt: 0, big: true, dark: true };
        st.boss = boss;
        V.npcs = [boss];
        let g = 0;
        yield () => {
            g += V.dt / 0.9;
            boss.size = TS * 3 * eOut(c01(g));
            st.buildRise = eOut(c01(g * 1.1));
            if (st.bgPortal) st.bgPortal.R *= 1 - V.dt * 2;
            return g >= 1;
        };
        st.bgPortal = null;
        yield 0.4;
        const txt = L("ON VA VOIR ÇA", "LET'S SEE ABOUT THAT");
        for (let i = 1; i <= txt.length; i++) {
            boss.say = txt.slice(0, i);
            if (txt[i - 1] !== " ") B.beep(140 + i * 12, 0.05, "square", 0.05);
            yield 0.13;
        }
        yield 1.2;
        boss.say = "";
        // l'épée apparaît dans la main, les cœurs et la barre de vie aussi
        P.sword = true;
        C.spawn(P.x + P.dir * 26, P.alt + 30, 24, { life: 0.8, color: "255,240,160", size: 3, g: -20, up: 90, vx: 160 });
        B.sfxBuy();
        startFight();
        P.frozen = false;
    }
    function freshFight() {
        return { hp: BOSS_HP, circles: [], atkT: 1.6, moveT: 0.5, tx: AC + 100, ta: 60, streak: 0, streakT: 0, pending: -1, over: false };
    }
    function startFight() {
        const st = V.story;
        st.fight = freshFight();
        P.hearts = 5;
        S().villeBossAttempts++;
        B.persist();
        C.pauseWorldMusic();
        C.playA(C.aBoss, true);
    }
    function circleRadius() { return clamp(V.VW / 6, 110, 185); }
    function hurtPlayer() {
        const f = V.story.fight;
        if (P.hurt > 0 || !f || f.over) return;
        P.hearts--;
        P.hurt = 1.1;
        P.va = 380;
        V.shake = 14;
        S().villeHeartsLost++;
        B.sfxCrash();
        if (P.hearts <= 0) { f.over = true; C.run(defeatSeq()); }
    }
    function updateFight(dt) {
        const st = V.story, f = st.fight, b = st.boss;
        if (!f || !b) return;
        const k = C.keys();
        const AW = st.AW;
        b.hurt = Math.max(0, b.hurt - dt);
        b.flash = Math.max(0, b.flash - dt * 5);
        if (!f.over) {
            // déplacement : le boss choisit où aller (surtout près du joueur)
            f.moveT -= dt;
            if (f.moveT <= 0) {
                if (Math.random() < 0.65) { f.tx = P.x + (Math.random() - 0.5) * 160; f.ta = clamp(P.alt + 20 + Math.random() * 60, 20, 300); }
                else { f.tx = AC + (Math.random() - 0.5) * AW * 1.3; f.ta = 30 + Math.random() * 270; }
                f.tx = clamp(f.tx, AC - AW + 110, AC + AW - 110);
                f.moveT = 2 + Math.random() * 1.6;
            }
            b.x = lerp(b.x, f.tx, Math.min(1, dt * 1.4));
            b.baseAlt = lerp(b.baseAlt, f.ta, Math.min(1, dt * 1.4));
            b.alt = b.baseAlt + Math.sin(V.t * 2.2) * 8;
            b.dir = P.x < b.x ? -1 : 1;
            // attaques : cercles rouges (1,5 s pour sortir de la zone)
            f.atkT -= dt;
            if (f.atkT <= 0) {
                const ratio = f.hp / BOSS_HP;
                const n = ratio > 0.65 ? 1 : ratio > 0.3 ? 2 : 3;
                const r = circleRadius();
                f.circles.push({ x: P.x, alt: P.alt + 20, r, t: 0, boom: 0 });
                for (let i = 1; i < n; i++) {
                    const rx = i === 1 ? P.x + P.vx * 0.9 + (Math.random() - 0.5) * 120 : AC + (Math.random() - 0.5) * AW * 1.6;
                    f.circles.push({ x: clamp(rx, AC - AW, AC + AW), alt: 20 + Math.random() * 260, r, t: 0, boom: 0 });
                }
                B.beep(220, 0.2, "sawtooth", 0.05);
                f.atkT = lerp(1.1, 2.0, ratio);
            }
        }
        f.circles.forEach((ci) => {
            ci.t += dt;
            if (ci.t >= CIRCLE_T && !ci.boom) {
                ci.boom = 0.001;
                B.beep(70, 0.25, "square", 0.08);
                if (Math.hypot(P.x - ci.x, P.alt + 20 - ci.alt) < ci.r && !f.over) hurtPlayer();
                C.spawn(ci.x, ci.alt, 14, { life: 0.5, color: "255,70,60", size: 4, g: 200, up: 200, vx: 300 });
            }
            if (ci.boom) ci.boom += dt;
        });
        f.circles = f.circles.filter((ci) => !ci.boom || ci.boom < 0.3);
        // coup d'épée (clic gauche / F / bouton ⚔️)
        if (k.pAtk && P.sword && P.swingCd <= 0 && !P.frozen) {
            P.swing = 0.22; P.swingCd = 0.32; f.pending = 0.06;
            B.beep(900, 0.06, "triangle", 0.05);
        }
        if (f.pending > 0) {
            f.pending -= dt;
            if (f.pending <= 0) {
                f.pending = -1;
                const sx = P.x + P.dir * 58, sa = P.alt + 28;
                const bx = b.x, ba = b.alt + b.size * 0.19;
                if (Math.abs(sx - bx) < 60 + b.size * 0.37 && Math.abs(sa - ba) < 58 + b.size * 0.21 && b.hurt <= 0 && !f.over) {
                    f.hp--;
                    b.hurt = 0.16; b.flash = 1;
                    b.x += P.dir * 26;
                    S().villeSwordHits++;
                    V.shake = Math.max(V.shake, 5);
                    B.beep(300, 0.08, "square", 0.07);
                    C.spawn(bx, ba, 10, { life: 0.5, color: "255,90,90", size: 3, g: 300, up: 180, vx: 260, square: true });
                    f.streak++; f.streakT = 1.3;
                    if (f.streak >= 5) {
                        // trop de coups d'affilée : il se téléporte de l'autre côté
                        f.streak = 0;
                        V.glitch = 0.8; glitchSfx();
                        b.x = P.x < AC ? AC + AW * 0.6 : AC - AW * 0.6;
                        b.baseAlt = 230; f.tx = b.x; f.ta = 230; f.moveT = 1.6;
                    }
                    if (f.hp <= 0) { f.over = true; f.circles = []; C.run(victorySeq()); }
                }
            }
        }
        f.streakT -= dt;
        if (f.streakT <= 0) f.streak = 0;
    }
    function* defeatSeq() {
        const st = V.story;
        P.frozen = true;
        st.fight.circles = [];
        yield hold(0.8, () => { V.glitch = Math.max(V.glitch, 1.2); });
        C.showMsg(L("Réessaie !", "Try again!"), 2);
        yield 1.2;
        C.resetPlayer(AC - st.AW * 0.6, 1);
        P.sword = true;
        st.boss.x = AC + st.AW * 0.4; st.boss.baseAlt = 60;
        startFight();
        P.frozen = false;
    }
    function* victorySeq() {
        const st = V.story, b = st.boss;
        P.frozen = true;
        b.glow = "rgba(120,120,130,0.6)";
        yield () => {
            b.alt = Math.max(0, b.alt - 520 * V.dt);
            b.rot = lerp(b.rot, Math.PI, Math.min(1, V.dt * 4));
            return b.alt <= 0;
        };
        b.rot = Math.PI;
        V.shake = 18;
        B.sfxLand();
        yield 1;
        b.big = false;
        const lines = [
            L("comment ?...", "how?..."),
            L("j'étais pourtant sûr...", "I was so sure..."),
            L("mais tu as gagné je te l'accorde", "but you won, I'll give you that"),
            L("Adieu ", "Farewell ") + C.pseudo() + "...",
        ];
        for (const line of lines) {
            for (let i = 1; i <= line.length; i++) {
                b.say = line.slice(0, i);
                const ch = line[i - 1];
                yield ch === "." ? 0.3 : ch === " " ? 0.1 : 0.04 + Math.random() * 0.2; // lettre par lettre, irrégulier
            }
            yield 1.4;
        }
        b.say = "";
        // désintégration
        let k = 0;
        yield () => {
            k += V.dt / 2.2;
            b.alpha = 1 - c01(k);
            for (let i = 0; i < 4; i++) C.spawn(b.x + (Math.random() - 0.5) * b.size * 0.7, b.alt + b.size * 0.19 + (Math.random() - 0.5) * 40, 1, { life: 1.4, color: Math.random() < 0.5 ? "210,210,215" : "255,80,90", size: 5, g: -90, up: 50, vx: 60, square: true });
            return k >= 1;
        };
        V.npcs = [];
        st.boss = null;
        yield 0.8;
        // tout se met à trembler : il faut sortir d'ici, l'épée reste là
        st.quake = true;
        B.beep(50, 1.5, "sawtooth", 0.08);
        C.showMsg(L("je ferais mieux de sortir d'ici", "I'd better get out of here"), 0);
        P.sword = false;
        st.sword = { x: P.x + P.dir * 30 };
        st.fight = null;
        st.stage = 5;
        st.escape = { voidX: AC + st.AW + 140 };
        P.frozen = false;
    }
    function updateEscape(dt) {
        const st = V.story, E = st.escape;
        const d = E.voidX - P.x;
        const sp = d > 620 ? 600 : d > 330 ? 330 : 285;
        E.voidX -= sp * dt;
        if (Math.random() < dt * 6) C.spawn(V.camX + Math.random() * V.VW, 430, 1, { life: 1.4, color: "90,80,120", size: 7, g: 500, up: 0, vx: 20, square: true });
        if (C.busy() || C.transBusy()) return;
        if (E.voidX < P.x + 24) {
            C.run((function* () {
                P.frozen = true;
                yield hold(0.6, () => { V.glitch = Math.max(V.glitch, 1.4); });
                C.resetPlayer(AC - 60, -1);
                E.voidX = AC + st.AW + 220;
                C.snapCam();
                P.frozen = false;
            })());
        } else if (P.x < RET_X + 70) {
            // de justesse !
            P.frozen = true;
            C.startTrans("glitchQuick", 1, () => {
                st.quake = false;
                st.escaped = true;
                st.escape = null;
                st.light = true;
                C.hideMsg();
                C.setLevel("darkroom", D_PORTAL - 60, -1);
                P.va = 520; P.vx = -260;
                V.shake = 22;
                B.sfxCrash();
                for (let i = 0; i < 30; i++) C.spawn(D_PORTAL, D_PORTAL_ALT, 1, { life: 0.6, color: "255,210,90", size: 2, g: 500, up: 260, vx: 520 });
            });
        }
    }
    const GPROPS = ["desk", "chair", "cabinet", "monitor", "lamp", "door", "chair", "desk"];
    function drawOfficeProp(c, kind, x, y, s) {
        c.save();
        c.translate(x, y);
        c.scale(s, s);
        switch (kind) {
            case "desk":
                c.fillStyle = "#7b5a3a"; c.fillRect(-60, -74, 120, 10);
                c.fillStyle = "#5e4329"; c.fillRect(-56, -64, 10, 64); c.fillRect(46, -64, 10, 64); c.fillRect(10, -64, 40, 44);
                c.fillStyle = "#c9a227"; c.fillRect(26, -46, 8, 3);
                break;
            case "chair":
                c.fillStyle = "#2f3542"; c.fillRect(-22, -44, 44, 8); c.fillRect(14, -96, 8, 56);
                c.fillStyle = "#57606f"; c.fillRect(-2, -36, 4, 26); c.fillRect(-20, -10, 40, 4);
                break;
            case "cabinet":
                c.fillStyle = "#8a8f99"; c.fillRect(-24, -110, 48, 110);
                c.fillStyle = "#6f747d"; for (let k = 0; k < 3; k++) { c.fillRect(-20, -104 + k * 36, 40, 30); }
                c.fillStyle = "#ccc"; for (let k = 0; k < 3; k++) c.fillRect(-8, -92 + k * 36, 16, 4);
                break;
            case "monitor":
                c.fillStyle = "#1e2229"; c.fillRect(-30, -58, 60, 40);
                c.fillStyle = Math.floor(V.t * 3 + x) % 5 ? "#39d1c3" : "#ff4d8d"; c.fillRect(-26, -54, 52, 32);
                c.fillStyle = "#1e2229"; c.fillRect(-4, -18, 8, 14); c.fillRect(-16, -4, 32, 4);
                break;
            case "lamp":
                c.fillStyle = "#3a3f48"; c.fillRect(-3, -140, 6, 140); c.fillRect(-14, -4, 28, 4);
                c.fillStyle = "#e0c060"; c.beginPath(); c.moveTo(-18, -128); c.lineTo(-8, -150); c.lineTo(8, -150); c.lineTo(18, -128); c.fill();
                break;
            case "door":
                c.fillStyle = "#6b4220"; c.fillRect(-36, -170, 72, 170);
                c.fillStyle = "#4e2f16"; c.fillRect(-28, -162, 56, 162);
                c.fillStyle = "#c9a227"; c.beginPath(); c.arc(18, -80, 4, 0, Math.PI * 2); c.fill();
                break;
        }
        c.restore();
    }
    function hBuilding(c, x, y, w, h, rot, col, winCol) {
        c.save();
        c.translate(x, y);
        c.rotate(rot);
        c.fillStyle = col; c.fillRect(-w / 2, -h / 2, w, h);
        c.fillStyle = winCol;
        for (let i = -w / 2 + 10; i < w / 2 - 14; i += 22) for (let j = -h / 2 + 10; j < h / 2 - 12; j += 18) c.fillRect(i, j, 10, 8);
        c.restore();
    }
    function drawGlitchWorld(c) {
        const GY = V.GY, w = V.VW, h = V.VH, cam = V.camX, st = V.story;
        const bg = c.createLinearGradient(0, 0, 0, h);
        bg.addColorStop(0, "#140b26"); bg.addColorStop(0.6, "#1d2a3c"); bg.addColorStop(1, "#0d1119");
        c.fillStyle = bg; c.fillRect(0, 0, w, h);
        // buildings couchés qui flottent au loin
        for (let i = -2; i < 14; i++) {
            const par = 0.15 + (i % 3) * 0.12;
            const span = 520;
            const x = ((i * span - cam * par) % (span * 12) + span * 12) % (span * 12) - span;
            if (x < -300 || x > w + 300) continue;
            const y = GY * (0.2 + sr(i * 3.3) * 0.55);
            hBuilding(c, x, y, 220 + sr(i) * 160, 60 + sr(i * 2) * 50, (sr(i * 5) - 0.5) * 0.5 + (i % 4 === 0 ? Math.PI / 2 : 0), "rgba(70,60,110," + (0.35 + par) + ")", "rgba(120,255,230,0.18)");
        }
        // plafond : un autre building à l'horizontale
        const ceil = GY - 430;
        if (ceil > -30) {
            c.fillStyle = "#2c2644"; c.fillRect(0, -10, w, ceil + 10);
            c.fillStyle = "#554a7a"; c.fillRect(0, ceil - 8, w, 8);
            c.fillStyle = "#1c1830";
            for (let x = Math.floor(cam / 36) * 36; x < cam + w; x += 36) for (let y = ceil - 26; y > -10; y -= 24) c.fillRect(x - cam + 8, y, 14, 10);
        }
        // accessoires à moitié enfouis dans le sol ou le plafond
        for (let i = 2; i < 18; i++) {
            const px = i * 310 + sr(i * 1.7) * 90;
            if (!inView(px, 120)) continue;
            if (Math.abs(px - AC) < 620 || Math.abs(px - E1) < 140 || Math.abs(px - E2) < 140) continue;
            const kind = GPROPS[Math.floor(sr(i * 4.9) * GPROPS.length)];
            const inCeil = sr(i * 8.3) < 0.35 && ceil > -30;
            const sink = 0.3 + sr(i * 2.9) * 0.4;
            c.save();
            c.beginPath();
            if (inCeil) c.rect(0, ceil, w, h); else c.rect(0, 0, w, GY);
            c.clip();
            if (inCeil) {
                c.translate(px - cam, ceil);
                c.rotate(Math.PI + (sr(i) - 0.5) * 0.4);
                drawOfficeProp(c, kind, 0, 60 * sink, 1);
            } else {
                c.translate(px - cam, GY);
                c.rotate((sr(i * 6) - 0.5) * 0.6);
                drawOfficeProp(c, kind, 0, 70 * sink, 1);
            }
            c.restore();
        }
        // portail de retour (tout à gauche)
        if (inView(RET_X, 100)) A.drawPortal(c, RET_X - cam, GY - 90, 62, V.t, false, st.escape ? 1 : 0.8);
        // portail d'arrière-plan qui aspire la trousse adverse
        if (st.bgPortal && st.bgPortal.R > 2) A.drawPortal(c, st.bgPortal.x - cam, GY - st.bgPortal.alt, st.bgPortal.R, V.t * 2, false, 1);
        // arène : un building de chaque côté + 4 plateformes en losange
        if (st.buildRise > 0 && st.stage < 5) {
            const AW = st.AW;
            [-1, 1].forEach((sd) => {
                const bx = AC + sd * AW - cam;
                const hh = (GY + 20) * st.buildRise;
                c.fillStyle = "#3a3358"; c.fillRect(bx - 40, GY - hh, 80, hh);
                c.fillStyle = "rgba(255,80,110,0.5)";
                for (let y = GY - 20; y > GY - hh + 10; y -= 28) { c.fillRect(bx - 28, y - 12, 16, 12); c.fillRect(bx + 12, y - 12, 16, 12); }
            });
            LV.glitch.plats().forEach((pl) => {
                const x = pl.x - cam, y = GY - pl.alt;
                c.fillStyle = "#5a5f6b"; c.fillRect(x - pl.w / 2, y, pl.w, 16);
                c.fillStyle = "#8d93a0"; c.fillRect(x - pl.w / 2, y, pl.w, 4);
                c.fillStyle = "rgba(120,255,230,0.35)";
                for (let k = x - pl.w / 2 + 10; k < x + pl.w / 2 - 12; k += 24) c.fillRect(k, y + 7, 12, 5);
            });
        }
        // sol : la façade d'un building couché
        c.fillStyle = "#3e3658"; c.fillRect(0, GY, w, h - GY);
        c.fillStyle = "#6c608f"; c.fillRect(0, GY - 3, w, 6);
        for (let x = Math.floor(cam / 40) * 40; x < cam + w; x += 40) {
            for (let r = 0; r * 26 + 18 < h - GY; r++) {
                c.fillStyle = sr(x * 0.3 + r * 7) < 0.08 ? "rgba(90,255,220,0.5)" : "#231e36";
                c.fillRect(x - cam + 10, GY + 18 + r * 26, 18, 12);
            }
        }
        // épée abandonnée
        if (st.sword) {
            c.save(); c.translate(st.sword.x - cam, GY - 8); c.rotate(Math.PI / 2 + 0.2); C.drawSword(c, 0, 0, 1, 0, 0.9); c.restore();
        }
    }
    function drawFightMid(c) {
        const st = V.story, f = st.fight;
        if (!f) return;
        const cam = V.camX, GY = V.GY;
        f.circles.forEach((ci) => {
            const x = ci.x - cam, y = GY - ci.alt;
            const p = c01(ci.t / CIRCLE_T);
            c.save();
            if (!ci.boom) {
                c.fillStyle = "rgba(255,30,40," + (0.14 + 0.26 * p) + ")";
                c.beginPath(); c.arc(x, y, ci.r, 0, Math.PI * 2); c.fill();
                c.strokeStyle = "rgba(255,60,60,0.95)"; c.lineWidth = 4;
                c.beginPath(); c.arc(x, y, ci.r, 0, Math.PI * 2); c.stroke();
                c.strokeStyle = "rgba(255,255,255,0.8)"; c.lineWidth = 2;
                c.beginPath(); c.arc(x, y, ci.r * (1 - p), 0, Math.PI * 2); c.stroke();
            } else {
                const q = ci.boom / 0.3;
                c.fillStyle = "rgba(255,220,200," + (1 - q) * 0.8 + ")";
                c.beginPath(); c.arc(x, y, ci.r * (1 + q * 0.2), 0, Math.PI * 2); c.fill();
            }
            c.restore();
        });
    }
    function drawGlitchFront(c) {
        const st = V.story;
        if (st.escape) {
            const x = st.escape.voidX - V.camX;
            if (x < V.VW + 40) {
                c.fillStyle = "#000";
                c.fillRect(x, 0, V.VW - x + 40, V.VH);
                for (let y = 0; y < V.VH; y += 14) {
                    const j = Math.random() * 40;
                    c.fillStyle = ["#ff2d6f", "#2dffd8", "#000", "#6c4dff"][Math.floor(Math.random() * 4)];
                    c.fillRect(x - j, y, j + 2, 12);
                }
            }
        }
    }
    function drawFightHud(c) {
        const st = V.story, f = st.fight;
        if (!f) return;
        const w = V.VW;
        const bw = Math.min(w * 0.6, 520), bx = (w - bw) / 2, by = 76;
        c.save();
        c.fillStyle = "rgba(0,0,0,0.6)"; rr(c, bx - 4, by - 4, bw + 8, 24, 8); c.fill();
        c.fillStyle = "#3a0d12"; rr(c, bx, by, bw, 16, 6); c.fill();
        const g = c.createLinearGradient(bx, 0, bx + bw, 0);
        g.addColorStop(0, "#ff2d4a"); g.addColorStop(1, "#ff8a3c");
        c.fillStyle = g; rr(c, bx, by, Math.max(0.01, bw * f.hp / BOSS_HP), 16, 6); c.fill();
        c.fillStyle = "#fff"; c.font = "900 13px 'Segoe UI', Arial"; c.textAlign = "center";
        c.fillText("???  " + L("— l'autre toi", "— the other you"), w / 2, by - 8);
        // 5 cœurs en bas à droite
        c.font = "26px sans-serif"; c.textAlign = "right"; c.textBaseline = "middle";
        const hy = V.VH - (document.getElementById("ville-touch").classList.contains("show") ? 120 : 30);
        for (let i = 0; i < 5; i++) c.fillText(i < P.hearts ? "❤️" : "🖤", w - 16 - i * 32, hy);
        c.restore();
    }
})();
