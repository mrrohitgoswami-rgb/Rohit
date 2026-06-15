package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.ReplyLog
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras ?: return
            try {
                val pdus = bundle.get("pdus") as? Array<*> ?: return
                val format = bundle.getString("format")
                for (pdu in pdus) {
                    val message = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        SmsMessage.createFromPdu(pdu as ByteArray, format)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsMessage.createFromPdu(pdu as ByteArray)
                    }
                    val senderNum = message.originatingAddress ?: continue
                    val messageBody = message.messageBody ?: continue
                    
                    handleIncomingSms(context.applicationContext, senderNum, messageBody)
                }
            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Error parsing SMS", e)
            }
        }
    }

    private fun handleIncomingSms(context: Context, sender: String, content: String) {
        val goAsync = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                
                // Process message through central assistant engine
                val resultLog = com.example.data.repository.AssistantEngine.processInboundEvent(
                    context = context,
                    platform = "SMS",
                    sender = sender,
                    content = content
                )

                // If message active and not ignored/bypassed
                if (resultLog.status == "SUCCESS") {
                    sendSms(context, sender, resultLog.replyDraft)
                }

                // Log the transaction
                db.replyLogDao().insert(resultLog)

            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Failed responding to SMS", e)
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
            Log.e("SmsReplyReceiver", "Failed to send SMS", e)
        }
    }
}
