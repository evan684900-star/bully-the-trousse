// Régénère android/core/src/main/kotlin/com/bullythetrousse/core/I18nStrings.kt
// depuis la table STRINGS d'index.html. Usage (depuis la racine du dépôt) :
//   node android/tools/gen-i18n-strings.js .
const fs = require("fs");
const root = process.argv[2];
const html = fs.readFileSync(root + "/index.html", "utf8");
const start = html.indexOf("const STRINGS = {");
const end = html.indexOf("\n    };", start);
const obj = html.slice(start + "const STRINGS = ".length, end + "\n    }".length);
const STRINGS = eval("(" + obj + ")");
const keys = Object.keys(STRINGS);
const esc = (s) => String(s).replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\$/g, "\\$").replace(/\n/g, "\\n");
const parts = 5;
const per = Math.ceil(keys.length / parts);
let out = `package com.bullythetrousse.core

// FICHIER GÉNÉRÉ depuis la table STRINGS de index.html : ne pas éditer à la
// main, relancer le générateur pour le mettre à jour.
//
// Découpé en plusieurs fonctions : une seule méthode contenant les ${keys.length}
// entrées frôlerait la limite de taille de méthode de la JVM (64 Ko).

internal object I18nStrings {
    val TABLE: Map<String, Pair<String, String>> by lazy {
        HashMap<String, Pair<String, String>>(${keys.length * 2}).apply {
${Array.from({ length: parts }, (_, i) => `            part${i}(this)`).join("\n")}
        }
    }
`;
for (let p = 0; p < parts; p++) {
  out += `\n    private fun part${p}(m: MutableMap<String, Pair<String, String>>) {\n`;
  for (const k of keys.slice(p * per, (p + 1) * per)) {
    const e = STRINGS[k];
    if (typeof e.fr !== "string" || typeof e.en !== "string") throw new Error("entrée non texte : " + k);
    out += `        m["${k}"] = "${esc(e.fr)}" to "${esc(e.en)}"\n`;
  }
  out += `    }\n`;
}
out += `}\n`;
fs.writeFileSync(root + "/android/core/src/main/kotlin/com/bullythetrousse/core/I18nStrings.kt", out);
console.log(keys.length + " clés");
