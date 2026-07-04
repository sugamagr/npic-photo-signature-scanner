package com.npic.photoandsignscanner.domain.model

/**
 * The four UP Board classes we track. Only 9 and 11 are registration classes on the UPMSP
 * portal; 10 and 12 are exam years — the app keeps all four because schools locally use the
 * app for internal photo digitization across the board (PRD §3).
 */
enum class ClassNum(val label: String, val portalCode: String) {
    Nine  ("9",  portalCode = "09"),
    Ten   ("10", portalCode = "10"),
    Eleven("11", portalCode = "11"),
    Twelve("12", portalCode = "12");

    companion object {
        fun fromLabel(label: String): ClassNum? = entries.firstOrNull { it.label == label }
    }
}
