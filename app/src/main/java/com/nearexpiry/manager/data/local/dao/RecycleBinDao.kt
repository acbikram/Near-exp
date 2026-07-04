package com.nearexpiry.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nearexpiry.manager.data.local.entity.RecycleBinEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycleBinDao {

    @Insert
    suspend fun insertAll(entries: List<RecycleBinEntity>)

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    fun getAll(): Flow<List<RecycleBinEntity>>

    @Query("SELECT * FROM recycle_bin WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<RecycleBinEntity>

    @Query("DELETE FROM recycle_bin WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** Removes bin copies after an Undo re-insert (matched by original id). */
    @Query("DELETE FROM recycle_bin WHERE originalId IN (:originalIds)")
    suspend fun deleteByOriginalIds(originalIds: List<Long>)

    /** Auto-purge: drop entries deleted before [threshold] (epoch millis). */
    @Query("DELETE FROM recycle_bin WHERE deletedAt < :threshold")
    suspend fun purgeOlderThan(threshold: Long)

    @Query("SELECT COUNT(*) FROM recycle_bin")
    suspend fun count(): Int
}
