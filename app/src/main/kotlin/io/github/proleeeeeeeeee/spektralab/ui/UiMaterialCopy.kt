// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import io.github.proleeeeeeeeee.spektralab.core.FilmStock
import io.github.proleeeeeeeeee.spektralab.core.PaperStock

/** Presentation copy only. Stable material IDs and spectral profiles stay in core/engine. */
internal data class UiMaterialCopy(val features: String, val subjects: String, val source: String)

internal fun filmUiCopy(stock: FilmStock): UiMaterialCopy = when (stock.id) {
    "kodak_gold_200" -> UiMaterialCopy("Daylight color negative, ISO 200.", "Travel, daylight, and everyday photography.", "Spektrafilm reference model based on published technical data.")
    "kodak_portra_400" -> UiMaterialCopy("Daylight color negative, ISO 400.", "Portraits and mixed everyday subjects.", "Spektrafilm reference model based on published technical data.")
    "cinestill_800t" -> UiMaterialCopy("Derived from Vision3 500T and modeled without the anti-halation backing.", "Tungsten light, night scenes, and artificial lighting.", "Derived model using Kodak Vision3 500T base data; no independent measured CineStill 800T curves are available.")
    "kodak_tri_x_400" -> UiMaterialCopy("ISO 400 B&W silver-gelatin negative model; D-76, contrast index 0.56.", "Documentary work and B&W tonal studies.", "Based on Kodak F-4017 characteristic curves with documented model assumptions.")
    "kodak_ektar_100" -> UiMaterialCopy("Daylight color negative, ISO 100.", "Daylight landscapes and detailed color scenes.", "Spektrafilm reference model based on published technical data.")
    "kodak_portra_160" -> UiMaterialCopy("Daylight color negative, ISO 160.", "Portraits and controlled daylight.", "Spektrafilm reference model based on published technical data.")
    "kodak_portra_800" -> UiMaterialCopy("Daylight color negative, ISO 800.", "Low light and scenes that need a faster shutter speed.", "Spektrafilm reference model based on published technical data.")
    "fujifilm_c200" -> UiMaterialCopy("Daylight color negative, ISO 200.", "Travel and daylight color comparisons.", "Spektrafilm reference model based on published technical data.")
    "ilford_hp5_plus_400" -> UiMaterialCopy("ISO 400 B&W negative fit; ILFOTEC HC 1+31.", "Documentary work, action, and available light.", "Based on 2018 HARMAN HP5 PLUS technical curves, including documented fitting assumptions.")
    "kodak_tmax_100" -> UiMaterialCopy("ISO 100 B&W negative fit; D-76, contrast index 0.56.", "Detailed landscapes and controlled light.", "Based on Kodak F-4016 curves with documented model assumptions.")
    "fujifilm_neopan_acros_100_ii" -> UiMaterialCopy("ISO 100 B&W negative fit; D-76, contrast index 0.57.", "Detailed B&W subjects and tonal comparisons.", "Based on FUJIFILM AF3-0258E curves, including documented fitting assumptions.")
    else -> UiMaterialCopy(stock.summary, stock.subjects, stock.provenance)
}

internal fun paperUiCopy(stock: PaperStock): UiMaterialCopy = when (stock.id) {
    "kodak_portra_endura" -> UiMaterialCopy("Color photographic paper reference model.", "Portrait and skin-tone print comparisons.", "Pinned spektrafilm reference data.")
    "kodak_supra_endura" -> UiMaterialCopy("Color photographic paper reference model.", "Color and contrast comparisons using the same photo.", "Pinned spektrafilm reference data.")
    "kodak_2383" -> UiMaterialCopy("Cinema print film using the K75P viewing model.", "Cinema-print look comparison; not photographic paper.", "Pinned spektrafilm Kodak Vision 2383 reference data.")
    "ilford_multigrade_iv_rc" -> UiMaterialCopy("Variable-contrast B&W silver-gelatin paper supporting grades 00–5.", "Compatible with Tri-X 400, T-Max 100, Neopan Acros 100 II, and HP5 Plus 400.", "Based on HARMAN reference curves with documented assumptions. TH-KG3 filter packs and print exposure were fitted on Tri-X and checked against the other three B&W films. This is not guaranteed to exactly reproduce a physical darkroom. Paper grade is separate from scan contrast.")
    "fujifilm_crystal_archive_typeii" -> UiMaterialCopy("Color photographic paper reference model.", "Color print comparison using the same input.", "Pinned spektrafilm reference data.")
    "kodak_endura_premier" -> UiMaterialCopy("Color photographic paper reference model.", "Color print comparison using the same input.", "Pinned spektrafilm reference data.")
    "kodak_ultra_endura" -> UiMaterialCopy("Color photographic paper reference model.", "Color print comparison using the same input.", "Pinned spektrafilm reference data.")
    "kodak_ektacolor_edge" -> UiMaterialCopy("Color photographic paper reference model.", "Color print comparison using the same input.", "Pinned spektrafilm reference data.")
    "kodak_2393" -> UiMaterialCopy("Cinema print-film reference model.", "Compares the cinema-print look with Vision 2383; not photographic paper.", "Pinned spektrafilm Kodak Vision 2393 reference data.")
    else -> UiMaterialCopy(stock.summary, "Compare print effects using the same photo.", "Pinned reference model.")
}

internal fun paperKind(stock: PaperStock): String = when {
    stock.cinemaPrint -> "Cinema print film"
    stock.monochrome -> "B&W paper"
    else -> "Color paper"
}
