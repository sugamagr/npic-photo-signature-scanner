package com.npic.photoandsignscanner.domain.model

/**
 * Gallery sort order. Six modes per PRD §4.8. Newest is the default so the last-captured
 * record surfaces top-left for immediate visual confirmation.
 */
enum class SortMode(val label: String) {
    Newest         ("Date newest"),
    Oldest         ("Date oldest"),
    NameAscending  ("Name A \u2192 Z"),
    NameDescending ("Name Z \u2192 A"),
    ClassAscending ("Class 9 \u2192 12"),
    ClassDescending("Class 12 \u2192 9"),
}
