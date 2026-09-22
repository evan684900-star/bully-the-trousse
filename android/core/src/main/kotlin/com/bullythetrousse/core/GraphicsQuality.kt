package com.bullythetrousse.core

/**
 * Niveau de détail graphique choisi par le joueur (Réglages → Qualité).
 *
 * Ajout propre au portage Android : le site tourne sur un ordinateur, alors
 * que l'app doit aussi tenir sur un téléphone d'entrée de gamme. Plutôt que
 * de deviner, on laisse le choix — et on garde le réglage dans la
 * sauvegarde, donc il survit au redémarrage.
 *
 * L'identifiant [id] est ce qui part dans le JSON : il ne doit jamais
 * changer, contrairement au libellé affiché.
 */
enum class GraphicsQuality(val id: String, val label: String) {
    LOW("basse", "Basse"),
    MEDIUM("normale", "Normale"),
    HIGH("haute", "Élevée"),
    ;

    /** Les réglages concrets que ce niveau implique (voir [QualityProfile]). */
    val profile: QualityProfile
        get() = when (this) {
            LOW -> QualityProfile(
                particleScale = 0.3,
                maxParticles = 70,
                ambientEffects = false,
                vignette = false,
                cloudLayers = 1,
                trailPoints = 45,
                trailPasses = 1,
                sharpSprites = false,
            )
            MEDIUM -> QualityProfile(
                particleScale = 0.7,
                maxParticles = 170,
                ambientEffects = true,
                vignette = true,
                cloudLayers = 1,
                trailPoints = 100,
                trailPasses = 2,
                sharpSprites = true,
            )
            HIGH -> QualityProfile(
                particleScale = 1.0,
                maxParticles = 300,
                ambientEffects = true,
                vignette = true,
                cloudLayers = 2,
                trailPoints = 140,
                trailPasses = 2,
                sharpSprites = true,
            )
        }

    companion object {
        /** Niveau par défaut d'une sauvegarde neuve, et repli d'un
         *  identifiant inconnu (sauvegarde d'une version plus récente). */
        val DEFAULT = MEDIUM

        fun fromId(id: String): GraphicsQuality = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Le niveau suivant, en boucle : le réglage est un simple bouton
         *  qu'on tape pour faire défiler les trois choix. */
        fun next(current: GraphicsQuality): GraphicsQuality = entries[(current.ordinal + 1) % entries.size]
    }
}

/**
 * Ce qu'un niveau de qualité change concrètement au rendu.
 *
 * - [particleScale] multiplie le nombre de particules d'une gerbe ;
 * - [maxParticles] plafonne le nuage de particules d'une cinématique ;
 * - [ambientEffects] couvre ce qui est purement décoratif et coûteux :
 *   étoiles, mouettes, reflets sur l'eau, lignes de vitesse, fumée, halo
 *   du soleil, ombres portées ;
 * - [cloudLayers] : une seule couche de nuages, ou deux en parallaxe ;
 * - [trailPoints]/[trailPasses] : la finesse du sillage derrière la trousse ;
 * - [sharpSprites] : filtrage bilinéaire du sprite (plus joli, plus coûteux).
 */
data class QualityProfile(
    val particleScale: Double,
    val maxParticles: Int,
    val ambientEffects: Boolean,
    val vignette: Boolean,
    val cloudLayers: Int,
    val trailPoints: Int,
    val trailPasses: Int,
    val sharpSprites: Boolean,
)
