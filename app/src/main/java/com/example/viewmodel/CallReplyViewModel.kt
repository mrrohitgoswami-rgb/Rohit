package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ReplyLog
import com.example.data.database.Setting
import com.example.data.repository.AssistantEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CallReplyViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val replyLogDao = db.replyLogDao()
    private val settingDao = db.settingDao()

    // Logs flow
    val logs: StateFlow<List<ReplyLog>> = replyLogDao.getAllLogsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Configuration states
    private val _isCallReplyEnabled = MutableStateFlow(false)
    val isCallReplyEnabled: StateFlow<Boolean> = _isCallReplyEnabled.asStateFlow()

    private val _isSmsReplyEnabled = MutableStateFlow(false)
    val isSmsReplyEnabled: StateFlow<Boolean> = _isSmsReplyEnabled.asStateFlow()

    private val _isWhatsappReplyEnabled = MutableStateFlow(false)
    val isWhatsappReplyEnabled: StateFlow<Boolean> = _isWhatsappReplyEnabled.asStateFlow()

    private val _customPrompt = MutableStateFlow("हिंदी में विनम्रता से छोटा और प्यारा उत्तर लिखें।")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    // Custom Active Auto-Reply rule states
    private val _autoReplyMode = MutableStateFlow("BOTH") // "BOTH", "CALL_ONLY", "MESSAGE_ONLY"
    val autoReplyMode: StateFlow<String> = _autoReplyMode.asStateFlow()

    private val _autoReplyTarget = MutableStateFlow("ALL") // "ALL", "SPECIFIC"
    val autoReplyTarget: StateFlow<String> = _autoReplyTarget.asStateFlow()

    private val _targetPrefixes = MutableStateFlow("") // Comma separated, e.g. "+91, +1, 98765"
    val targetPrefixes: StateFlow<String> = _targetPrefixes.asStateFlow()

    private val _autoReplySchedule = MutableStateFlow("ALWAYS") // "ALWAYS", "WORK_HOURS", "WEEKENDS"
    val autoReplySchedule: StateFlow<String> = _autoReplySchedule.asStateFlow()

    // Active playground state
    private val _testSender = MutableStateFlow("+91 98765 43210")
    val testSender: StateFlow<String> = _testSender.asStateFlow()

    private val _testInputMessage = MutableStateFlow("क्या आप आज मिलने के लिए खाली हैं? यह बहुत महत्वपूर्ण है।")
    val testInputMessage: StateFlow<String> = _testInputMessage.asStateFlow()

    private val _testPlatform = MutableStateFlow("SMS")
    val testPlatform: StateFlow<String> = _testPlatform.asStateFlow()

    private val _testGeneratedResult = MutableStateFlow("")
    val testGeneratedResult: StateFlow<String> = _testGeneratedResult.asStateFlow()

    private val _isLoadingTest = MutableStateFlow(false)
    val isLoadingTest: StateFlow<Boolean> = _isLoadingTest.asStateFlow()

    init {
        // Load settings from database
        viewModelScope.launch(Dispatchers.IO) {
            val callReply = settingDao.getSetting("call_reply_enabled")?.value?.toBoolean() ?: false
            _isCallReplyEnabled.value = callReply

            val smsReply = settingDao.getSetting("sms_reply_enabled")?.value?.toBoolean() ?: false
            _isSmsReplyEnabled.value = smsReply

            val whatsappReply = settingDao.getSetting("whatsapp_reply_enabled")?.value?.toBoolean() ?: false
            _isWhatsappReplyEnabled.value = whatsappReply

            val prompt = settingDao.getSetting("custom_prompt")?.value ?: "हिंदी में विनम्रता से छोटा और प्यारा उत्तर लिखें।"
            _customPrompt.value = prompt

            val mode = settingDao.getSetting("auto_reply_mode")?.value ?: "BOTH"
            _autoReplyMode.value = mode

            val target = settingDao.getSetting("auto_reply_target")?.value ?: "ALL"
            _autoReplyTarget.value = target

            val prefixes = settingDao.getSetting("target_prefixes")?.value ?: ""
            _targetPrefixes.value = prefixes

            val schedule = settingDao.getSetting("auto_reply_schedule")?.value ?: "ALWAYS"
            _autoReplySchedule.value = schedule
        }
    }

    fun toggleCallReply(enabled: Boolean) {
        _isCallReplyEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("call_reply_enabled", enabled.toString()))
        }
    }

    fun toggleSmsReply(enabled: Boolean) {
        _isSmsReplyEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("sms_reply_enabled", enabled.toString()))
        }
    }

    fun toggleWhatsappReply(enabled: Boolean) {
        _isWhatsappReplyEnabled.value = enabled
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("whatsapp_reply_enabled", enabled.toString()))
        }
    }

    fun updateCustomPrompt(prompt: String) {
        _customPrompt.value = prompt
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("custom_prompt", prompt))
        }
    }

    fun updateAutoReplyMode(mode: String) {
        _autoReplyMode.value = mode
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("auto_reply_mode", mode))
        }
    }

    fun updateAutoReplyTarget(target: String) {
        _autoReplyTarget.value = target
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("auto_reply_target", target))
        }
    }

    fun updateTargetPrefixes(prefixes: String) {
        _targetPrefixes.value = prefixes
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("target_prefixes", prefixes))
        }
    }

    fun updateAutoReplySchedule(schedule: String) {
        _autoReplySchedule.value = schedule
        viewModelScope.launch(Dispatchers.IO) {
            settingDao.saveSetting(Setting("auto_reply_schedule", schedule))
        }
    }

    fun setTestSender(sender: String) {
        _testSender.value = sender
    }

    fun setTestInputMessage(msg: String) {
        _testInputMessage.value = msg
    }

    fun setTestPlatform(platform: String) {
        _testPlatform.value = platform
    }

    // Trigger local simulation directly via the centralized AssistantEngine
    fun simulateAutoReplyEvent() {
        val platform = _testPlatform.value
        val sender = _testSender.value
        val inputMsg = _testInputMessage.value

        _isLoadingTest.value = true
        _testGeneratedResult.value = "Analyzing and auto-replying..."

        viewModelScope.launch(Dispatchers.IO) {
            // First run via AssistantEngine to get analysis, priority, and summaries
            val analyzedLog = AssistantEngine.processInboundEvent(
                context = getApplication(),
                platform = platform,
                sender = sender,
                content = inputMsg
            )

            // Save the analyzed log result with its priority, transcription and summary
            val savedLogId = replyLogDao.insert(analyzedLog)

            _testGeneratedResult.value = if (analyzedLog.status.startsWith("FAILED")) {
                "Error encountered: ${analyzedLog.replyDraft}"
            } else {
                analyzedLog.replyDraft
            }

            _isLoadingTest.value = false
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            replyLogDao.clearLogs()
        }
    }
}
