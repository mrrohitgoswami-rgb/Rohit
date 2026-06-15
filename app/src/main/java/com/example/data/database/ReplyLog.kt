package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reply_logs")
data class ReplyLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val platform: String, // "CALL", "SMS", "WHATSAPP"
    val sender: String, // Phone number or name
    val incomingContent: String, // Message body or alert
    val replyDraft: String, // Generated reply text
    val status: String, // "SUCCESS", "FAILED", "PENDING", "DISABLED"
    val priority: String = "बाद में", // "तत्काल" (Urgent), "बाद में" (Later), "उपेक्षा" (Ignore / Spam)
    val callTranscription: String? = null,
    val callSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
