package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplyLogDao {
    @Query("SELECT * FROM reply_logs ORDER BY timestamp DESC")
    fun getAllLogsFlow(): Flow<List<ReplyLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ReplyLog): Long

    @Query("UPDATE reply_logs SET status = :status, replyDraft = :replyDraft WHERE id = :id")
    suspend fun updateStatus(id: Int, status: String, replyDraft: String)

    @Query("UPDATE reply_logs SET status = :status, replyDraft = :replyDraft, priority = :priority, callTranscription = :callTranscription, callSummary = :callSummary WHERE id = :id")
    suspend fun updateLogDetails(id: Int, status: String, replyDraft: String, priority: String, callTranscription: String?, callSummary: String?)

    @Query("DELETE FROM reply_logs")
    suspend fun clearLogs()
}
