package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.ReplyLog
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallReplyReceiver : BroadcastReceiver() {
    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var incomingNumber: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
            
            // Extract the incoming number if available
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            if (number != null) {
                incomingNumber = number
            }

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                Log.d("CallReplyReceiver", "Call Ringing from: $incomingNumber")
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                // If the state was ringing and now transitioned to idle
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING && !incomingNumber.isNullOrEmpty()) {
                    val callerNum = incomingNumber!!
                    incomingNumber = null // Reset
                    handleCallEvent(context.applicationContext, callerNum)
                }
            }
            lastState = state
        }
    }

    private fun handleCallEvent(context: Context, phoneNumber: String) {
        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                
                // Process call through the central AssistantEngine
                val resultLog = com.example.data.repository.AssistantEngine.processInboundEvent(
                    context = context,
                    platform = "CALL",
                    sender = phoneNumber,
                    content = "Missed / Unanswered Call"
                )

                // If event is fully active and not bypassed by target/schedule rules
                if (resultLog.status == "SUCCESS") {
                    sendSms(context, phoneNumber, resultLog.replyDraft)
                }

                // Save log to local database
                db.replyLogDao().insert(resultLog)

            } catch (e: Exception) {
                Log.e("CallReplyReceiver", "Failed responding to call", e)
            } finally {
                goAsync.finish()
            }
        }
    }

    private fun sendSms(context: Context, phoneNumber: String, message: String) {
        try {
            val smsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e("CallReplyReceiver", "Failed to send SMS to $phoneNumber", e)
        }
    }
}
