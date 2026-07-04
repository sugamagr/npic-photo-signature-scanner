package com.npic.photoandsignscanner.features.detail

import androidx.compose.runtime.Immutable
import com.npic.photoandsignscanner.domain.model.StudentRecord

/**
 * Detail screen UI state (PRD §4.9). [record] is null while the repository is being
 * queried or when the requested ID no longer exists (post-Delete edge). The screen
 * shows a loading spinner during initial load and pops back to Gallery on missing.
 */
@Immutable
data class DetailUiState(
    val record: StudentRecord? = null,
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
)
