package com.npic.photoandsignscanner.domain.model

import androidx.compose.runtime.Immutable

/**
 * How the user names a saved record. Per PRD §4.6 the Save dialog exposes a
 * `Serial | Name` segmented control, and per PRD §6.3 the two modes produce different
 * filename patterns:
 *
 *  * [Serial]: `{portalCode}{4-digit serial}.jpg`   e.g. `090001.jpg`
 *  * [Name]  : `{Name_ClassSuffix}.jpg`             e.g. `Rahul_Kumar_09.jpg`
 *
 * A sealed hierarchy (rather than an enum) so each variant can carry its own value
 * exactly once and the `filename` derivation stays typesafe. PRD §4.6 validation:
 *
 *  * Serial: 1..9999
 *  * Name  : 2..50 chars, letters/spaces/hyphens/periods only
 */
@Immutable
sealed interface NamingMode {

    /** Auto-populated with next-available serial for the selected class; editable. */
    @Immutable
    data class Serial(val number: Int) : NamingMode {
        init {
            require(number in MIN..MAX) { "Serial $number outside [$MIN, $MAX]" }
        }

        companion object {
            const val MIN: Int = 1
            const val MAX: Int = 9999
        }
    }

    /** Free-text name; validation runs in the Save layer since it depends on locale rules. */
    @Immutable
    data class Name(val text: String) : NamingMode {
        companion object {
            const val MIN_LENGTH: Int = 2
            const val MAX_LENGTH: Int = 50

            /**
             * PRD §4.6: letters/spaces/hyphens/periods only. Kept ASCII for the shell —
             * Devanagari support ships with the localization batch.
             */
            private val allowed = Regex("^[A-Za-z .-]+$")

            fun isValid(text: String): Boolean {
                val trimmed = text.trim()
                return trimmed.length in MIN_LENGTH..MAX_LENGTH && allowed.matches(trimmed)
            }
        }
    }

    /** Discriminator for the segmented control without materialising a value. */
    enum class Kind { Serial, Name }

    val kind: Kind
        get() = when (this) {
            is Serial -> Kind.Serial
            is Name   -> Kind.Name
        }
}

/**
 * Derives the on-disk filename for a naming mode + class per PRD §6.3.
 *
 * For [NamingMode.Name] the spec is `{Name_ClassSuffix}.jpg` — spaces collapse to
 * underscores, class portal code appended (e.g. `_09`), then `.jpg`.
 */
fun NamingMode.toFilename(classNum: ClassNum): String = when (this) {
    is NamingMode.Serial -> "${classNum.portalCode}${number.toString().padStart(4, '0')}.jpg"
    is NamingMode.Name   -> {
        val cleaned = text.trim().replace(Regex("\\s+"), "_")
        "${cleaned}_${classNum.portalCode}.jpg"
    }
}
