package com.npic.photoandsignscanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for `drafts`. v1.0 keeps one active draft at a time — [observeActive] returns the
 * newest row so the Gallery's resume-prompt (PRD §8.3) surfaces the last in-progress
 * capture on cold start.
 */
@Dao
interface DraftDao {

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<DraftEntity?>

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DraftEntity)

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM drafts")
    suspend fun clear()
}
