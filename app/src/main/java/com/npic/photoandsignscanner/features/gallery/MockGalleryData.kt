package com.npic.photoandsignscanner.features.gallery

import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.StudentRecord
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Mock records for the pre-Room shell. Populates ~14 records across all four classes with a
 * mix of missing-signature and complete records so the Gallery visually exercises every UI
 * state during design QA.
 *
 * DELETE this file when the Room-backed repository lands (Layer 5). Grep-friendly TODO
 * marker below so the removal PR is easy to identify.
 */
// TODO(gallery-shell): remove MockGalleryData once StudentRepository is wired.
object MockGalleryData {
    fun records(): List<StudentRecord> {
        val now = Clock.System.now()
        return buildList {
            // Class 9 — 5 records, one missing signature
            add(mock(1,  ClassNum.Nine,   1, "Ananya Sharma",     hoursAgo = 1))
            add(mock(2,  ClassNum.Nine,   2, "Ravi Kumar",        hoursAgo = 2))
            add(mock(3,  ClassNum.Nine,   3, "Priya Verma",       hoursAgo = 3, hasSig = false))
            add(mock(4,  ClassNum.Nine,   4, "Arjun Singh",       hoursAgo = 5))
            add(mock(5,  ClassNum.Nine,   5, "Sneha Patel",       hoursAgo = 6))
            // Class 10 — 3 records
            add(mock(6,  ClassNum.Ten,    1, "Rohan Gupta",       hoursAgo = 8))
            add(mock(7,  ClassNum.Ten,    2, "Kavya Iyer",        hoursAgo = 10))
            add(mock(8,  ClassNum.Ten,    3, "Aditya Mishra",     hoursAgo = 24, hasSig = false))
            // Class 11 — 4 records, one missing signature
            add(mock(9,  ClassNum.Eleven, 1, "Ishika Rao",        hoursAgo = 26))
            add(mock(10, ClassNum.Eleven, 2, "Vikram Chauhan",    hoursAgo = 28))
            add(mock(11, ClassNum.Eleven, 3, "Nisha Yadav",       hoursAgo = 30, hasSig = false))
            add(mock(12, ClassNum.Eleven, 4, "Karan Malhotra",    hoursAgo = 48))
            // Class 12 — 2 records
            add(mock(13, ClassNum.Twelve, 1, "Meera Joshi",       hoursAgo = 60))
            add(mock(14, ClassNum.Twelve, 2, "Siddharth Reddy",   hoursAgo = 72))
        }
    }

    private fun mock(
        id: Long,
        classNum: ClassNum,
        serial: Int,
        name: String,
        hoursAgo: Int,
        hasSig: Boolean = true,
    ): StudentRecord {
        val now = Clock.System.now()
        val at  = now - hoursAgo.hours
        // Deterministic UUID-shaped string keyed by the mock sequence number so shell
        // debug sessions render the same records across process restarts (aids visual
        // QA + hand-verifying Gallery ordering). Padding to 12 chars matches the UUID
        // node segment so shape is preserved even with two-digit ids.
        val uuidLike = "00000000-0000-0000-0000-%012d".format(id)
        return StudentRecord(
            id            = uuidLike,
            classNum      = classNum,
            serial        = serial,
            displayName   = name,
            photoPath     = "mock://photo/$id",
            signaturePath = if (hasSig) "mock://sig/$id" else null,
            createdAt     = at,
            updatedAt     = at,
        )
    }
}
