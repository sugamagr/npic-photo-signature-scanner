package com.npic.photoandsignscanner.data.repo

import android.database.sqlite.SQLiteConstraintException
import com.npic.photoandsignscanner.data.db.StudentDao
import com.npic.photoandsignscanner.data.db.toEntity
import com.npic.photoandsignscanner.data.db.toRecord
import com.npic.photoandsignscanner.domain.model.ClassNum
import com.npic.photoandsignscanner.domain.model.NamingMode
import com.npic.photoandsignscanner.domain.model.SaveInput
import com.npic.photoandsignscanner.domain.model.SaveResult
import com.npic.photoandsignscanner.domain.model.StudentDraft
import com.npic.photoandsignscanner.domain.model.StudentRecord
import com.npic.photoandsignscanner.domain.repo.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/**
 * Room-backed [StudentRepository] (PRD §8.1). All heavy lifting is in the DAO's
 * @Transaction methods — this class exists to bridge domain shapes ↔ Room entities and
 * to sequence the "duplicate check → insert / return DuplicateFound" flow required by
 * PRD §4.7.
 *
 * The DAO's UNIQUE(classNum, serial) index gives us a DB-level backstop against the
 * check-then-write race: if two saves collide, one commits and the other trips
 * [SQLiteConstraintException] which we translate into [SaveResult.DuplicateFound].
 */
class RoomStudentRepository(
    private val dao: StudentDao,
) : StudentRepository {

    override fun observeAll(): Flow<List<StudentRecord>> =
        dao.observeAll().map { list -> list.map { it.toRecord() } }

    override suspend fun getById(id: String): StudentRecord? =
        dao.getById(id)?.toRecord()

    override suspend fun nextSerial(classNum: ClassNum): Int =
        dao.peekNextSerial(classNum.label)

    override suspend fun findByClassSerial(classNum: ClassNum, serial: Int): StudentRecord? =
        dao.findByClassSerial(classNum.label, serial)?.toRecord()

    override suspend fun findByClassName(classNum: ClassNum, name: String): StudentRecord? =
        dao.findByClassName(classNum.label, name)?.toRecord()

    override suspend fun save(draft: StudentDraft, input: SaveInput): SaveResult {
        if (!draft.hasAnyMedia) return SaveResult.MissingBothMedia

        val duplicate = when (input.naming) {
            is NamingMode.Serial -> findByClassSerial(input.classNum, input.naming.number)
            is NamingMode.Name   -> findByClassName(input.classNum, input.naming.text)
        }
        if (duplicate != null) {
            return SaveResult.DuplicateFound(existing = duplicate, incoming = draft, input = input)
        }

        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            // Name mode still needs a serial slot for stable ordering + future export routes.
            is NamingMode.Name   -> dao.nextSerialAndBump(input.classNum.label)
        }

        val now = Clock.System.now()
        val record = StudentRecord(
            id            = draft.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = draft.photoPath.orEmpty(),
            signaturePath = draft.signaturePath,
            createdAt     = draft.createdAt,
            updatedAt     = now,
        )

        return try {
            dao.insert(record.toEntity(namingKind = input.naming.kind.name))
            // If the user is in Serial mode AND the chosen number is higher than the
            // current counter, advance the counter so the next auto-populated serial
            // stays ahead of the user's manual pick. PRD §4.7: serials never rewind.
            if (input.naming is NamingMode.Serial) {
                val current = dao.getLastSerial(input.classNum.label) ?: 0
                if (input.naming.number > current) {
                    dao.setCounter(
                        com.npic.photoandsignscanner.data.db.ClassCounterEntity(
                            classNum = input.classNum.label,
                            lastSerial = input.naming.number,
                        ),
                    )
                }
            }
            SaveResult.Success(record)
        } catch (constraint: SQLiteConstraintException) {
            // Race backstop: another Save inserted the same (class, serial) between our
            // duplicate check and this insert. Re-read the existing row and route to the
            // duplicate-resolve UI.
            val existing = when (input.naming) {
                is NamingMode.Serial -> findByClassSerial(input.classNum, input.naming.number)
                is NamingMode.Name   -> findByClassName(input.classNum, input.naming.text)
            }
            if (existing != null) {
                SaveResult.DuplicateFound(existing = existing, incoming = draft, input = input)
            } else {
                throw constraint
            }
        }
    }

    override suspend fun replace(existingId: String, draft: StudentDraft, input: SaveInput): SaveResult {
        if (!draft.hasAnyMedia) return SaveResult.MissingBothMedia

        val serial = when (val n = input.naming) {
            is NamingMode.Serial -> n.number
            is NamingMode.Name   -> dao.nextSerialAndBump(input.classNum.label)
        }
        val now = Clock.System.now()
        val record = StudentRecord(
            id            = draft.id,
            classNum      = input.classNum,
            serial        = serial,
            displayName   = input.displayName,
            photoPath     = draft.photoPath.orEmpty(),
            signaturePath = draft.signaturePath,
            createdAt     = draft.createdAt,
            updatedAt     = now,
        )
        dao.replace(existingId = existingId, incoming = record.toEntity(namingKind = input.naming.kind.name))
        return SaveResult.Success(record)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }
}
