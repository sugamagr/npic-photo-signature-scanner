package com.npic.photoandsignscanner.domain.model

/**
 * Gallery sort order (PRD §5.6). Newest is default so the last-captured record surfaces
 * top-left for immediate visual confirmation.
 */
enum class SortMode(val label: String) {
    Newest("Newest"),
    Oldest("Oldest"),
    ClassAscending("Class"),
    NameAscending("Name"),
}
