package com.nearexpiry.manager.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.nearexpiry.manager.data.local.entity.RecheckCodeEntity

@Dao
interface RecheckCodeDao {
    @Query("SELECT COUNT(*) FROM recheck_codes")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM recheck_codes WHERE code = :code)")
    suspend fun contains(code: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(codes: List<RecheckCodeEntity>)

    @Query("DELETE FROM recheck_codes")
    suspend fun clearAll()

    /** Replaces the entire global list in one Room transaction. */
    @Transaction
    suspend fun replaceAll(codes: List<RecheckCodeEntity>) {
        clearAll()
        insertAll(codes)
    }
}
