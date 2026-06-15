package com.example.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.ReplyLog
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReplyNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        if (packageName != "com.whatsapp") {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val content = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        if (sender.isEmpty() || content.isEmpty()) {
            return
        }

        // WhatsApp adds system notifications such as backup status or "Checking for new messages..."
        // We should ignore these
        if (content.contains("Checking for new messages") || content.contains("WhatsApp Web")
            || sender.contains("WhatsApp") || content.lowercase().contains("backup") || content.lowercase().contains("media")
        ) {
            return
        }

        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                
                // Process WhatsApp message through our central engine
                val resultLog = com.example.data.repository.AssistantEngine.processInboundEvent(
                    context = applicationContext,
                    platform = "WHATSAPP",
                    sender = sender,
                    content = content
                )

                // If active and approved for action
                if (resultLog.status == "SUCCESS") {
                    val replied = replyToNotification(sbn, resultLog.replyDraft)
                    val finalStatus = if (replied) "SUCCESS" else "FAILED: No Reply Action found"
                    val finalLog = resultLog.copy(status = finalStatus)
                    db.replyLogDao().insert(finalLog)
                } else {
                    db.replyLogDao().insert(resultLog)
                }
            } catch (e: Exception) {
                Log.e("WhatsAppListener", "Error executing reply logic", e)
            }
        }
    }

    private fun replyToNotification(sbn: StatusBarNotification, replyMessage: String): Boolean {
        val notification = sbn.notification ?: return false
        val actions = notification.actions ?: return false

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey != null) {
                    try {
                        val intent = Intent()
                        val bundle = Bundle()
                        bundle.putCharSequence(remoteInput.resultKey, replyMessage)
                        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

                        // Trigger the reply pendingintent!
                        action.actionIntent.send(applicationContext, 0, intent)
                        return true
                    } catch (e: Exception) {
                        Log.e("WhatsAppListener", "Failed sending WhatsApp reply", e)
                    }
                }
            }
        }
        return false
    }
}
