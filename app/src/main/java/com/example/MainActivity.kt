package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.ReplyLog
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.BentoBlue
import com.example.ui.theme.BentoGreen
import com.example.ui.theme.BentoPurple
import com.example.viewmodel.CallReplyViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: CallReplyViewModel = viewModel()

    val logs by viewModel.logs.collectAsState()
    val isCallReplyEnabled by viewModel.isCallReplyEnabled.collectAsState()
    val isSmsReplyEnabled by viewModel.isSmsReplyEnabled.collectAsState()
    val isWhatsappReplyEnabled by viewModel.isWhatsappReplyEnabled.collectAsState()
    val customPrompt by viewModel.customPrompt.collectAsState()

    // Active auto-routing rules states
    val autoReplyMode by viewModel.autoReplyMode.collectAsState()
    val autoReplyTarget by viewModel.autoReplyTarget.collectAsState()
    val targetPrefixes by viewModel.targetPrefixes.collectAsState()
    val autoReplySchedule by viewModel.autoReplySchedule.collectAsState()

    // Playground state
    val testSender by viewModel.testSender.collectAsState()
    val testInputMessage by viewModel.testInputMessage.collectAsState()
    val testPlatform by viewModel.testPlatform.collectAsState()
    val testResult by viewModel.testGeneratedResult.collectAsState()
    val isLoadingTest by viewModel.isLoadingTest.collectAsState()

    // Permissions State check
    var hasSmsPermissions by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.RECEIVE_SMS) &&
                    hasPermission(context, Manifest.permission.SEND_SMS)
        )
    }

    var hasCallPermissions by remember {
        mutableStateOf(
            hasPermission(context, Manifest.permission.READ_PHONE_STATE) &&
                    hasPermission(context, Manifest.permission.READ_CALL_LOG) &&
                    hasPermission(context, Manifest.permission.SEND_SMS)
        )
    }

    var isNotificationAccessGranted by remember {
        mutableStateOf(isNotificationServiceEnabled(context))
    }

    // Dynamic launcher to request runtime SMS and phone permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasSmsPermissions = (results[Manifest.permission.RECEIVE_SMS] == true) &&
                (results[Manifest.permission.SEND_SMS] == true)
        
        hasCallPermissions = (results[Manifest.permission.READ_PHONE_STATE] == true) &&
                (results[Manifest.permission.READ_CALL_LOG] == true) &&
                (results[Manifest.permission.SEND_SMS] == true)
    }

    // Refresh dynamic access states on Resume
    DisposableEffect(Unit) {
        isNotificationAccessGranted = isNotificationServiceEnabled(context)
        onDispose {}
    }

    // Preset instructions for fast configuration
    val presets = listOf(
        Pair("हिंदी विनम्र", "हिंदी में विनम्रता से छोटा और प्यारा उत्तर लिखें।"),
        Pair("Professional Eng", "Write a professional, crisp replies in English."),
        Pair("In Meeting", "State that I'm in an urgent meeting, ask them to wait briefly."),
        Pair("Busy / Late", "State that I'm currently busy or driving. Call back later.")
    )

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Aesthetic Gradient Header Block
        HeaderBlock()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .testTag("main_scrollable_area"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Warnings / Permissions Status Alert Area
            if (!hasSmsPermissions || !hasCallPermissions || !isNotificationAccessGranted) {
                item {
                    PermissionAlertsCard(
                        hasSms = hasSmsPermissions,
                        hasCall = hasCallPermissions,
                        hasNotification = isNotificationAccessGranted,
                        onRequestPermissions = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG
                                )
                            )
                        },
                        onRequestNotification = {
                            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Hero Status Bento Card
            item {
                HeroBentoCard(
                    isAnyActive = isCallReplyEnabled || isSmsReplyEnabled || isWhatsappReplyEnabled
                )
            }

            // Controls Bento Grid - Row 1 (Call & WhatsApp)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoSwitchCard(
                        title = "कॉल ऑटो रिप्लाई",
                        subtitle = "इनकमिंग कॉल ऑटो रिप्लाई",
                        icon = Icons.Default.Phone,
                        checked = isCallReplyEnabled,
                        activeColor = BentoBlue,
                        onCheckedChange = { viewModel.toggleCallReply(it) },
                        modifier = Modifier.weight(1f)
                    )
                    BentoSwitchCard(
                        title = "व्हाट्सएप रिप्लाई",
                        subtitle = "WhatsApp स्मार्ट रिप्लाई",
                        icon = Icons.Default.Notifications,
                        checked = isWhatsappReplyEnabled,
                        activeColor = BentoGreen,
                        onCheckedChange = { viewModel.toggleWhatsappReply(it) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Controls Bento Grid - Row 2 (SMS & Status Filler Card)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoSwitchCard(
                        title = "एसएमएस रिप्लाई",
                        subtitle = "टेक्स्ट संदेश (SMS) रिप्लाई",
                        icon = Icons.Default.Send,
                        checked = isSmsReplyEnabled,
                        activeColor = BentoPurple,
                        onCheckedChange = { viewModel.toggleSmsReply(it) },
                        modifier = Modifier.weight(1.1f)
                    )
                    
                    // Style-aligned bento stats card for Gemini model description
                    Card(
                        modifier = Modifier
                            .height(135.dp)
                            .weight(0.9f),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "AI ENGINE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Gemini Flash",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Rules Routing Bento Card
            item {
                AutoReplyRulesCard(
                    autoReplyMode = autoReplyMode,
                    autoReplyTarget = autoReplyTarget,
                    targetPrefixes = targetPrefixes,
                    autoReplySchedule = autoReplySchedule,
                    onUpdateMode = { viewModel.updateAutoReplyMode(it) },
                    onUpdateTarget = { viewModel.updateAutoReplyTarget(it) },
                    onUpdatePrefixes = { viewModel.updateTargetPrefixes(it) },
                    onUpdateSchedule = { viewModel.updateAutoReplySchedule(it) }
                )
            }

            // Prompt Modifier Block Card
            item {
                PromptConfigCard(
                    customPrompt = customPrompt,
                    presets = presets,
                    onPromptChange = { viewModel.updateCustomPrompt(it) }
                )
            }

            // Playground Simulator Section Card
            item {
                PlaygroundSimulatorCard(
                    testSender = testSender,
                    testInputMessage = testInputMessage,
                    testPlatform = testPlatform,
                    testResult = testResult,
                    isLoading = isLoadingTest,
                    onUpdateSender = { viewModel.setTestSender(it) },
                    onUpdateMessage = { viewModel.setTestInputMessage(it) },
                    onUpdatePlatform = { viewModel.setTestPlatform(it) },
                    onSimulateTrigger = { viewModel.simulateAutoReplyEvent() }
                )
            }

            // Live Activity Log list section header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "History",
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "हाल की गतिविधि (Activity Log)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (logs.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("साफ करें", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // History Logs display
            if (logs.isEmpty()) {
                item {
                    NoLogsPlaceholder()
                }
            } else {
                items(logs) { log ->
                    LogItemRow(log = log)
                }
            }
        }
    }
}

@Composable
fun HeaderBlock() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "SYSTEM STATUS",
                style = MaterialTheme.typography.labelSmall.copy(
                    letterSpacing = 1.6.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Gemini AI Assistant",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Beautiful pulsing status indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF34A853))
                )
            }
        }
        // Custom user icon container matching Design HTML
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun HeroBentoCard(isAnyActive: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Gemini active",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(MaterialTheme.colorScheme.onPrimary)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isAnyActive) "ACTIVE" else "STANDBY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Gemini सक्रिय है",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 32.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "बुद्धिमान कॉल और संदेश प्रबंधन",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun BentoSwitchCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    activeColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(135.dp)
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (checked) activeColor.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) activeColor else Color.Gray,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = activeColor,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("switch_$title")
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PermissionAlertsCard(
    hasSms: Boolean,
    hasCall: Boolean,
    hasNotification: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestNotification: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "अनुमतियां आवश्यक हैं (Permissions Needed)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            if (!hasSms || !hasCall) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• फोन कॉल और ऑटो-एसएमएस अनुमति",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("अनुमति दें", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!hasNotification) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• व्हाट्सएप रिप्लाई के लिए नोटिफिकेशन एक्सेस",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = onRequestNotification,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("सेटिंग्स खोलें", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
fun PromptConfigCard(
    customPrompt: String,
    presets: List<Pair<String, String>>,
    onPromptChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Gemini के लिए निर्देश (Custom Instructions)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "सहायक उत्तरों के लहजे, भाषा या सामग्री को बदलने के लिए निर्देश बदलें।",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = customPrompt,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("instruction_field"),
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(16.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Quick select presets list spacing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { (label, value) ->
                    val isSelected = customPrompt == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onPromptChange(value) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaygroundSimulatorCard(
    testSender: String,
    testInputMessage: String,
    testPlatform: String,
    testResult: String,
    isLoading: Boolean,
    onUpdateSender: (String) -> Unit,
    onUpdateMessage: (String) -> Unit,
    onUpdatePlatform: (String) -> Unit,
    onSimulateTrigger: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Test",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "रिप्लाई टेस्ट प्रयोगशाला (Simulation)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Selector tabs for platforms
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("CALL", "SMS", "WHATSAPP").forEach { platform ->
                    val isSelected = testPlatform == platform
                    val badgeColor = when (platform) {
                        "CALL" -> BentoBlue
                        "SMS" -> BentoPurple
                        else -> BentoGreen
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) badgeColor.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.02f)
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) badgeColor else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onUpdatePlatform(platform) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (platform == "CALL") "कॉल (Call)" else if (platform == "SMS") "एसएमएस" else "व्हाट्सएप",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) badgeColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sender input field
            OutlinedTextField(
                value = testSender,
                onValueChange = onUpdateSender,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("test_sender_input"),
                label = { Text("कॉलर / भेजने वाला व्यक्ति") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                )
            )

            if (testPlatform != "CALL") {
                Spacer(modifier = Modifier.height(12.dp))
                // Body/Message input field
                OutlinedTextField(
                    value = testInputMessage,
                    onValueChange = onUpdateMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("test_message_input"),
                    label = { Text("आने वाला संदेश सामग्री") },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Trigger simulation
            Button(
                onClick = onSimulateTrigger,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("simulate_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("प्रतिक्रिया उत्पन्न हो रही है...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gemini ऑटो रिप्लाई का परीक्षण करें", fontWeight = FontWeight.Bold)
                }
            }

            if (testResult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "सहायक द्वारा उत्पन्न उत्तर:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = testResult,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemRow(log: ReplyLog) {
    val platformColor = when (log.platform) {
        "CALL" -> BentoBlue
        "SMS" -> BentoPurple
        else -> BentoGreen
    }

    val platformIcon = when (log.platform) {
        "CALL" -> Icons.Default.Phone
        "SMS" -> Icons.Default.Send
        else -> Icons.Default.Notifications
    }

    val durationString = remember(log.timestamp) {
        try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(log.timestamp))
        } catch (e: Exception) {
            "Just now"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Row metadata
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(platformColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = platformIcon,
                        contentDescription = log.platform,
                        tint = platformColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.sender,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "प्लेटफ़ॉर्म: ${log.platform} • $durationString",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Priority Badge
                val priorityLabel = log.priority
                val priorityContainerColor = when (priorityLabel) {
                    "तत्काल" -> Color(0xFF5C1919)
                    "बाद में" -> Color(0xFF5C4319)
                    "उपेक्षा" -> Color(0xFF2C3E50)
                    else -> Color(0xFF2C3E50)
                }
                val priorityContentColor = when (priorityLabel) {
                    "तत्काल" -> Color(0xFFFF8A80)
                    "बाद में" -> Color(0xFFFFD54F)
                    "उपेक्षा" -> Color(0xFFBDC3C7)
                    else -> Color(0xFFBDC3C7)
                }

                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(priorityContainerColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = priorityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = priorityContentColor
                    )
                }

                // Status Badge
                val isSuccess = log.status.startsWith("SUCCESS")
                val isPending = log.status == "PENDING"
                val badgeContainerColor = when {
                    isSuccess -> Color(0xFF1B3B2B)
                    isPending -> Color(0xFF3F2B1B)
                    else -> Color(0xFF3B1B1B)
                }
                val badgeContentColor = when {
                    isSuccess -> Color(0xFF81C784)
                    isPending -> Color(0xFFFFB74D)
                    else -> Color(0xFFE57373)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeContainerColor)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSuccess) "सफल" else if (isPending) "चल रहा है" else "विफल",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeContentColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log details Content Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Incoming section
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "आने वाला:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.incomingContent,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Autoreply section
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(platformColor.copy(alpha = 0.04f))
                        .border(1.dp, platformColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "ऑटो रिप्लाई:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = platformColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.replyDraft,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Call Transcription and AI Summarization expanded drawer helper
            if (log.platform == "CALL" && (!log.callTranscription.isNullOrBlank() || !log.callSummary.isNullOrBlank())) {
                var isExpanded by remember { mutableStateOf(false) }
                
                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(platformColor.copy(alpha = 0.03f))
                        .border(1.dp, platformColor.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Summary",
                                tint = platformColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "कॉल ट्रांसक्रिप्शन और सारांश",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = platformColor
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text(
                            text = "कॉल ट्रांसक्रिप्शन (Speech-to-Text):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = log.callTranscription ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color.White.copy(alpha = 0.05f))
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "AI संक्षिप्त सारांश (मुख्य बिंदु / निर्णय / कार्रवाई):",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = platformColor
                        )
                        Text(
                            text = log.callSummary ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoLogsPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Notifications,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "अभी तक कोई गतिविधि नहीं है",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "परीक्षण के लिए उपर दिए गए सिम्युलेटर टूल का उपयोग करें।",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

// Utility methods for permissions checking
private fun hasPermission(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun isNotificationServiceEnabled(context: Context): Boolean {
    val packageNames = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    )
    return packageNames != null && packageNames.contains(context.packageName)
}

@Composable
fun AutoReplyRulesCard(
    autoReplyMode: String,
    autoReplyTarget: String,
    targetPrefixes: String,
    autoReplySchedule: String,
    onUpdateMode: (String) -> Unit,
    onUpdateTarget: (String) -> Unit,
    onUpdatePrefixes: (String) -> Unit,
    onUpdateSchedule: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Rules",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ऑटो-रिप्लाई सक्रियता नियम (Routing Rules)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Mode selection
            Text(
                text = "ऑटो-रिप्लाई मोड / सक्रियता निर्धारित करें:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("BOTH", "दोनों", Icons.Default.Check),
                    Triple("CALL_ONLY", "केवल कॉल", Icons.Default.Phone),
                    Triple("MESSAGE_ONLY", "केवल संदेश", Icons.Default.Send)
                ).forEach { (value, label, icon) ->
                    val isSelected = autoReplyMode == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.02f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(12.dp)
                              )
                              .clickable { onUpdateMode(value) }
                              .padding(vertical = 10.dp),
                          contentAlignment = Alignment.Center
                      ) {
                          Text(
                              text = label,
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold,
                              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                          )
                      }
                  }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // 2. Schedule selection
              Text(
                  text = "सक्रिय रहने का समय:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  listOf(
                      Pair("ALWAYS", "हमेशा active"),
                      Pair("WORK_HOURS", "मुख्य कार्य समय (9 AM-6 PM)"),
                      Pair("WEEKENDS", "सप्ताहांत (Weekends)")
                  ).forEach { (value, label) ->
                      val isSelected = autoReplySchedule == value
                      Box(
                          modifier = Modifier
                              .weight(1f)
                              .clip(RoundedCornerShape(12.dp))
                              .background(
                                  if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                  else Color.White.copy(alpha = 0.02f)
                              )
                              .border(
                                  width = 1.dp,
                                  color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                  shape = RoundedCornerShape(12.dp)
                              )
                              .clickable { onUpdateSchedule(value) }
                              .padding(vertical = 10.dp),
                          contentAlignment = Alignment.Center
                      ) {
                          Text(
                              text = label,
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold,
                              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                              maxLines = 1,
                              overflow = TextOverflow.Ellipsis
                          )
                      }
                  }
              }

              Spacer(modifier = Modifier.height(14.dp))

              // 3. Target Contacts
              Text(
                  text = "ऑटो-रिप्लाई किसे भेजें:",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontWeight = FontWeight.Bold
              )
              Spacer(modifier = Modifier.height(6.dp))
              Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                  listOf(
                      Pair("ALL", "सभी को (All Users)"),
                      Pair("SPECIFIC", "विशिष्ट संपर्कों को")
                  ).forEach { (value, label) ->
                      val isSelected = autoReplyTarget == value
                      Box(
                          modifier = Modifier
                              .weight(1f)
                              .clip(RoundedCornerShape(12.dp))
                              .background(
                                  if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                  else Color.White.copy(alpha = 0.02f)
                              )
                              .border(
                                  width = 1.dp,
                                  color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.05f),
                                  shape = RoundedCornerShape(12.dp)
                              )
                              .clickable { onUpdateTarget(value) }
                              .padding(vertical = 10.dp),
                          contentAlignment = Alignment.Center
                      ) {
                          Text(
                              text = label,
                              style = MaterialTheme.typography.labelSmall,
                              fontWeight = FontWeight.Bold,
                              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                          )
                      }
                  }
              }

              if (autoReplyTarget == "SPECIFIC") {
                  Spacer(modifier = Modifier.height(10.dp))
                  OutlinedTextField(
                      value = targetPrefixes,
                      onValueChange = onUpdatePrefixes,
                      modifier = Modifier.fillMaxWidth(),
                      placeholder = { Text("उदा. +9198, +1, रोहित") },
                      label = { Text("विशिष्ट संपर्क / कीवर्ड (अल्पविराम से विभाजित करें)") },
                      singleLine = true,
                      shape = RoundedCornerShape(12.dp),
                      colors = OutlinedTextFieldDefaults.colors(
                          focusedBorderColor = MaterialTheme.colorScheme.primary,
                          unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                      )
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                      text = "असिस्टेंट केवल उन संपर्कों को जवाब देगा जो इन कीवर्ड्स से मेल खाते हैं।",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                  )
              }
          }
      }
  }
