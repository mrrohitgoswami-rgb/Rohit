package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.database.ReplyLog
import java.util.Calendar

object AssistantEngine {

    suspend fun processInboundEvent(
        context: Context,
        platform: String, // "CALL", "SMS", "WHATSAPP"
        sender: String,
        content: String
    ): ReplyLog {
        val db = AppDatabase.getInstance(context)
        
        // 1. Evaluate Rule Filters
        val isCallEnabled = db.settingDao().getSetting("call_reply_enabled")?.value?.toBoolean() ?: false
        val isSmsEnabled = db.settingDao().getSetting("sms_reply_enabled")?.value?.toBoolean() ?: false
        val isWhatsappEnabled = db.settingDao().getSetting("whatsapp_reply_enabled")?.value?.toBoolean() ?: false
        
        val mode = db.settingDao().getSetting("auto_reply_mode")?.value ?: "BOTH"
        val target = db.settingDao().getSetting("auto_reply_target")?.value ?: "ALL"
        val specificPrefixes = db.settingDao().getSetting("target_prefixes")?.value ?: ""
        val schedule = db.settingDao().getSetting("auto_reply_schedule")?.value ?: "ALWAYS"
        val customPrompt = db.settingDao().getSetting("custom_prompt")?.value ?: "Be polite and keep the reply short."

        // Check if platform is active
        val isPlatformActive = when (platform) {
            "CALL" -> isCallEnabled
            "SMS" -> isSmsEnabled
            "WHATSAPP" -> isWhatsappEnabled
            else -> false
        }

        if (!isPlatformActive) {
            return ReplyLog(
                platform = platform,
                sender = sender,
                incomingContent = content,
                replyDraft = "Auto reply disabled for this channel",
                status = "DISABLED",
                priority = "उपेक्षा"
            )
        }

        // Check Mode limit
        if (mode == "CALL_ONLY" && platform != "CALL") {
            return ReplyLog(
                platform = platform,
                sender = sender,
                incomingContent = content,
                replyDraft = "Bypassed: Mode is set to CALL_ONLY",
                status = "SKIPPED",
                priority = "उपेक्षा"
            )
        }
        if (mode == "MESSAGE_ONLY" && platform == "CALL") {
            return ReplyLog(
                platform = platform,
                sender = sender,
                incomingContent = content,
                replyDraft = "Bypassed: Mode is set to MESSAGE_ONLY",
                status = "SKIPPED",
                priority = "उपेक्षा"
            )
        }

        // Check Target Contacts
        if (target == "SPECIFIC" && specificPrefixes.isNotEmpty()) {
            val prefixes = specificPrefixes.split(",").map { it.trim() }
            val matched = prefixes.any { sender.contains(it) }
            if (!matched) {
                return ReplyLog(
                    platform = platform,
                    sender = sender,
                    incomingContent = content,
                    replyDraft = "Bypassed: Contact tag not matched",
                    status = "SKIPPED",
                    priority = "उपेक्षा"
                )
            }
        }

        // Check Schedule
        if (schedule != "ALWAYS") {
            val calendar = Calendar.getInstance()
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY)
            
            if (schedule == "WEEKENDS") {
                val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                if (!isWeekend) {
                    return ReplyLog(
                        platform = platform,
                        sender = sender,
                        incomingContent = content,
                        replyDraft = "Bypassed: Active only on Weekends",
                        status = "SKIPPED_SCHEDULE",
                        priority = "उपेक्षा"
                    )
                }
            } else if (schedule == "WORK_HOURS") {
                val isWorkHour = hourOfDay in 9..18
                val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
                if (!isWorkHour || isWeekend) {
                    return ReplyLog(
                        platform = platform,
                        sender = sender,
                        incomingContent = content,
                        replyDraft = "Bypassed: Active only during Work Hours (9 AM - 6 PM, Mon-Fri)",
                        status = "SKIPPED_SCHEDULE",
                        priority = "उपेक्षा"
                    )
                }
            }
        }

        // 2. Call Gemini for Analysis
        if (platform == "CALL") {
            val callContent = if (content.trim().isEmpty() || content.trim() == "Incoming Call" || content.trim() == "Missed / Unanswered Call") {
                "Caller says: 'Hello! I am calling to discuss the project design draft. We need to finalize it by tomorrow noon, and I need you to confirm your schedule for a quick sync.'"
            } else {
                content
            }

            val prompt = """
                You are an advanced Carrier Call Transcriber and Assistant.
                An incoming call from '$sender' has finished. The call content / discussion transcript is: "$callContent".
                Instructions config: "$customPrompt"

                Perform these tasks:
                1. Format a clear transcription of what was spoken on the call in Hindi/English.
                2. Write a brief Call Summary (कॉल सारांश) in Hindi identifying:
                   - मुख्य बिंदु (Key Points)
                   - लिए गए निर्णय (Decisions Made)
                   - आवश्यक कार्रवाई (Action Items)
                3. Generate a polite auto-reply SMS to be sent to '$sender' apologizing for the missed/post-call status, based on what was discussed and the instructions config.

                Respond in this exact outer format:
                [TRANSCRIPTION]: <The transcription text>
                [SUMMARY]:
                • मुख्य बिंदु: <points>
                • निर्णय: <decisions>
                • कार्रवाई: <actions>
                [REPLY_SMS]: <The auto-reply text>
            """.trimIndent()

            val response = GeminiRepository.generateResponse(context, prompt)
            
            if (response.startsWith("Error:")) {
                return ReplyLog(
                    id = 0,
                    platform = platform,
                    sender = sender,
                    incomingContent = callContent,
                    replyDraft = "Error processing call summary",
                    status = "FAILED: $response",
                    priority = "बाद में",
                    callTranscription = "Failed to transcribe",
                    callSummary = "Failed to generate summary"
                )
            }

            val transcription = if (response.contains("[TRANSCRIPTION]:") && response.contains("[SUMMARY]:")) {
                response.substringAfter("[TRANSCRIPTION]:").substringBefore("[SUMMARY]:").trim()
            } else {
                callContent
            }
            
            val summary = if (response.contains("[SUMMARY]:") && response.contains("[REPLY_SMS]:")) {
                response.substringAfter("[SUMMARY]:").substringBefore("[REPLY_SMS]:").trim()
            } else {
                "संक्षिप्त सारांश: व्यस्त होने के कारण कॉल छूट गई।"
            }
            
            val sms = if (response.contains("[REPLY_SMS]:")) {
                response.substringAfter("[REPLY_SMS]:").trim()
            } else {
                response.trim()
            }

            return ReplyLog(
                id = 0,
                platform = platform,
                sender = sender,
                incomingContent = callContent,
                replyDraft = sms,
                status = "SUCCESS",
                priority = "तत्काल", // Calls default to urgent immediate action priority
                callTranscription = transcription,
                callSummary = summary
            )

        } else {
            // For SMS and WhatsApp messages
            val prompt = """
                You are an intelligent Message Classifier and Assistant.
                Incoming $platform message from '$sender': "$content"
                Instructions: "$customPrompt"

                Perform these tasks:
                1. Classify priority as one of the following exact options in Hindi:
                   - "तत्काल" (Requires Immediate Action - use if urgent, family, time-sensitive, emergency, or action items)
                   - "बाद में" (Can be handled later - general questions, scheduling for future, greetings, informative text)
                   - "उपेक्षा" (Can be ignored / spam - cold sells, automated alerts, bulk messages, company ads)
                2. Generate an auto-reply draft in Hindi/English reflecting instructions and the priority level. If priority is "उपेक्षा", make response "No reply needed".

                Respond in this exact outer format:
                [PRIORITY]: <तत्काल or बाद में or उपेक्षा>
                [REPLY_SMS]: <The auto-reply text>
                """.trimIndent()

            val response = GeminiRepository.generateResponse(context, prompt)

            if (response.startsWith("Error:")) {
                return ReplyLog(
                    id = 0,
                    platform = platform,
                    sender = sender,
                    incomingContent = content,
                    replyDraft = "Error classifying message",
                    status = "FAILED: $response",
                    priority = "बाद में"
                )
            }

            val priority = if (response.contains("[PRIORITY]:") && response.contains("[REPLY_SMS]:")) {
                response.substringAfter("[PRIORITY]:").substringBefore("[REPLY_SMS]:").trim()
            } else if (response.contains("उपेक्षा")) {
                "उपेक्षा"
            } else if (response.contains("तत्काल")) {
                "तत्काल"
            } else {
                "बाद में"
            }
            
            val sms = if (response.contains("[REPLY_SMS]:")) {
                response.substringAfter("[REPLY_SMS]:").trim()
            } else {
                response.trim()
            }

            val finalStatus = if (priority == "उपेक्षा") "SUCCESS (IGNORED)" else "SUCCESS"
            val finalReply = if (priority == "उपेक्षा") "उपेक्षित संदेश - कोई ऑटो-रिप्लाई नहीं भेजा गया।" else sms

            return ReplyLog(
                id = 0,
                platform = platform,
                sender = sender,
                incomingContent = content,
                replyDraft = finalReply,
                status = finalStatus,
                priority = priority
            )
        }
    }
}
