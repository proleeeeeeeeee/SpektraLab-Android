// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.core

/** Stable IDs bind actual pinned profiles. Descriptions are creative guidance, not calibration claims. */
data class FilmStock(
    val id: String,val brand: String,val name: String,val monochrome: Boolean = false,
    val summary: String = "",val subjects: String = "",val provenance: String = "",
    val edgeText: String = "",val derivedFrom: String? = null,
)
data class PaperStock(
    val id: String,val brand: String,val name: String,val monochrome: Boolean = false,
    val cinemaPrint: Boolean = false,val summary: String = "",
)
object Materials {
    val films = listOf(
        FilmStock("kodak_gold_200","Kodak","Gold 200",false,"Daylight color negative, ISO 200.","Travel, daylight and everyday scenes.","spektrafilm datasheet-based profile","KODAK GB 200-7",null),
        FilmStock("kodak_portra_400","Kodak","Portra 400",false,"Daylight color negative, ISO 400.","Portraits and mixed everyday subjects.","spektrafilm datasheet-based profile","KODAK PORTRA 400",null),
        FilmStock("cinestill_800t","CineStill","800T",false,"Vision3 500T-derived model with no anti-halation backing.","Tungsten/night-light creative studies.","Derived alias; no independent 800T measurement curves","CINESTILL 800T","kodak_vision3_500t"),
        FilmStock("kodak_tri_x_400","Kodak","Tri-X 400",true,"ISO 400 silver-negative model; D-76, CI 0.56.","Documentary and black-and-white tonal studies.","Kodak F-4017 curves plus documented assumptions","KODAK 400TX",null),
        FilmStock("kodak_ektar_100","Kodak","Ektar 100",false,"Daylight color negative, ISO 100.","Daylight landscapes and detailed color subjects.","spektrafilm datasheet-based profile","KODAK EKTAR 100",null),
        FilmStock("kodak_portra_160","Kodak","Portra 160",false,"Daylight color negative, ISO 160.","Portraits and controlled daylight.","spektrafilm datasheet-based profile","KODAK PORTRA 160",null),
        FilmStock("kodak_portra_800","Kodak","Portra 800",false,"Daylight color negative, ISO 800.","Available-light and faster-shutter creative studies.","spektrafilm datasheet-based profile","KODAK PORTRA 800",null),
        FilmStock("fujifilm_c200","Fujifilm","C200",false,"Daylight color negative, ISO 200.","Travel and daylight color comparisons.","spektrafilm datasheet-based profile","FUJICOLOR C200",null),
        FilmStock("ilford_hp5_plus_400","Ilford","HP5 Plus 400",true,"ISO 400 silver-negative fit; ILFOTEC HC 1+31.","Documentary, action and available-light studies.","HARMAN HP5 PLUS Nov 2018 curves; fitted and assumed terms","ILFORD HP5 PLUS 400",null),
        FilmStock("kodak_tmax_100","Kodak","T-Max 100",true,"ISO 100 silver-negative fit; D-76, CI 0.56.","Detailed monochrome landscapes and controlled light.","Kodak F-4016 curves plus documented assumptions","KODAK TMAX 100",null),
        FilmStock("fujifilm_neopan_acros_100_ii","Fujifilm","Neopan Acros 100 II",true,"ISO 100 silver-negative fit; D-76, CI 0.57.","Fine-detail monochrome and tonal comparisons.","FUJIFILM AF3-0258E curves plus fitted/assumed terms","NEOPAN ACROS 100 II",null),
    )
    val papers = listOf(
        PaperStock("kodak_portra_endura","Kodak","Portra Endura",false,false,"Color photographic paper; compare portrait-oriented print rendering."),
        PaperStock("kodak_supra_endura","Kodak","Supra Endura",false,false,"Color photographic paper; compare color/contrast on the same input."),
        PaperStock("kodak_2383","Kodak","Vision 2383",false,true,"Cinema print film, not photographic paper; K75P viewing model."),
        PaperStock("ilford_multigrade_iv_rc","Ilford","Multigrade IV RC",true,false,"Variable-contrast silver paper; grades 00–5 use fitted TH-KG3 filter packs."),
        PaperStock("fujifilm_crystal_archive_typeii","Fujifilm","Crystal Archive Type II",false,false,"Color photographic-paper profile for same-input print comparisons."),
        PaperStock("kodak_endura_premier","Kodak","Endura Premier",false,false,"Color photographic-paper profile for same-input print comparisons."),
        PaperStock("kodak_ultra_endura","Kodak","Ultra Endura",false,false,"Color photographic-paper profile for same-input print comparisons."),
        PaperStock("kodak_ektacolor_edge","Kodak","Ektacolor Edge",false,false,"Color photographic-paper profile for same-input print comparisons."),
        PaperStock("kodak_2393","Kodak","Vision 2393",false,true,"Cinema print film, not photographic paper; compare with Vision 2383."),
    )
    fun film(id: String) = films.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown film stock")
    fun paper(id: String) = papers.firstOrNull { it.id == id }
        ?: throw IllegalArgumentException("Unknown paper stock")
    fun compatiblePapers(filmId: String) = papers.filter { it.monochrome == film(filmId).monochrome }
}
