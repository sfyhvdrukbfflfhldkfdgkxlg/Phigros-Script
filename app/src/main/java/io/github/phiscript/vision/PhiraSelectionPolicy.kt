package io.github.phiscript.vision
/** Manual selection replaces OCR only after independent gameplay or menu evidence. */
internal class PhiraSelectionPolicy(manualChartId: String, candidates: List<PhiraCandidate>) {
    val manual: PhiraCandidate? = if (manualChartId.isBlank()) null else candidates.singleOrNull { it.id == manualChartId }
        ?: error("手选 Phira 谱面已不存在，请重新选择")
    fun choose(automatic: PhiraCandidate?, gameEvidence: Boolean): PhiraCandidate? {
        val selected = manual ?: return automatic
        if (!gameEvidence || conflicts(automatic)) return null
        return selected
    }
    fun conflicts(automatic: PhiraCandidate?): Boolean {
        val selected = manual ?: return false
        return automatic != null && (automatic.id != selected.id || automatic.level != selected.level)
    }
}
