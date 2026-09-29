// Régénère android/app/src/main/kotlin/com/bullythetrousse/app/Changelog.kt
// depuis le tableau CHANGELOG d'index.html. Usage (depuis la racine du dépôt) :
//   node android/tools/gen-changelog.js .
const fs = require("fs");
const root = process.argv[2];
const html = fs.readFileSync(root + "/index.html", "utf8");
const start = html.indexOf("const CHANGELOG = [");
const end = html.indexOf("\n    ];", start);
const CHANGELOG = eval(html.slice(start + "const CHANGELOG = ".length, end + "\n    ]".length));
const esc = (s) => String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\$/g, "\\$").replace(/\n/g, "\\n");
const lines = (list) => "listOf(" + list.map((l) => `ChangelogLine("${esc(l.fr)}", "${esc(l.en)}")`).join(", ") + ")";
const entry = (e) => `    ChangelogEntry(\n        "${esc(e.version)}",\n        ${lines(e.major || [])},\n        ${lines(e.minor || [])},\n    ),\n`;
const half = Math.ceil(CHANGELOG.length / 2);
let out = `package com.bullythetrousse.app

// FICHIER GÉNÉRÉ depuis CHANGELOG (index.html) : ne pas éditer à la main.

/**
 * Le journal des changements du site, repris intégralement (${CHANGELOG.length}
 * versions, même ordre, entrées majeures et mineures) dans les deux langues.
 * Accessible en touchant le numéro de version sur le menu, comme
 * \`#version-tag\` côté web.
 */
data class ChangelogLine(val fr: String, val en: String)

data class ChangelogEntry(val version: String, val major: List<ChangelogLine>, val minor: List<ChangelogLine>)

/** La version affichée sur le menu : la plus récente du journal. */
val GAME_VERSION: String get() = CHANGELOG.first().version

val CHANGELOG: List<ChangelogEntry> by lazy { changelogPart0() + changelogPart1() }

// Découpé en deux fonctions : une seule dépasserait la taille de méthode JVM.
private fun changelogPart0(): List<ChangelogEntry> = listOf(
${CHANGELOG.slice(0, half).map(entry).join("")})

// Découpé en deux fonctions : une seule dépasserait la taille de méthode JVM.
private fun changelogPart1(): List<ChangelogEntry> = listOf(
${CHANGELOG.slice(half).map(entry).join("")})
`;
fs.writeFileSync(root + "/android/app/src/main/kotlin/com/bullythetrousse/app/Changelog.kt", out);
console.log(CHANGELOG.length + " versions");
