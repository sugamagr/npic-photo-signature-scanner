package com.npic.photoandsignscanner.data.repo

import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import com.npic.photoandsignscanner.features.gallery.MockGalleryData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * In-memory [StudentRepository] backed by a [MutableStateFlow]. Seeds from
 * [MockGalleryData] on construction so the Gallery keeps its shell content until the Room
 * layer lands.
 *
 * Thread-safety: reads through [observeAll] are lock-free (StateFlow); writes go through
 * [mutex] so `nextSerial` + duplicate check + append form one atomic transaction.
 *
 * TODO(repo): swap for a Room-backed impl. This class is deliberately kept behind
 * [StudentRepository] so the swap is a wiring change in MainActivity only.
 */
class InMemoryStudentRepository(
    seed: List<StudentRecord> = MockGalleryData.records(),
) : StudentRepository {

    private val _records = MutableStateFlow(seed)
    private val mutex = Mutex()

    override fun observeAll(): Flow<List<StudentRecord>> = _records.asStateFlow()

    override suspend fun getById(id: String): StudentRecord? =
        _records.value.firstOrNull { it.id == id }

    override suspend fun delete(id: String): Unit = mutex.withLock {
        val filtered = _records.value.filterNot { it.id == id }
        // Only emit if we actually removed something so subscribers don't see spurious
        // list-identity changes.
        if (filtered.size != _records.value.size) {
            _records.value = filtered
        }
    }

    override suspend fun nextSerial(classNum: ClassNum): Int = mutex.withLock {
        val existing = _records.value.filter { it.classNum == classNum }
        if (existing.isEmpty()) 1 else existing.maxOf { it.serial } + 1
    }

    override suspend fun findByClassSerial(classNum: ClassNum, serial: Int): StudentRecord? =
        _records.value.firstOrNull { it.classNum == classNum && it.serial == serial }

    override suspend fun findByClassName(classNum: ClassNum, name: String): StudentRecord? {
        val key = nameKey(name)
        return _records.value.firstOrNull { it.classNum == classNum && nameKey(it.displayName) == key }
    }

    override suspend fun save(draft: StudentDraft, input: SaveInput): SaveResult = mutex.withLock {
        if (!draft.hasAnyMedia) return@withLock SaveResult.MissingBothMedia

        val duplicate = when (input.naming) {
            is NamingMode.Serial -> findByClassSerial(input.classNum, input.naming.number)
            is NamingMode.Name   -> findByClassName(input.classNum, input.naming.text)
        }
        if (duplicate != null) {
            return@withLock SaveResult.DuplicateFound(existing = duplicate, incoming = draft, input = input)
        }

        val record = draft.toRecord(input)
        _records.value = _records.value + record
        SaveResult.Success(record)
    }

    override suspend fun replace(existingId: String, draft: StudentDraft, input: SaveInput): SaveResult =
        mutex.withLock {
            if (!draft.hasAnyMedia) return@withLock SaveResult.MissingBothMedia
            val filtered = _records.value.filterNot { it.id == existingId }
            val record = draft.toRecord(input)
            _records.value = filtered + record
            SaveResult.Success(record)
        }

    private fun StudentDraft.toRecord(input: SaveInput): StudentRecord {
        val now = Clock.System.now()
        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            // Free-text names don't own a serial slot; assign the next available number so
            // the record still sorts stably in Class-ordered views. Files exported under
            // the Name naming mode use displayName, not serial.
            is NamingMode.Name   -> nextSerialSync(input.classNum)
        }
        // Reuse the draft's UUID as the record ID so the Layer 9 SourceStore assets
        // (`sources/{draftId}_photo.jpg`, `..._signature.jpg`) keep pointing at the same
        // record after Save. PRD §8.1 UUID contract.
        return StudentRecord(
            id            = this.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = photoPath ?: "",
            signaturePath = signaturePath,
            createdAt     = createdAt,
            updatedAt     = now,
        )
    }

    /** Non-suspending serial computation for use inside an already-locked block. */
    private fun nextSerialSync(classNum: ClassNum): Int {
        val existing = _records.value.filter { it.classNum == classNum }
        return if (existing.isEmpty()) 1 else existing.maxOf { it.serial } + 1
    }

    private companion object {
        /** Normalised comparison key for name-based duplicate detection. */
        fun nameKey(raw: String): String =
            raw.trim().lowercase().replace(Regex("\\s+"), " ")
    }
}
