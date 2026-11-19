package com.sip.dialer

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.content.ContentValues
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.provider.MediaStore
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.ContactsContract
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.linphone.core.*
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var core: Core
    private var currentCall: Call? = null
    private var isRecording = false
    private var isOnHold = false
    private var currentRecordFilePath: String? = null
    private var lastSavedRecordFileName: String = "Not Recorded"
    private var mediaPlayer: MediaPlayer? = null

    private var incomingRingtone: Ringtone? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val accountPingMap = ConcurrentHashMap<String, String>()
    private val accountStateMap = ConcurrentHashMap<String, String>()
    private var editingAccountIndex: Int = -1
    private var ringtoneTargetAccountIndex: Int = -1

    private var activeCallNumber = ""
    private var activeCallName = ""
    private var activeCallLineLabel = ""
    private var activeCallLineKey = ""
    private var activeCallType = "Dialed"

    private val mainHandler = Handler(Looper.getMainLooper())
    private var callStartTimeMillis = 0L
    private var isTimerRunning = false
    private var lastFormattedDuration = "00:00"
    private var isPinging = false

    private var lastQualityRating = 0f
    private var lastPacketLoss = 0f
    private var lastJitter = 0f
    private var lastDelay = 0f
    private var lastCodec = "Unknown"

    private val phoneContactsList = mutableListOf<Pair<String, String>>()
    private var currentHistoryFilter = "All"
    private var lastBackPressTime = 0L
    private lateinit var tvAiLiveEngineStatus: TextView

    private lateinit var rootContainer: RelativeLayout
    private lateinit var tvLiveClockDate: TextView
    private lateinit var layoutMultiPingStrip: LinearLayout
    private lateinit var tvNetworkType: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var tvIspName: TextView
    private lateinit var btnRefreshNet: TextView

    private lateinit var tabKeypadView: View
    private lateinit var layoutActiveCallScreen: LinearLayout
    private lateinit var tabRecentsView: LinearLayout
    private lateinit var tabContactsView: LinearLayout
    private lateinit var tabSettingsView: FrameLayout

    private lateinit var btnNavKeypad: Button
    private lateinit var btnNavRecents: Button
    private lateinit var btnNavContacts: Button
    private lateinit var btnNavSettings: Button

    private lateinit var etDialNumber: EditText
    private lateinit var btnMakeCall: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnRecordingsList: Button

    private lateinit var tvInCallAccountBadge: TextView
    private lateinit var tvInCallName: TextView
    private lateinit var tvInCallNumber: TextView
    private lateinit var tvInCallStatusTimer: TextView
    private lateinit var btnMute: Button
    private lateinit var btnHold: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnRecord: Button
    private lateinit var btnToggleInCallDtmf: Button
    private lateinit var btnInCallAddNote: Button
    private lateinit var layoutInCallDtmf: LinearLayout
    private lateinit var tvDtmfEntered: TextView
    private lateinit var gridInCallDtmf: GridLayout
    private lateinit var btnEndCall: Button

    private lateinit var layoutIncomingOverlay: LinearLayout
    private lateinit var tvIncomingLineBadge: TextView
    private lateinit var tvIncomingPulseRing: TextView
    private lateinit var tvIncomingCallerName: TextView
    private lateinit var tvIncomingCallerNumber: TextView
    private lateinit var btnAnswerIncoming: Button
    private lateinit var btnDeclineIncoming: Button

    private lateinit var barMissed: View
    private lateinit var barDialed: View
    private lateinit var barReceived: View
    private lateinit var tvChartLegend: TextView
    private lateinit var tabFilterAll: Button
    private lateinit var tabFilterMissed: Button
    private lateinit var tabFilterDialed: Button
    private lateinit var tabFilterReceived: Button
    private lateinit var layoutHistoryTableRows: LinearLayout
    private lateinit var btnClearHistory: Button

    private lateinit var etSearchContact: EditText
    private lateinit var layoutContactsList: LinearLayout

    private lateinit var settingsMenuHub: ScrollView
    private lateinit var subCallingAccount: LinearLayout
    private lateinit var subSipAccount: ScrollView
    private lateinit var subUiThemes: LinearLayout
    private lateinit var subBackupRestore: LinearLayout
    private lateinit var subAbout: LinearLayout

    private lateinit var tvCallingAccountSub: TextView
    private lateinit var tvCurrentThemeSub: TextView
    private lateinit var switchAutoRecord: Switch
    private lateinit var rgCallingAccount: RadioGroup

    private lateinit var layoutSavedAccountsContainer: LinearLayout
    private lateinit var tvFormTitle: TextView
    private lateinit var etAccountLabel: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etDomain: EditText
    private lateinit var spinnerTransport: Spinner
    private lateinit var btnSaveAccount: Button
    private lateinit var btnCancelEdit: Button

    private val clockRunnable = object : Runnable {
        override fun run() {
            val sdf = SimpleDateFormat("hh:mm:ss a • dd MMM yyyy", Locale.getDefault())
            tvLiveClockDate.text = "🕒 ${sdf.format(Date())}"
            mainHandler.postDelayed(this, 1000)
        }
    }

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsedSec = (SystemClock.elapsedRealtime() - callStartTimeMillis) / 1000
                lastFormattedDuration = String.format(Locale.US, "%02d:%02d", elapsedSec / 60, elapsedSec % 60)
                tvInCallStatusTimer.text = if (isOnHold) "⏸️ ON HOLD ($lastFormattedDuration)" else "⏱ $lastFormattedDuration"
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (isPinging) {
                measureAllAccountsPing()
                mainHandler.postDelayed(this, 3500)
            }
        }
    }

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && ringtoneTargetAccountIndex >= 0) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            val accounts = getSavedAccounts()
            if (ringtoneTargetAccountIndex < accounts.length()) {
                val obj = accounts.getJSONObject(ringtoneTargetAccountIndex)
                obj.put("ringtone_uri", uri?.toString() ?: "")
                saveAccountsList(accounts)
                renderSettingsAccountsList()
                Toast.makeText(this, "🔔 Custom Ringtone Saved for ${obj.optString("label")}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
                val backupObj = JSONObject().apply {
                    put("app", "AI_SIP_Dialer")
                    put("version", "1.5")
                    put("exported_at", System.currentTimeMillis())
                    put("multi_accounts", JSONArray(prefs.getString("multi_accounts", "[]") ?: "[]"))
                    put("call_history", JSONArray(prefs.getString("call_history", "[]") ?: "[]"))
                    put("default_call_mode", prefs.getString("default_call_mode", "ASK"))
                    put("auto_record", prefs.getBoolean("auto_record", false))
                    put("ui_theme", prefs.getInt("ui_theme", 1))
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(backupObj.toString(2).toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(this, "✅ Backup Exported Successfully!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val rawText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val json = JSONObject(rawText)
                if (json.optString("app") != "AI_SIP_Dialer") {
                    Toast.makeText(this, "⚠️ Invalid backup file format!", Toast.LENGTH_LONG).show()
                    return@registerForActivityResult
                }
                val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putString("multi_accounts", json.optJSONArray("multi_accounts")?.toString() ?: "[]")
                    .putString("call_history", json.optJSONArray("call_history")?.toString() ?: "[]")
                    .putString("default_call_mode", json.optString("default_call_mode", "ASK"))
                    .putBoolean("auto_record", json.optBoolean("auto_record", false))
                    .putInt("ui_theme", json.optInt("ui_theme", 1))
                    .apply()

                applySavedTheme()
                switchAutoRecord.isChecked = prefs.getBoolean("auto_record", false)
                registerAllSavedAccountsSimultaneously()
                Toast.makeText(this, "✅ Backup Restored & Accounts Activated!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "⚠️ Could not read backup file!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { updateNetworkStatusAndFetchIsp() }
        }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            runOnUiThread { updateNetworkTypeLabel(caps) }
        }
        override fun onLost(network: Network) {
            runOnUiThread {
                tvNetworkType.text = "⚠️ Offline"
                tvPublicIp.text = "🌐 IP: No Network"
                tvIspName.text = "ISP: Disconnected"
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_CONTACTS] == true) loadPhoneContacts()
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String
        ) {
            runOnUiThread {
                val user = account.params.identityAddress?.username ?: ""
                val domain = account.params.identityAddress?.domain ?: ""
                val key = "$user@$domain"
                accountStateMap[key] = when (state) {
                    RegistrationState.Ok -> "OK"
                    RegistrationState.Progress -> "WAIT"
                    else -> "FAIL"
                }
                refreshTopMultiPingStrip()
                val okCount = accountStateMap.values.count { it == "OK" }
                SipKeepAliveService.startService(this@MainActivity, "$okCount SIP Account(s) Online • Ready")
                tvAiLiveEngineStatus.text = if (okCount > 0) "✨ AI Engine Ready • $okCount Line(s) Online" else "⚡ AI Engine Standby • Add SIP" 
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String
        ) {
            runOnUiThread {
                when (state) {
                    Call.State.OutgoingInit, Call.State.OutgoingProgress, Call.State.OutgoingRinging -> {
                        currentCall = call
                        isOnHold = false
                        activeCallNumber = cleanTo11DigitsIfBd(call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly())
                        activeCallName = resolveContactName(activeCallNumber)
                        activeCallType = "Dialed"
                        lastFormattedDuration = "00:00"
                        lastSavedRecordFileName = "Not Recorded"
                        showDedicatedActiveCallScreen("📤 Dialing...")
                        enableProximitySensor(true)
                    }
                    Call.State.IncomingReceived -> {
                        currentCall = call
                        isOnHold = false
                        activeCallNumber = cleanTo11DigitsIfBd(call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly())
                        activeCallName = resolveContactName(activeCallNumber)
                        activeCallType = "Received"
                        lastFormattedDuration = "00:00"
                        lastSavedRecordFileName = "Not Recorded"

                        val toUser = call.toAddress?.username ?: ""
                        val toDomain = call.toAddress?.domain ?: ""
                        activeCallLineKey = "$toUser@$toDomain"
                        activeCallLineLabel = findLabelForAccount(toUser, toDomain)

                        val customUri = findRingtoneUriForAccount(toUser)
                        startIncomingRingtone(customUri)
                        showAnimatedIncomingOverlay()
                    }
                    Call.State.Connected, Call.State.StreamsRunning -> {
                        stopIncomingRingtone()
                        hideAnimatedIncomingOverlay()
                        currentCall = call
                        showDedicatedActiveCallScreen("🟢 Connected")
                        startCallTimer()
                        enableProximitySensor(true)
                        captureCallMetrics(call)

                        val autoRec = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getBoolean("auto_record", false)
                        if (autoRec && !isRecording) {
                            try {
                                call.startRecording()
                                isRecording = true
                                btnRecord.text = "⏹\nStop"
                                btnRecord.setBackgroundColor(0xFFDC2626.toInt())
                                lastSavedRecordFileName = File(currentRecordFilePath ?: "").name
                            } catch (_: Exception) {}
                        }
                    }
                    Call.State.Paused, Call.State.PausedByRemote -> {
                        isOnHold = true
                        btnHold.text = "▶️\nResume"
                        btnHold.setBackgroundColor(0xFFD97706.toInt())
                    }
                    Call.State.End, Call.State.Released -> {
                        stopIncomingRingtone()
                        hideAnimatedIncomingOverlay()
                        stopCallTimer()
                        enableProximitySensor(false)
                        if (isRecording) {
                            try { call.stopRecording(); exportRecordingToPublicMusic(currentRecordFilePath) } catch (_: Exception) {}
                            isRecording = false
                            btnRecord.text = "⏺\nRecord"
                            btnRecord.setBackgroundResource(R.drawable.bg_ai_pill)
                        }
                        captureCallMetrics(call)
                        val pingSnapshot = accountPingMap[activeCallLineKey] ?: "${lastDelay.toInt()}ms"
                        if (activeCallNumber.isNotEmpty()) {
                            val finalType = if (lastFormattedDuration == "00:00" && activeCallType == "Received") "Missed" else activeCallType
                            saveCallToHistory(
                                activeCallNumber,
                                activeCallName,
                                activeCallLineLabel,
                                finalType,
                                lastFormattedDuration,
                                lastQualityRating,
                                pingSnapshot,
                                lastJitter,
                                lastPacketLoss,
                                lastCodec
                            )
                            activeCallNumber = ""
                        }
                        hideDedicatedActiveCallScreen()
                        currentCall = null
                        showAnimatedColorfulCallSummary(pingSnapshot)
                    }
                    Call.State.Error -> {
                        stopIncomingRingtone()
                        hideAnimatedIncomingOverlay()
                        stopCallTimer()
                        enableProximitySensor(false)
                        if (activeCallNumber.isNotEmpty()) {
                            saveCallToHistory(activeCallNumber, activeCallName, activeCallLineLabel, "Missed", "00:00", 0f, "--", 0f, 0f, "N/A")
                            activeCallNumber = ""
                        }
                        hideDedicatedActiveCallScreen()
                        currentCall = null
                        Toast.makeText(this@MainActivity, "Call Error: $message", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }

        override fun onCallStatsUpdated(core: Core, call: Call, stats: CallStats) {
            captureCallMetrics(call)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        applySavedTheme()
        setupProximitySensor()
        initLinphoneCore()
        setupDialpad()
        setupInCallDtmfPad()
        setupListeners()
        requestAppPermissions()
        registerNetworkMonitor()
        updateNetworkStatusAndFetchIsp()
        registerAllSavedAccountsSimultaneously()
        SipKeepAliveService.startService(this)
        mainHandler.post(clockRunnable)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 1. If in Settings sub-screen, go back to Settings Menu Hub
                if (tabSettingsView.visibility == View.VISIBLE && settingsMenuHub.visibility != View.VISIBLE) {
                    openSettingsSubScreen("HUB")
                    return
                }
                // 2. If in Recents, Contacts, or Settings tab, go back to Keypad tab
                if (tabRecentsView.visibility == View.VISIBLE ||
                    tabContactsView.visibility == View.VISIBLE ||
                    tabSettingsView.visibility == View.VISIBLE) {
                    switchMainTab("KEYPAD")
                    return
                }
                // 3. On Keypad tab: double-back to minimize (keeps SIP alive in background instead of killing app)
                val now = SystemClock.elapsedRealtime()
                if (now - lastBackPressTime < 2000) {
                    moveTaskToBack(true)
                } else {
                    lastBackPressTime = now
                    Toast.makeText(this@MainActivity, "Press Back again to minimize (SIP stays active)", Toast.LENGTH_SHORT).show()
                }
            }
        })
        startLivePing()
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        tvLiveClockDate = findViewById(R.id.tvLiveClockDate)
        tvAiLiveEngineStatus = findViewById(R.id.tvAiLiveEngineStatus)
        ObjectAnimator.ofFloat(tvAiLiveEngineStatus, View.ALPHA, 1f, 0.45f, 1f).apply {
            duration = 1800
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        layoutMultiPingStrip = findViewById(R.id.layoutMultiPingStrip)
        tvNetworkType = findViewById(R.id.tvNetworkType)
        tvPublicIp = findViewById(R.id.tvPublicIp)
        tvIspName = findViewById(R.id.tvIspName)
        btnRefreshNet = findViewById(R.id.btnRefreshNet)

        tabKeypadView = findViewById(R.id.tabKeypadView)
        layoutActiveCallScreen = findViewById(R.id.layoutActiveCallScreen)
        tabRecentsView = findViewById(R.id.tabRecentsView)
        tabContactsView = findViewById(R.id.tabContactsView)
        tabSettingsView = findViewById(R.id.tabSettingsView)

        btnNavKeypad = findViewById(R.id.btnNavKeypad)
        btnNavRecents = findViewById(R.id.btnNavRecents)
        btnNavContacts = findViewById(R.id.btnNavContacts)
        btnNavSettings = findViewById(R.id.btnNavSettings)

        etDialNumber = findViewById(R.id.etDialNumber)
        btnMakeCall = findViewById(R.id.btnMakeCall)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnRecordingsList = findViewById(R.id.btnRecordingsList)

        tvInCallAccountBadge = findViewById(R.id.tvInCallAccountBadge)
        tvInCallName = findViewById(R.id.tvInCallName)
        tvInCallNumber = findViewById(R.id.tvInCallNumber)
        tvInCallStatusTimer = findViewById(R.id.tvInCallStatusTimer)
        btnMute = findViewById(R.id.btnMute)
        btnHold = findViewById(R.id.btnHold)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnRecord = findViewById(R.id.btnRecord)
        btnToggleInCallDtmf = findViewById(R.id.btnToggleInCallDtmf)
        btnInCallAddNote = findViewById(R.id.btnInCallAddNote)
        layoutInCallDtmf = findViewById(R.id.layoutInCallDtmf)
        tvDtmfEntered = findViewById(R.id.tvDtmfEntered)
        gridInCallDtmf = findViewById(R.id.gridInCallDtmf)
        btnEndCall = findViewById(R.id.btnEndCall)

        layoutIncomingOverlay = findViewById(R.id.layoutIncomingOverlay)
        tvIncomingLineBadge = findViewById(R.id.tvIncomingLineBadge)
        tvIncomingPulseRing = findViewById(R.id.tvIncomingPulseRing)
        tvIncomingCallerName = findViewById(R.id.tvIncomingCallerName)
        tvIncomingCallerNumber = findViewById(R.id.tvIncomingCallerNumber)
        btnAnswerIncoming = findViewById(R.id.btnAnswerIncoming)
        btnDeclineIncoming = findViewById(R.id.btnDeclineIncoming)

        barMissed = findViewById(R.id.barMissed)
        barDialed = findViewById(R.id.barDialed)
        barReceived = findViewById(R.id.barReceived)
        tvChartLegend = findViewById(R.id.tvChartLegend)
        tabFilterAll = findViewById(R.id.tabFilterAll)
        tabFilterMissed = findViewById(R.id.tabFilterMissed)
        tabFilterDialed = findViewById(R.id.tabFilterDialed)
        tabFilterReceived = findViewById(R.id.tabFilterReceived)
        layoutHistoryTableRows = findViewById(R.id.layoutHistoryTableRows)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        etSearchContact = findViewById(R.id.etSearchContact)
        layoutContactsList = findViewById(R.id.layoutContactsList)

        settingsMenuHub = findViewById(R.id.settingsMenuHub)
        subCallingAccount = findViewById(R.id.subCallingAccount)
        subSipAccount = findViewById(R.id.subSipAccount)
        subUiThemes = findViewById(R.id.subUiThemes)
        subBackupRestore = findViewById(R.id.subBackupRestore)
        subAbout = findViewById(R.id.subAbout)

        tvCallingAccountSub = findViewById(R.id.tvCallingAccountSub)
        tvCurrentThemeSub = findViewById(R.id.tvCurrentThemeSub)
        switchAutoRecord = findViewById(R.id.switchAutoRecord)
        rgCallingAccount = findViewById(R.id.rgCallingAccount)

        layoutSavedAccountsContainer = findViewById(R.id.layoutSavedAccountsContainer)
        tvFormTitle = findViewById(R.id.tvFormTitle)
        etAccountLabel = findViewById(R.id.etAccountLabel)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etDomain = findViewById(R.id.etDomain)
        spinnerTransport = findViewById(R.id.spinnerTransport)
        btnSaveAccount = findViewById(R.id.btnSaveAccount)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)

        spinnerTransport.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("UDP", "TCP", "TLS"))
        switchAutoRecord.isChecked = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getBoolean("auto_record", false)
    }

    private fun cleanTo11DigitsIfBd(raw: String): String {
        val digitsOnly = raw.replace("[^0-9]".toRegex(), "")
        return if (digitsOnly.startsWith("8801") && digitsOnly.length == 13) {
            digitsOnly.substring(2)
        } else if (digitsOnly.startsWith("01") && digitsOnly.length == 11) {
            digitsOnly
        } else {
            digitsOnly.ifEmpty { raw.trim() }
        }
    }

    private data class AppThemeItem(val id: Int, val name: String, val bgRes: Int, val accentColor: String, val isHackerGreen: Boolean = false)

    private fun getAll20Themes(): List<AppThemeItem> = listOf(
        AppThemeItem(1, "1. Luminous Cyber-Glass (Default)", R.drawable.bg_theme_luminous, "#FFFFFF"),
        AppThemeItem(2, "2. Emerald Matrix AI", R.drawable.bg_theme_emerald, "#6EE7B7"),
        AppThemeItem(3, "3. Sunset Neon Cyber", R.drawable.bg_theme_sunset, "#FDA4AF"),
        AppThemeItem(4, "4. Royal Obsidian Gold", R.drawable.bg_theme_obsidian, "#FDE047"),
        AppThemeItem(5, "5. Arctic Aurora Glass", R.drawable.bg_theme_aurora, "#7DD3FC"),
        AppThemeItem(6, "6. 💻 Matrix Hacker (Black & Neon Green)", R.drawable.bg_theme_hacker, "#00FF41", true),
        AppThemeItem(7, "7. Cyberpunk 2077 Neon", R.drawable.bg_theme_cyberpunk, "#FACC15"),
        AppThemeItem(8, "8. Crimson Blood Vampire", R.drawable.bg_theme_crimson, "#FCA5A5"),
        AppThemeItem(9, "9. Deep Ocean Abyss", R.drawable.bg_theme_ocean, "#38BDF8"),
        AppThemeItem(10, "10. Mystic Amethyst Purple", R.drawable.bg_theme_amethyst, "#E879F9"),
        AppThemeItem(11, "11. Solar Flare Amber", R.drawable.bg_theme_solar, "#FDBA74"),
        AppThemeItem(12, "12. Midnight Stealth Blue", R.drawable.bg_theme_midnight, "#93C5FD"),
        AppThemeItem(13, "13. Enchanted Forest Pine", R.drawable.bg_theme_forest, "#86EFAC"),
        AppThemeItem(14, "14. Sakura Rose Blossom", R.drawable.bg_theme_sakura, "#F9A8D4"),
        AppThemeItem(15, "15. Dracula Dark Code", R.drawable.bg_theme_dracula, "#BD93F9"),
        AppThemeItem(16, "16. Electric Cobalt Storm", R.drawable.bg_theme_cobalt, "#60A5FA"),
        AppThemeItem(17, "17. Molten Lava Magma", R.drawable.bg_theme_lava, "#F87171"),
        AppThemeItem(18, "18. Toxic Biohazard Lime", R.drawable.bg_theme_toxic, "#A3E635"),
        AppThemeItem(19, "19. Cosmic Nebula Galaxy", R.drawable.bg_theme_galaxy, "#C084FC"),
        AppThemeItem(20, "20. Vintage Bronze Steampunk", R.drawable.bg_theme_bronze, "#FCD34D")
    )

    private fun populate20ThemesList() {
        val container = findViewById<LinearLayout>(R.id.layoutThemesListContainer) ?: return
        container.removeAllViews()
        val currentId = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getInt("ui_theme", 1)

        for (item in getAll20Themes()) {
            val isSelected = item.id == currentId
            val btn = Button(this).apply {
                text = if (isSelected) "✅ ${item.name}" else item.name
                textSize = 12.5f
                isAllCaps = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(36, 0, 28, 0)
                setTextColor(Color.parseColor(item.accentColor))
                setBackgroundResource(R.drawable.bg_ai_pill)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    118
                ).apply { setMargins(0, 0, 0, 14) }
                setOnClickListener {
                    selectUiTheme(item.id)
                    populate20ThemesList()
                }
            }
            container.addView(btn)
        }
    }

    private fun applyGlobalTextColor(view: View, colorInt: Int) {
        if (view is TextView) {
            view.setTextColor(colorInt)
            if (view is EditText) view.setHintTextColor(Color.parseColor("#8000FF41"))
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyGlobalTextColor(view.getChildAt(i), colorInt)
            }
        }
    }

    private fun applySavedTheme() {
        val themeId = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getInt("ui_theme", 1)
        val item = getAll20Themes().find { it.id == themeId } ?: getAll20Themes().first()
        rootContainer.setBackgroundResource(item.bgRes)
        tvCurrentThemeSub.text = item.name

        if (item.isHackerGreen) {
            applyGlobalTextColor(rootContainer, Color.parseColor("#00FF41"))
        } else {
            applyGlobalTextColor(rootContainer, Color.WHITE)
            tvLiveClockDate.setTextColor(Color.parseColor("#FDE047"))
            tvAiLiveEngineStatus.setTextColor(Color.parseColor("#6EE7B7"))
            tvIspName.setTextColor(Color.parseColor("#BAE6FD"))
        }
    }

    private fun selectUiTheme(themeId: Int) {
        getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().putInt("ui_theme", themeId).apply()
        applySavedTheme()
        Toast.makeText(this, "🎨 Theme Applied!", Toast.LENGTH_SHORT).show()
    }

    private fun animateViewEnter(view: View) {
        view.alpha = 0f
        view.translationY = 24f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).translationY(0f).setDuration(240).start()
    }

    private fun switchMainTab(tab: String) {
        tabKeypadView.visibility = View.GONE
        tabRecentsView.visibility = View.GONE
        tabContactsView.visibility = View.GONE
        tabSettingsView.visibility = View.GONE

        btnNavKeypad.setTextColor(0xFFE2E8F0.toInt())
        btnNavRecents.setTextColor(0xFFE2E8F0.toInt())
        btnNavContacts.setTextColor(0xFFE2E8F0.toInt())
        btnNavSettings.setTextColor(0xFFE2E8F0.toInt())

        val target: View = when (tab) {
            "RECENTS" -> {
                btnNavRecents.setTextColor(0xFF7DD3FC.toInt())
                renderCallHistoryTable(currentHistoryFilter)
                tabRecentsView
            }
            "CONTACTS" -> {
                btnNavContacts.setTextColor(0xFF7DD3FC.toInt())
                if (phoneContactsList.isEmpty()) loadPhoneContacts()
                renderContactsList(etSearchContact.text.toString())
                tabContactsView
            }
            "SETTINGS" -> {
                btnNavSettings.setTextColor(0xFF7DD3FC.toInt())
                openSettingsSubScreen("HUB")
                tabSettingsView
            }
            else -> {
                btnNavKeypad.setTextColor(0xFF7DD3FC.toInt())
                if (currentCall != null) layoutActiveCallScreen else tabKeypadView
            }
        }
        animateViewEnter(target)
    }

    private fun openSettingsSubScreen(sub: String) {
        settingsMenuHub.visibility = View.GONE
        subCallingAccount.visibility = View.GONE
        subSipAccount.visibility = View.GONE
        subUiThemes.visibility = View.GONE
        subBackupRestore.visibility = View.GONE
        subAbout.visibility = View.GONE

        val target: View = when (sub) {
            "CALLING" -> {
                populateCallingAccountRadios()
                subCallingAccount
            }
            "SIP" -> {
                renderSettingsAccountsList()
                subSipAccount
            }
            "THEMES" -> { populate20ThemesList(); subUiThemes }
            "BACKUP" -> subBackupRestore
            "ABOUT" -> subAbout
            else -> {
                updateCallingAccountSubLabel()
                settingsMenuHub
            }
        }
        animateViewEnter(target)
    }

    private fun updateCallingAccountSubLabel() {
        val pref = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getString("default_call_mode", "ASK") ?: "ASK"
        val accounts = getSavedAccounts()
        if (pref == "ASK") {
            tvCallingAccountSub.text = "Ask Every Time"
        } else {
            val idx = pref.toIntOrNull() ?: 0
            if (idx in 0 until accounts.length()) {
                val obj = accounts.getJSONObject(idx)
                tvCallingAccountSub.text = "Default: ${obj.optString("label")} (${obj.getString("username")})"
            } else {
                tvCallingAccountSub.text = "Ask Every Time"
            }
        }
    }

    private fun populateCallingAccountRadios() {
        rgCallingAccount.removeAllViews()
        val accounts = getSavedAccounts()
        val pref = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getString("default_call_mode", "ASK") ?: "ASK"

        val rbAsk = RadioButton(this).apply {
            id = View.generateViewId()
            text = "❓ Ask Every Time (Choose SIP before calling)"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(16, 20, 16, 20)
            isChecked = (pref == "ASK")
            setOnClickListener {
                getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().putString("default_call_mode", "ASK").apply()
                Toast.makeText(this@MainActivity, "Set to: Ask Every Time", Toast.LENGTH_SHORT).show()
            }
        }
        rgCallingAccount.addView(rbAsk)

        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            val rb = RadioButton(this).apply {
                id = View.generateViewId()
                text = "📡 ${obj.optString("label")} (${obj.getString("username")})"
                setTextColor(Color.WHITE)
                textSize = 14f
                setPadding(16, 20, 16, 20)
                isChecked = (pref == i.toString())
                setOnClickListener {
                    getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().putString("default_call_mode", i.toString()).apply()
                    Toast.makeText(this@MainActivity, "Default SIP: ${obj.optString("label")}", Toast.LENGTH_SHORT).show()
                }
            }
            rgCallingAccount.addView(rb)
        }
    }

    private fun showDedicatedActiveCallScreen(statusText: String) {
        tabRecentsView.visibility = View.GONE
        tabContactsView.visibility = View.GONE
        tabSettingsView.visibility = View.GONE
        tabKeypadView.visibility = View.GONE

        tvInCallAccountBadge.text = "📡 Line: ${activeCallLineLabel.ifEmpty { "SIP" }}"
        tvInCallName.text = activeCallName.ifEmpty { activeCallNumber }
        tvInCallNumber.text = activeCallNumber
        tvInCallStatusTimer.text = statusText
        btnHold.text = "⏸️\nHold"
        btnHold.setBackgroundResource(R.drawable.bg_ai_pill)
        layoutInCallDtmf.visibility = View.GONE
        tvDtmfEntered.text = "DTMF: "

        animateViewEnter(layoutActiveCallScreen)
    }

    private fun hideDedicatedActiveCallScreen() {
        layoutActiveCallScreen.visibility = View.GONE
        switchMainTab("KEYPAD")
    }

    private fun showAnimatedIncomingOverlay() {
        tvIncomingLineBadge.text = "📥 Incoming on: ${activeCallLineLabel.ifEmpty { "SIP Line" }}"
        tvIncomingCallerName.text = activeCallName.ifEmpty { activeCallNumber }
        tvIncomingCallerNumber.text = activeCallNumber

        layoutIncomingOverlay.alpha = 0f
        layoutIncomingOverlay.visibility = View.VISIBLE
        layoutIncomingOverlay.animate().alpha(1f).setDuration(250).start()

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.2f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.2f, 1.0f)
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(tvIncomingPulseRing, scaleX, scaleY).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hideAnimatedIncomingOverlay() {
        pulseAnimator?.cancel()
        layoutIncomingOverlay.visibility = View.GONE
    }

    private fun getSavedAccounts(): JSONArray {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        return JSONArray(prefs.getString("multi_accounts", "[]") ?: "[]")
    }

    private fun saveAccountsList(array: JSONArray) {
        getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().putString("multi_accounts", array.toString()).apply()
    }

    private fun findLabelForAccount(user: String, domain: String): String {
        val accounts = getSavedAccounts()
        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            if (obj.optString("username") == user) return "${obj.optString("label")} ($user)"
        }
        return if (user.isNotEmpty()) "$user@$domain" else "SIP Line"
    }

    private fun findRingtoneUriForAccount(user: String): Uri? {
        val accounts = getSavedAccounts()
        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            if (obj.optString("username") == user) {
                val u = obj.optString("ringtone_uri", "")
                if (u.isNotEmpty()) return Uri.parse(u)
            }
        }
        return null
    }

    private fun registerAllSavedAccountsSimultaneously() {
        core.clearAccounts()
        core.clearAllAuthInfo()
        accountStateMap.clear()
        accountPingMap.clear()

        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            refreshTopMultiPingStrip()
            return
        }

        for (i in 0 until accounts.length()) {
            val acc = accounts.getJSONObject(i)
            val user = acc.getString("username")
            val pass = acc.getString("password")
            val domain = acc.getString("domain")
            val transportStr = acc.optString("transport", "UDP")

            val transport = when (transportStr) {
                "TCP" -> TransportType.Tcp
                "TLS" -> TransportType.Tls
                else -> TransportType.Udp
            }

            core.addAuthInfo(Factory.instance().createAuthInfo(user, null, pass, null, null, domain, null))
            val params = core.createAccountParams()
            params.identityAddress = Factory.instance().createAddress("sip:$user@$domain")
            val srv = Factory.instance().createAddress("sip:$domain")
            srv?.transport = transport
            params.serverAddress = srv
            params.isRegisterEnabled = true

            val linphoneAcc = core.createAccount(params)
            core.addAccount(linphoneAcc)
            if (i == 0) core.defaultAccount = linphoneAcc

            accountStateMap["$user@$domain"] = "WAIT"
            accountPingMap["$user@$domain"] = "-- ms"
        }

        refreshTopMultiPingStrip()
        measureAllAccountsPing()
    }

    private fun measureAllAccountsPing() {
        val accounts = getSavedAccounts()
        for (i in 0 until accounts.length()) {
            val acc = accounts.getJSONObject(i)
            val user = acc.getString("username")
            val domain = acc.getString("domain")
            val key = "$user@$domain"

            val parts = domain.split(":")
            val host = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 5060 else 5060

            thread {
                var pingMs = -1L
                try {
                    val start = SystemClock.elapsedRealtime()
                    val socket = Socket()
                    socket.connect(InetSocketAddress(host, port), 2000)
                    pingMs = SystemClock.elapsedRealtime() - start
                    socket.close()
                } catch (_: Exception) {
                    try {
                        val start = SystemClock.elapsedRealtime()
                        if (java.net.InetAddress.getByName(host).isReachable(2000)) {
                            pingMs = SystemClock.elapsedRealtime() - start
                        }
                    } catch (_: Exception) {}
                }
                accountPingMap[key] = if (pingMs >= 0) "${pingMs}ms" else "Online"
                runOnUiThread { refreshTopMultiPingStrip() }
            }
        }
    }

    private fun refreshTopMultiPingStrip() {
        layoutMultiPingStrip.removeAllViews()
        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            val tv = TextView(this).apply {
                text = "● No SIP Account Active (Tap Settings)"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setOnClickListener { switchMainTab("SETTINGS") }
            }
            layoutMultiPingStrip.addView(tv)
            return
        }

        for (i in 0 until accounts.length()) {
            val acc = accounts.getJSONObject(i)
            val label = acc.optString("label", "SIP")
            val user = acc.getString("username")
            val domain = acc.getString("domain")
            val key = "$user@$domain"
            val st = accountStateMap[key] ?: "WAIT"
            val ping = accountPingMap[key] ?: "--"
            val dot = when (st) { "OK" -> "🟢"; "WAIT" -> "🟡"; else -> "🔴" }

            val chip = TextView(this).apply {
                text = "$dot $label: ⚡$ping"
                setTextColor(if (st == "OK") Color.parseColor("#6EE7B7") else Color.parseColor("#FDE047"))
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(20, 6, 20, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 12, 0) }
                setOnClickListener { switchMainTab("SETTINGS") }
            }
            layoutMultiPingStrip.addView(chip)
        }
    }

    private fun renderSettingsAccountsList() {
        layoutSavedAccountsContainer.removeAllViews()
        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            layoutSavedAccountsContainer.addView(TextView(this).apply {
                text = "No accounts saved yet."
                setTextColor(Color.WHITE)
                textSize = 12f
            })
            return
        }

        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            val label = obj.optString("label", "SIP")
            val user = obj.getString("username")
            val domain = obj.getString("domain")
            val tr = obj.optString("transport", "UDP")
            val hasCustomRing = obj.optString("ringtone_uri", "").isNotEmpty()

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(22, 12, 14, 12)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
            }

            val infoTv = TextView(this).apply {
                text = "$label ($user)\n🌐 $domain ($tr) • ${if (hasCustomRing) "🔔 Custom Tone" else "🔔 Default"}"
                setTextColor(Color.WHITE)
                textSize = 11.5f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnRingtone = Button(this).apply {
                text = "🔔"
                textSize = 13f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(88, 88)
                setOnClickListener {
                    ringtoneTargetAccountIndex = i
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone for $label")
                    }
                    ringtonePickerLauncher.launch(intent)
                }
            }

            val btnEdit = Button(this).apply {
                text = "✏️"
                textSize = 13f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(88, 88)
                setOnClickListener {
                    editingAccountIndex = i
                    tvFormTitle.text = "✏️ Edit: $label"
                    etAccountLabel.setText(label)
                    etUsername.setText(user)
                    etPassword.setText(obj.getString("password"))
                    etDomain.setText(domain)
                    spinnerTransport.setSelection(when (tr) { "TCP" -> 1; "TLS" -> 2; else -> 0 })
                    btnSaveAccount.text = "✅ Update"
                    btnCancelEdit.visibility = View.VISIBLE
                }
            }

            val btnDelete = Button(this).apply {
                text = "🗑️"
                textSize = 13f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(88, 88)
                setOnClickListener {
                    val newArr = JSONArray()
                    for (j in 0 until accounts.length()) if (j != i) newArr.put(accounts.getJSONObject(j))
                    saveAccountsList(newArr)
                    registerAllSavedAccountsSimultaneously()
                    renderSettingsAccountsList()
                }
            }

            row.addView(infoTv)
            row.addView(btnRingtone)
            row.addView(btnEdit)
            row.addView(btnDelete)
            layoutSavedAccountsContainer.addView(row)
        }
    }

    private fun initiateOutgoingCallFlow(destination: String) {
        val cleanNum = cleanTo11DigitsIfBd(destination)
        if (cleanNum.isEmpty()) {
            Toast.makeText(this, "Enter a number to call", Toast.LENGTH_SHORT).show()
            return
        }

        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            Toast.makeText(this, "Add a SIP account in Settings first!", Toast.LENGTH_SHORT).show()
            switchMainTab("SETTINGS")
            return
        }

        if (accounts.length() == 1) {
            dialWithSpecificAccount(cleanNum, accounts.getJSONObject(0))
            return
        }

        val prefMode = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getString("default_call_mode", "ASK") ?: "ASK"
        if (prefMode != "ASK") {
            val idx = prefMode.toIntOrNull() ?: 0
            if (idx in 0 until accounts.length()) {
                dialWithSpecificAccount(cleanNum, accounts.getJSONObject(idx))
                return
            }
        }

        val choices = Array(accounts.length()) { i ->
            val obj = accounts.getJSONObject(i)
            val lbl = obj.optString("label", "SIP")
            val usr = obj.getString("username")
            val dom = obj.getString("domain")
            val ping = accountPingMap["$usr@$dom"] ?: ""
            "📡 $lbl ($usr)  [⚡$ping]"
        }

        AlertDialog.Builder(this)
            .setTitle("📞 Call $cleanNum Using:")
            .setItems(choices) { _, which -> dialWithSpecificAccount(cleanNum, accounts.getJSONObject(which)) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dialWithSpecificAccount(destination: String, accJson: JSONObject) {
        val label = accJson.optString("label", "SIP")
        val user = accJson.getString("username")
        val domain = accJson.getString("domain")
        activeCallLineKey = "$user@$domain"
        activeCallLineLabel = "$label ($user)"

        core.accountList.find {
            it.params.identityAddress?.username == user && it.params.identityAddress?.domain == domain
        }?.let { core.defaultAccount = it }

        val sipUri = if (destination.startsWith("sip:")) destination else "sip:$destination@$domain"
        val remoteAddress = Factory.instance().createAddress(sipUri) ?: return
        val callParams = core.createCallParams(null) ?: return
        callParams.isAudioEnabled = true
        callParams.recordFile = prepareRecordFilePath("OutCall")

        lastQualityRating = 0f
        lastPacketLoss = 0f
        lastJitter = 0f
        lastDelay = 0f
        lastCodec = callParams.usedAudioPayloadType?.mimeType ?: "Default"

        core.inviteAddressWithParams(remoteAddress, callParams)
    }

    private fun loadPhoneContacts() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestAppPermissions()
            return
        }

        thread {
            val tempList = mutableListOf<Pair<String, String>>()
            val seen = HashSet<String>()
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val formatted11 = cleanTo11DigitsIfBd(cursor.getString(numIdx) ?: "")
                    if (formatted11.isNotEmpty() && seen.add("$name|$formatted11")) {
                        tempList.add(name to formatted11)
                    }
                }
            }
            runOnUiThread {
                phoneContactsList.clear()
                phoneContactsList.addAll(tempList)
                if (tabContactsView.visibility == View.VISIBLE) renderContactsList(etSearchContact.text.toString())
            }
        }
    }

    private fun resolveContactName(number: String): String {
        val clean = number.takeLast(10)
        if (clean.length < 4) return number
        return phoneContactsList.find { it.second.endsWith(clean) }?.first ?: number
    }

    private fun renderContactsList(query: String) {
        layoutContactsList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) phoneContactsList else {
            phoneContactsList.filter { it.first.lowercase(Locale.getDefault()).contains(q) || it.second.contains(q) }
        }

        if (filtered.isEmpty()) {
            layoutContactsList.addView(TextView(this).apply {
                text = "No contacts found."
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(16, 40, 16, 40)
            })
            return
        }

        for ((name, num11) in filtered.take(150)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(26, 16, 26, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
            }

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            col.addView(TextView(this).apply {
                text = num11
                setTextColor(Color.parseColor("#BAE6FD"))
                textSize = 12f
            })

            card.addView(col)
            card.addView(TextView(this).apply { text = "📞"; textSize = 19f })

            card.setOnClickListener {
                etDialNumber.setText(num11)
                switchMainTab("KEYPAD")
                initiateOutgoingCallFlow(num11)
            }
            layoutContactsList.addView(card)
        }
    }

    private fun saveCallToHistory(
        number: String, name: String, line: String, type: String, duration: String,
        mos: Float, ping: String, jitter: Float, loss: Float, codec: String
    ) {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString("call_history", "[]") ?: "[]")

        val entry = JSONObject().apply {
            put("number", number)
            put("name", name)
            put("line", line)
            put("type", type)
            put("duration", duration)
            put("time", SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date()))
            put("mos", String.format(Locale.US, "%.2f", mos))
            put("ping", ping)
            put("jitter", String.format(Locale.US, "%.1f ms", jitter))
            put("loss", String.format(Locale.US, "%.2f%%", loss))
            put("codec", codec)
        }

        val updated = JSONArray().apply { put(entry) }
        for (i in 0 until minOf(jsonArray.length(), 69)) updated.put(jsonArray.getJSONObject(i))
        prefs.edit().putString("call_history", updated.toString()).apply()
    }

    private fun renderCallHistoryTable(filter: String) {
        currentHistoryFilter = filter
        val jsonArray = JSONArray(getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getString("call_history", "[]") ?: "[]")

        val tabMap = mapOf("All" to tabFilterAll, "Missed" to tabFilterMissed, "Dialed" to tabFilterDialed, "Received" to tabFilterReceived)
        for ((key, btn) in tabMap) {
            if (key == filter) {
                btn.setBackgroundColor(Color.parseColor("#38BDF8"))
                btn.setTextColor(Color.parseColor("#0F172A"))
            } else {
                btn.setBackgroundColor(Color.parseColor("#1E293B"))
                btn.setTextColor(Color.WHITE)
            }
        }

        var missed = 0; var dialed = 0; var received = 0
        for (i in 0 until jsonArray.length()) {
            when (jsonArray.getJSONObject(i).optString("type")) {
                "Missed" -> missed++
                "Dialed", "Outgoing" -> dialed++
                "Received", "Incoming" -> received++
            }
        }
        tvChartLegend.text = "❌ Missed: $missed   |   📤 Dialed: $dialed   |   📥 Received: $received"
        (barMissed.layoutParams as LinearLayout.LayoutParams).weight = maxOf(missed.toFloat(), 0.2f)
        (barDialed.layoutParams as LinearLayout.LayoutParams).weight = maxOf(dialed.toFloat(), 0.2f)
        (barReceived.layoutParams as LinearLayout.LayoutParams).weight = maxOf(received.toFloat(), 0.2f)
        barMissed.requestLayout()

        layoutHistoryTableRows.removeAllViews()
        var count = 0

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val normType = when (val r = obj.optString("type", "Dialed")) {
                "Outgoing" -> "Dialed"; "Incoming" -> "Received"; else -> r
            }
            if (filter != "All" && normType != filter) continue
            count++

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(14, 16, 14, 16)
                setBackgroundColor(if (count % 2 == 0) Color.parseColor("#260F172A") else Color.parseColor("#14FFFFFF"))
            }

            val (badge, badgeColor) = when (normType) {
                "Missed" -> "❌ Miss" to "#FCA5A5"
                "Received" -> "📥 Recv" to "#6EE7B7"
                else -> "📤 Dial" to "#7DD3FC"
            }

            val num = obj.optString("number", "")
            val name = obj.optString("name", num)
            val line = obj.optString("line", "")
            val dur = obj.optString("duration", "00:00")
            val time = obj.optString("time", "")

            val callerDisplay = buildString {
                append(if (name != num) "$name ($num)" else num)
                if (line.isNotEmpty()) append("\n📡 $line")
            }

            val cells = listOf(
                Triple(badge, 0.85f, badgeColor),
                Triple(callerDisplay, 1.6f, "#FFFFFF"),
                Triple(dur, 0.8f, "#FDE047"),
                Triple(time, 1.25f, "#E0F2FE")
            )
            for ((txt, w, colHex) in cells) {
                row.addView(TextView(this).apply {
                    text = txt
                    setTextColor(Color.parseColor(colHex))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
                })
            }

            row.setOnClickListener {
                showHistoryDetailDialog(obj)
            }
            layoutHistoryTableRows.addView(row)
        }
    }

    private fun showHistoryDetailDialog(obj: JSONObject) {
        val num = obj.optString("number", "")
        val name = obj.optString("name", num)
        val line = obj.optString("line", "Default")
        val type = obj.optString("type", "Dialed")
        val dur = obj.optString("duration", "00:00")
        val time = obj.optString("time", "")
        val mos = obj.optString("mos", "0.00")
        val ping = obj.optString("ping", "--")
        val jitter = obj.optString("jitter", "0.0 ms")
        val loss = obj.optString("loss", "0.00%")
        val codec = obj.optString("codec", "Unknown")

        val details = """
            👤 Contact: $name
            📱 Number: $num
            📡 SIP Line: $line
            🕒 Date & Time: $time ($type)
            ⏱️ Duration: $dur
            ━━━━━━━━━━━━━━━━━━━━━━━━
            🌟 Call Quality (MOS): $mos / 5.0
            ⚡ Server Ping: $ping
            📶 Jitter Buffer: $jitter
            📉 Packet Loss: $loss
            🎙️ Audio Codec: $codec
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📊 Call History & Quality Details")
            .setMessage(details)
            .setPositiveButton("📞 Call Again") { _, _ ->
                etDialNumber.setText(num)
                switchMainTab("KEYPAD")
                initiateOutgoingCallFlow(num)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAnimatedColorfulCallSummary(pingSnapshot: String) {
        val (qualityEmoji, qualityLabel, badgeHex) = when {
            lastQualityRating >= 4.0f -> Triple("🌟", "EXCELLENT HD", "#34D399")
            lastQualityRating >= 3.0f -> Triple("✅", "GOOD QUALITY", "#38BDF8")
            lastQualityRating >= 2.0f -> Triple("⚠️", "FAIR AUDIO", "#FBBF24")
            lastQualityRating > 0f -> Triple("🔴", "WEAK SIGNAL", "#F87171")
            else -> Triple("✨", "CALL ENDED", "#A78BFA")
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_summary_gradient)
            setPadding(44, 40, 44, 36)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        card.addView(TextView(this).apply {
            text = "$qualityEmoji $qualityLabel"
            setTextColor(Color.parseColor(badgeHex))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
        })

        card.addView(TextView(this).apply {
            text = "⏱️ Duration: $lastFormattedDuration  •  📡 ${activeCallLineLabel.ifEmpty { "SIP" }}"
            setTextColor(Color.parseColor("#E0F2FE"))
            textSize = 13f
            setPadding(0, 10, 0, 18)
        })

        val metricsText = """
            🌟 MOS Score: ${String.format(Locale.US, "%.2f", lastQualityRating)} / 5.0
            ⚡ Server Ping: $pingSnapshot
            📶 Jitter: ${String.format(Locale.US, "%.1f", lastJitter)} ms
            📉 Packet Loss: ${String.format(Locale.US, "%.2f", lastPacketLoss)}%
            🎙️ Codec: $lastCodec
            📁 Rec: $lastSavedRecordFileName
        """.trimIndent()

        card.addView(TextView(this).apply {
            text = metricsText
            setTextColor(Color.WHITE)
            textSize = 13.5f
            setLineSpacing(8f, 1f)
            setBackgroundColor(Color.parseColor("#33000000"))
            setPadding(28, 22, 28, 22)
        })

        val btnOk = Button(this).apply {
            text = "✨ Done"
            setTextColor(Color.parseColor("#0F172A"))
            setBackgroundColor(Color.parseColor("#38BDF8"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(340, 105).apply { setMargins(0, 26, 0, 0) }
        }
        card.addView(btnOk)

        val dialog = AlertDialog.Builder(this).setView(card).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnOk.setOnClickListener { dialog.dismiss() }
        dialog.show()

        card.scaleX = 0.75f
        card.scaleY = 0.75f
        card.alpha = 0f
        card.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(360).setInterpolator(OvershootInterpolator()).start()
    }

    private fun startIncomingRingtone(customUri: Uri?) {
        try {
            stopIncomingRingtone()
            val uri = customUri
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            incomingRingtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }

            val pattern = longArrayOf(0, 700, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                else @Suppress("DEPRECATION") v.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}
    }

    private fun stopIncomingRingtone() {
        try { if (incomingRingtone?.isPlaying == true) incomingRingtone?.stop() } catch (_: Exception) {}
        incomingRingtone = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
            else @Suppress("DEPRECATION") (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
        } catch (_: Exception) {}
    }

    private fun setupInCallDtmfPad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        for (k in keys) {
            gridInCallDtmf.addView(Button(this).apply {
                text = k
                setTextColor(Color.WHITE)
                textSize = 15f
                setBackgroundResource(R.drawable.bg_ai_pill)
                layoutParams = GridLayout.LayoutParams().apply { width = 130; height = 90; setMargins(6, 5, 6, 5) }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    sendReliableDtmf(k[0])
                    tvDtmfEntered.append(k)
                }
            })
        }
    }

    private fun registerNetworkMonitor() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback)
        } catch (_: Exception) {}
    }

    private fun updateNetworkTypeLabel(caps: NetworkCapabilities?) {
        if (caps == null) { tvNetworkType.text = "⚠️ Offline"; return }
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> { tvNetworkType.text = "📶 Wi-Fi"; tvNetworkType.setTextColor(0xFF6EE7B7.toInt()) }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> { tvNetworkType.text = "📡 Mobile Data"; tvNetworkType.setTextColor(0xFFFDE047.toInt()) }
            else -> { tvNetworkType.text = "🌐 Online"; tvNetworkType.setTextColor(0xFFFFFFFF.toInt()) }
        }
    }

    private fun updateNetworkStatusAndFetchIsp() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        updateNetworkTypeLabel(caps)
        if (caps == null) { tvPublicIp.text = "🌐 IP: No Network"; tvIspName.text = "ISP: Offline"; return }

        thread {
            var ipRes = "Unavailable"; var ispRes = "Unknown ISP"
            try {
                val conn = URL("http://ip-api.com/json/?fields=status,query,isp,org").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000; conn.readTimeout = 4000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.optString("status") == "success") {
                    ipRes = json.optString("query", "Unknown")
                    ispRes = json.optString("isp", "").ifEmpty { json.optString("org", "Unknown ISP") }
                }
            } catch (_: Exception) {
                try {
                    val conn2 = URL("https://ipwho.is/").openConnection() as HttpURLConnection
                    conn2.connectTimeout = 4000; conn2.readTimeout = 4000
                    val json2 = JSONObject(conn2.inputStream.bufferedReader().use { it.readText() })
                    if (json2.optBoolean("success", false)) {
                        ipRes = json2.optString("ip", "Unknown")
                        ispRes = json2.optJSONObject("connection")?.optString("isp", "Unknown ISP") ?: "Unknown ISP"
                    }
                } catch (_: Exception) {}
            }
            runOnUiThread { tvPublicIp.text = "🌐 IP: $ipRes"; tvIspName.text = "ISP: $ispRes" }
        }
    }

    private fun sendReliableDtmf(digit: Char) {
        try {
            val call = currentCall ?: return
            // 1. Send out-of-band RFC 2833 RTP telephone-event
            call.sendDtmf(digit)
            // 2. Inject in-band DTMF audio directly into outgoing SIP voice stream (250ms)
            core.playDtmf(digit, 250)
            mainHandler.postDelayed({
                try { core.stopDtmf() } catch (_: Exception) {}
            }, 260)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun exportRecordingToPublicMusic(tempPath: String?) {
        if (tempPath.isNullOrEmpty()) return
        val srcFile = File(tempPath)
        if (!srcFile.exists() || srcFile.length() <= 44) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, srcFile.name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SIP_Recordings")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        srcFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    values.clear()
                    values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    lastSavedRecordFileName = srcFile.name
                }
            } else {
                val pubDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SIP_Recordings")
                if (!pubDir.exists()) pubDir.mkdirs()
                val dest = File(pubDir, srcFile.name)
                srcFile.copyTo(dest, overwrite = true)
                lastSavedRecordFileName = dest.name
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getRecordingsDirectory(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SIP_Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun prepareRecordFilePath(prefix: String): String {
        val file = File(getRecordingsDirectory(), "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.wav")
        currentRecordFilePath = file.absolutePath
        return file.absolutePath
    }

    private fun showRecordingsDialog() {
        val files = getRecordingsDirectory().listFiles { f -> f.extension.equals("wav", true) && f.length() > 44 }?.sortedByDescending { it.lastModified() }
        if (files.isNullOrEmpty()) { Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("🎧 Saved Recordings")
            .setItems(Array(files.size) { i -> "🎵 ${files[i].name} (${files[i].length() / 1024} KB)" }) { _, which ->
                try {
                    stopMediaPlayer()
                    mediaPlayer = MediaPlayer().apply { setDataSource(files[which].absolutePath); prepare(); start() }
                    Toast.makeText(this, "Playing: ${files[which].name}", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {}
            }
            .setNegativeButton("Close") { _, _ -> stopMediaPlayer() }
            .show()
    }

    private fun stopMediaPlayer() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startLivePing() { if (!isPinging) { isPinging = true; mainHandler.post(pingRunnable) } }
    private fun stopLivePing() { isPinging = false; mainHandler.removeCallbacks(pingRunnable) }
    private fun startCallTimer() { if (!isTimerRunning) { callStartTimeMillis = SystemClock.elapsedRealtime(); isTimerRunning = true; mainHandler.post(timerRunnable) } }
    private fun stopCallTimer() { isTimerRunning = false; mainHandler.removeCallbacks(timerRunnable) }

    private fun setupProximitySensor() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "AISipDialer::Proximity")
        }
    }

    private fun enableProximitySensor(enable: Boolean) {
        try {
            if (enable && proximityWakeLock?.isHeld == false) proximityWakeLock?.acquire(60 * 60 * 1000L)
            else if (!enable && proximityWakeLock?.isHeld == true) proximityWakeLock?.release()
        } catch (_: Exception) {}
    }

    private fun formatKeyLabel(mainDigit: String, subLetters: String): SpannableString {
        val full = if (subLetters.isEmpty()) mainDigit else "$mainDigit\n$subLetters"
        return SpannableString(full).apply {
            setSpan(RelativeSizeSpan(1.35f), 0, mainDigit.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (subLetters.isNotEmpty()) setSpan(RelativeSizeSpan(0.55f), mainDigit.length + 1, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun setupDialpad() {
        val dialConfig = listOf(
            Triple(R.id.btn1, "1", ""), Triple(R.id.btn2, "2", "ABC"), Triple(R.id.btn3, "3", "DEF"),
            Triple(R.id.btn4, "4", "GHI"), Triple(R.id.btn5, "5", "JKL"), Triple(R.id.btn6, "6", "MNO"),
            Triple(R.id.btn7, "7", "PQRS"), Triple(R.id.btn8, "8", "TUV"), Triple(R.id.btn9, "9", "WXYZ"),
            Triple(R.id.btnStar, "*", ""), Triple(R.id.btn0, "0", "+"), Triple(R.id.btnHash, "#", "")
        )
        for ((id, digit, letters) in dialConfig) {
            findViewById<Button>(id).apply {
                text = formatKeyLabel(digit, letters)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    etDialNumber.append(digit)
                    if (currentCall != null) sendReliableDtmf(digit[0])
                }
            }
        }
        findViewById<Button>(R.id.btn0).setOnLongClickListener { etDialNumber.append("+"); true }
        btnBackspace.setOnClickListener {
            val t = etDialNumber.text.toString()
            if (t.isNotEmpty()) { etDialNumber.setText(t.substring(0, t.length - 1)); etDialNumber.setSelection(etDialNumber.text.length) }
        }
        btnBackspace.setOnLongClickListener { etDialNumber.setText(""); true }
    }

    private fun requestAppPermissions() {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(list.toTypedArray())
    }

    private fun initLinphoneCore() {
        val factory = Factory.instance()
        factory.setDebugMode(false, "AISipDialer")
        core = factory.createCore(null, null, this)
        core.useRfc2833ForDtmf = true
        core.useInfoForDtmf = false

        core.addListener(coreListener)
        core.start()
    }

    private fun resetAccountForm() {
        editingAccountIndex = -1
        tvFormTitle.text = "➕ Add New SIP Account"
        etAccountLabel.setText(""); etUsername.setText(""); etPassword.setText(""); etDomain.setText("")
        btnSaveAccount.text = "💾 Save & Activate"
        btnCancelEdit.visibility = View.GONE
    }

    private fun setupListeners() {
        btnNavKeypad.setOnClickListener { switchMainTab("KEYPAD") }
        btnNavRecents.setOnClickListener { switchMainTab("RECENTS") }
        btnNavContacts.setOnClickListener { switchMainTab("CONTACTS") }
        btnNavSettings.setOnClickListener { switchMainTab("SETTINGS") }

        findViewById<LinearLayout>(R.id.menuCallingAccount).setOnClickListener { openSettingsSubScreen("CALLING") }
        findViewById<LinearLayout>(R.id.menuSipAccount).setOnClickListener { openSettingsSubScreen("SIP") }
        findViewById<LinearLayout>(R.id.menuUiThemes).setOnClickListener { openSettingsSubScreen("THEMES") }
        findViewById<LinearLayout>(R.id.menuBackupRestore).setOnClickListener { openSettingsSubScreen("BACKUP") }
        findViewById<LinearLayout>(R.id.menuAbout).setOnClickListener { openSettingsSubScreen("ABOUT") }

        findViewById<Button>(R.id.btnBackFromCalling).setOnClickListener { openSettingsSubScreen("HUB") }
        findViewById<Button>(R.id.btnBackFromSip).setOnClickListener { openSettingsSubScreen("HUB") }
        findViewById<Button>(R.id.btnBackFromThemes).setOnClickListener { openSettingsSubScreen("HUB") }
        findViewById<Button>(R.id.btnBackFromBackup).setOnClickListener { openSettingsSubScreen("HUB") }
        findViewById<Button>(R.id.btnBackFromAbout).setOnClickListener { openSettingsSubScreen("HUB") }



        switchAutoRecord.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().putBoolean("auto_record", isChecked).apply()
            Toast.makeText(this, if (isChecked) "⏺ Auto Recording ON" else "Auto Recording OFF", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnExportBackup).setOnClickListener {
            val fileName = "AI_SIP_Backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.json"
            exportBackupLauncher.launch(fileName)
        }
        findViewById<Button>(R.id.btnImportBackup).setOnClickListener {
            importBackupLauncher.launch(arrayOf("application/json", "*/*"))
        }

        btnRefreshNet.setOnClickListener {
            updateNetworkStatusAndFetchIsp()
            measureAllAccountsPing()
            Toast.makeText(this, "Refreshing Network & All SIP Pings...", Toast.LENGTH_SHORT).show()
        }

        btnRecordingsList.setOnClickListener { showRecordingsDialog() }
        btnMakeCall.setOnClickListener { initiateOutgoingCallFlow(etDialNumber.text.toString()) }
        btnEndCall.setOnClickListener { currentCall?.terminate() }

        btnAnswerIncoming.setOnClickListener {
            stopIncomingRingtone()
            hideAnimatedIncomingOverlay()
            currentCall?.let { call ->
                val params = core.createCallParams(call)
                params?.isAudioEnabled = true
                params?.recordFile = prepareRecordFilePath("InCall")
                call.acceptWithParams(params)
            }
        }
        btnDeclineIncoming.setOnClickListener {
            stopIncomingRingtone()
            hideAnimatedIncomingOverlay()
            currentCall?.decline(Reason.Declined)
        }

        btnMute.setOnClickListener {
            core.isMicEnabled = !core.isMicEnabled
            btnMute.text = if (core.isMicEnabled) "🔇\nMute" else "🎙️\nUnmute"
        }
        btnHold.setOnClickListener {
            val call = currentCall ?: return@setOnClickListener
            if (!isOnHold) { call.pause(); isOnHold = true; btnHold.text = "▶️\nResume"; btnHold.setBackgroundColor(0xFFD97706.toInt()) }
            else { call.resume(); isOnHold = false; btnHold.text = "⏸️\nHold"; btnHold.setBackgroundResource(R.drawable.bg_ai_pill) }
        }
        btnSpeaker.setOnClickListener {
            val curr = core.outputAudioDevice
            val spk = core.audioDevices.find { it.type == AudioDevice.Type.Speaker }
            val ear = core.audioDevices.find { it.type == AudioDevice.Type.Earpiece }
            if (curr?.type == AudioDevice.Type.Speaker && ear != null) { core.outputAudioDevice = ear; btnSpeaker.text = "🔊\nSpeaker"; enableProximitySensor(true) }
            else if (spk != null) { core.outputAudioDevice = spk; btnSpeaker.text = "📱\nEarpiece"; enableProximitySensor(false) }
        }
        btnRecord.setOnClickListener {
            val call = currentCall ?: return@setOnClickListener
            if (call.state != Call.State.StreamsRunning && call.state != Call.State.Connected) return@setOnClickListener
            if (!isRecording) {
                try {
                    call.startRecording(); isRecording = true; btnRecord.text = "⏹\nStop"; btnRecord.setBackgroundColor(0xFFDC2626.toInt())
                    lastSavedRecordFileName = File(currentRecordFilePath ?: "").name
                } catch (_: Exception) {}
            } else {
                try { call.stopRecording(); exportRecordingToPublicMusic(currentRecordFilePath) } catch (_: Exception) {}
                isRecording = false; btnRecord.text = "⏺\nRecord"; btnRecord.setBackgroundResource(R.drawable.bg_ai_pill)
            }
        }
        btnToggleInCallDtmf.setOnClickListener {
            layoutInCallDtmf.visibility = if (layoutInCallDtmf.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnInCallAddNote.setOnClickListener {
            val p = accountPingMap[activeCallLineKey] ?: "--"
            Toast.makeText(this, "⚡ Ping: $p | MOS: ${String.format(Locale.US, "%.2f", lastQualityRating)}", Toast.LENGTH_SHORT).show()
        }

        tabFilterAll.setOnClickListener { renderCallHistoryTable("All") }
        tabFilterMissed.setOnClickListener { renderCallHistoryTable("Missed") }
        tabFilterDialed.setOnClickListener { renderCallHistoryTable("Dialed") }
        tabFilterReceived.setOnClickListener { renderCallHistoryTable("Received") }
        btnClearHistory.setOnClickListener {
            getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().remove("call_history").apply()
            renderCallHistoryTable(currentHistoryFilter)
        }

        etSearchContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderContactsList(s?.toString() ?: "") }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnSaveAccount.setOnClickListener {
            val label = etAccountLabel.text.toString().trim().ifEmpty { "SIP ${getSavedAccounts().length() + 1}" }
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val domain = etDomain.text.toString().trim()
            val tr = spinnerTransport.selectedItem.toString()
            if (user.isEmpty() || pass.isEmpty() || domain.isEmpty()) {
                Toast.makeText(this, "Fill Username, Password & Domain", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val accounts = getSavedAccounts()
            val existingTone = if (editingAccountIndex in 0 until accounts.length()) accounts.getJSONObject(editingAccountIndex).optString("ringtone_uri", "") else ""
            val entry = JSONObject().apply {
                put("label", label); put("username", user); put("password", pass); put("domain", domain); put("transport", tr); put("ringtone_uri", existingTone)
            }
            val updated = JSONArray()
            if (editingAccountIndex in 0 until accounts.length()) {
                for (i in 0 until accounts.length()) updated.put(if (i == editingAccountIndex) entry else accounts.getJSONObject(i))
            } else {
                for (i in 0 until accounts.length()) updated.put(accounts.getJSONObject(i))
                updated.put(entry)
            }
            saveAccountsList(updated)
            resetAccountForm()
            registerAllSavedAccountsSimultaneously()
            renderSettingsAccountsList()
            Toast.makeText(this, "✅ Account Saved!", Toast.LENGTH_SHORT).show()
        }
        btnCancelEdit.setOnClickListener { resetAccountForm() }
    }

    private fun captureCallMetrics(call: Call) {
        lastQualityRating = call.currentQuality
        call.audioStats?.let {
            lastPacketLoss = it.receiverLossRate
            lastJitter = it.jitterBufferSizeMs
            lastDelay = it.roundTripDelay * 1000f
        }
        call.currentParams?.usedAudioPayloadType?.let { lastCodec = "${it.mimeType}/${it.clockRate}" }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopIncomingRingtone()
        pulseAnimator?.cancel()
        mainHandler.removeCallbacks(clockRunnable)
        try { (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        stopCallTimer()
        stopLivePing()
        stopMediaPlayer()
        enableProximitySensor(false)
        core.removeListener(coreListener)
        core.stop()
    }
}
