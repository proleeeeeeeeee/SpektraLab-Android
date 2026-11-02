// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 proleeeeeeeeee
package io.github.proleeeeeeeeee.spektralab.ui

import io.github.proleeeeeeeeee.spektralab.core.FilmStock
import io.github.proleeeeeeeeee.spektralab.core.PaperStock

/** Presentation copy only. Stable material IDs and spectral profiles stay in core/engine. */
internal data class UiMaterialCopy(val features: String, val subjects: String, val source: String)

internal fun filmUiCopy(stock: FilmStock): UiMaterialCopy = when (stock.id) {
    "kodak_gold_200" -> UiMaterialCopy("日光型彩色负片，ISO 200。", "旅行、日光与日常记录。", "spektrafilm 基于技术资料建立的参考模型。")
    "kodak_portra_400" -> UiMaterialCopy("日光型彩色负片，ISO 400。", "人像与日常混合题材。", "spektrafilm 基于技术资料建立的参考模型。")
    "cinestill_800t" -> UiMaterialCopy("由 Vision3 500T 派生，模拟移除防光晕背层。", "钨丝灯、夜景与人工光源。", "派生模型，沿用 Kodak Vision3 500T 的基础数据；没有独立的 CineStill 800T 实测曲线。")
    "kodak_tri_x_400" -> UiMaterialCopy("ISO 400 黑白银盐负片模型；D-76，反差指数 0.56。", "纪实与黑白影调研究。", "依据 Kodak F-4017 特性曲线，并包含已记录的模型假设。")
    "kodak_ektar_100" -> UiMaterialCopy("日光型彩色负片，ISO 100。", "日光风景与细节丰富的彩色场景。", "spektrafilm 基于技术资料建立的参考模型。")
    "kodak_portra_160" -> UiMaterialCopy("日光型彩色负片，ISO 160。", "人像与可控日光。", "spektrafilm 基于技术资料建立的参考模型。")
    "kodak_portra_800" -> UiMaterialCopy("日光型彩色负片，ISO 800。", "弱光与需要较快快门的场景。", "spektrafilm 基于技术资料建立的参考模型。")
    "fujifilm_c200" -> UiMaterialCopy("日光型彩色负片，ISO 200。", "旅行与日光彩色对照。", "spektrafilm 基于技术资料建立的参考模型。")
    "ilford_hp5_plus_400" -> UiMaterialCopy("ISO 400 黑白负片拟合；ILFOTEC HC 1+31。", "纪实、运动与现场光。", "依据 HARMAN HP5 PLUS 2018 年技术曲线，包含拟合与假设项。")
    "kodak_tmax_100" -> UiMaterialCopy("ISO 100 黑白负片拟合；D-76，反差指数 0.56。", "细节风景与可控光线。", "依据 Kodak F-4016 曲线，并包含已记录的模型假设。")
    "fujifilm_neopan_acros_100_ii" -> UiMaterialCopy("ISO 100 黑白负片拟合；D-76，反差指数 0.57。", "精细黑白题材与影调对照。", "依据 FUJIFILM AF3-0258E 曲线，包含拟合与假设项。")
    else -> UiMaterialCopy(stock.summary, stock.subjects, stock.provenance)
}

internal fun paperUiCopy(stock: PaperStock): UiMaterialCopy = when (stock.id) {
    "kodak_portra_endura" -> UiMaterialCopy("彩色摄影相纸参考模型。", "人像与肤色印相对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_supra_endura" -> UiMaterialCopy("彩色摄影相纸参考模型。", "同一张照片的色彩与反差对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_2383" -> UiMaterialCopy("电影正片，使用 K75P 观看模型。", "电影印片效果对照；并非摄影相纸。", "固定版本的 spektrafilm Kodak Vision 2383 参考数据。")
    "ilford_multigrade_iv_rc" -> UiMaterialCopy("可变反差黑白银盐相纸，支持 00–5 号。", "与 Tri-X 400、T-Max 100、Neopan Acros 100 II、HP5 Plus 400 搭配。", "依据 HARMAN 参考曲线并包含模型假设。TH-KG3 滤色包与印相曝光在 Tri-X 上拟合，并检查其余三款黑白负片；不保证物理暗房的精确复刻。反差号与扫描对比度独立。")
    "fujifilm_crystal_archive_typeii" -> UiMaterialCopy("彩色摄影相纸参考模型。", "同一输入的彩色印相对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_endura_premier" -> UiMaterialCopy("彩色摄影相纸参考模型。", "同一输入的彩色印相对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_ultra_endura" -> UiMaterialCopy("彩色摄影相纸参考模型。", "同一输入的彩色印相对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_ektacolor_edge" -> UiMaterialCopy("彩色摄影相纸参考模型。", "同一输入的彩色印相对照。", "固定版本的 spektrafilm 参考数据。")
    "kodak_2393" -> UiMaterialCopy("电影正片参考模型。", "与 Vision 2383 比较电影印片效果；并非摄影相纸。", "固定版本的 spektrafilm Kodak Vision 2393 参考数据。")
    else -> UiMaterialCopy(stock.summary, "在同一照片上比较印相效果。", "固定版本的参考模型。")
}

internal fun paperKind(stock: PaperStock): String = when {
    stock.cinemaPrint -> "电影正片"
    stock.monochrome -> "黑白相纸"
    else -> "彩色相纸"
}
