package com.sip.dialer

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
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

    // Multi-Account Simultaneous Tracking
    private val accountPingMap = ConcurrentHashMap<String, String>()
    private val accountStateMap = ConcurrentHashMap<String, String>()
    private var editingAccountIndex: Int = -1

    // Active Call Info
    private var activeCallNumber = ""
    private var activeCallName = ""
    private var activeCallLineLabel = ""
    private var activeCallType = "Dialed"

    // Timers
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callStartTimeMillis = 0L
    private var isTimerRunning = false
    private var lastFormattedDuration = "00:00"

    private val pingHandler = Handler(Looper.getMainLooper())
    private var isPinging = false

    // Quality Metrics
    private var lastQualityRating = 0f
    private var lastPacketLoss = 0f
    private var lastJitter = 0f
    private var lastDelay = 0f
    private var lastCodec = "Unknown"

    // In-App Contacts Cache
    private val phoneContactsList = mutableListOf<Pair<String, String>>()
    private var currentHistoryFilter = "All"

    // UI References
    private lateinit var layoutMultiPingStrip: LinearLayout
    private lateinit var tvNetworkType: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var tvIspName: TextView
    private lateinit var btnRefreshNet: TextView

    // Tab Views
    private lateinit var tabKeypadView: View
    private lateinit var layoutActiveCallScreen: LinearLayout
    private lateinit var tabRecentsView: LinearLayout
    private lateinit var tabContactsView: LinearLayout
    private lateinit var tabSettingsView: ScrollView

    // Nav Buttons
    private lateinit var btnNavKeypad: Button
    private lateinit var btnNavRecents: Button
    private lateinit var btnNavContacts: Button
    private lateinit var btnNavSettings: Button

    // Keypad & Dial
    private lateinit var etDialNumber: EditText
    private lateinit var btnMakeCall: Button
    private lateinit var btnBackspace: Button
    private lateinit var btnRecordingsList: Button

    // Dedicated Active In-Call Views
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

    // Incoming Call Animated Overlay
    private lateinit var layoutIncomingOverlay: LinearLayout
    private lateinit var tvIncomingLineBadge: TextView
    private lateinit var tvIncomingPulseRing: TextView
    private lateinit var tvIncomingCallerName: TextView
    private lateinit var tvIncomingCallerNumber: TextView
    private lateinit var btnAnswerIncoming: Button
    private lateinit var btnDeclineIncoming: Button

    // Recents Views
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

    // In-App Contacts Views
    private lateinit var etSearchContact: EditText
    private lateinit var layoutContactsList: LinearLayout

    // Settings Views
    private lateinit var spinnerDefaultCallAccount: Spinner
    private lateinit var layoutSavedAccountsContainer: LinearLayout
    private lateinit var tvFormTitle: TextView
    private lateinit var etAccountLabel: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etDomain: EditText
    private lateinit var spinnerTransport: Spinner
    private lateinit var btnSaveAccount: Button
    private lateinit var btnCancelEdit: Button

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsedSec = (SystemClock.elapsedRealtime() - callStartTimeMillis) / 1000
                val mins = elapsedSec / 60
                val secs = elapsedSec % 60
                lastFormattedDuration = String.format(Locale.US, "%02d:%02d", mins, secs)
                tvInCallStatusTimer.text = if (isOnHold) "⏸️ ON HOLD ($lastFormattedDuration)" else "⏱ $lastFormattedDuration"
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (isPinging) {
                measureAllAccountsPing()
                pingHandler.postDelayed(this, 3500)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { updateNetworkStatusAndFetchIsp() }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            runOnUiThread { updateNetworkTypeLabel(networkCapabilities) }
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
        if (permissions[Manifest.permission.READ_CONTACTS] == true) {
            loadPhoneContacts()
        }
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(this, "Microphone permission is required for calls!", Toast.LENGTH_LONG).show()
        }
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
                val statusStr = when (state) {
                    RegistrationState.Ok -> "OK"
                    RegistrationState.Progress -> "WAIT"
                    else -> "FAIL"
                }
                accountStateMap[key] = statusStr
                refreshTopMultiPingStrip()
                if (tabSettingsView.visibility == View.VISIBLE) {
                    renderSettingsAccountsList()
                }
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
                        activeCallNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
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
                        activeCallNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                        activeCallName = resolveContactName(activeCallNumber)
                        activeCallType = "Received"
                        lastFormattedDuration = "00:00"
                        lastSavedRecordFileName = "Not Recorded"

                        // Identify which SIP account received this incoming call
                        val toUser = call.toAddress?.username ?: ""
                        val toDomain = call.toAddress?.domain ?: ""
                        activeCallLineLabel = findLabelForAccount(toUser, toDomain)

                        startIncomingRingtone()
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
                            try { call.stopRecording() } catch (_: Exception) {}
                            isRecording = false
                            btnRecord.text = "⏺\nRecord"
                            btnRecord.setBackgroundResource(R.drawable.bg_ai_pill)
                        }
                        if (activeCallNumber.isNotEmpty()) {
                            val finalType = if (lastFormattedDuration == "00:00" && activeCallType == "Received") "Missed" else activeCallType
                            saveCallToHistory(activeCallNumber, activeCallName, activeCallLineLabel, finalType, lastFormattedDuration)
                            activeCallNumber = ""
                        }
                        hideDedicatedActiveCallScreen()
                        currentCall = null
                        showCallQualitySummary()
                    }
                    Call.State.Error -> {
                        stopIncomingRingtone()
                        hideAnimatedIncomingOverlay()
                        stopCallTimer()
                        enableProximitySensor(false)
                        if (activeCallNumber.isNotEmpty()) {
                            saveCallToHistory(activeCallNumber, activeCallName, activeCallLineLabel, "Missed", "00:00")
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
        setupProximitySensor()
        initLinphoneCore()
        setupDialpad()
        setupInCallDtmfPad()
        setupListeners()
        requestAppPermissions()
        registerNetworkMonitor()
        updateNetworkStatusAndFetchIsp()
        registerAllSavedAccountsSimultaneously()
        startLivePing()
    }

    private fun initViews() {
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

        spinnerDefaultCallAccount = findViewById(R.id.spinnerDefaultCallAccount)
        layoutSavedAccountsContainer = findViewById(R.id.layoutSavedAccountsContainer)
        tvFormTitle = findViewById(R.id.tvFormTitle)
        etAccountLabel = findViewById(R.id.etAccountLabel)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etDomain = findViewById(R.id.etDomain)
        spinnerTransport = findViewById(R.id.spinnerTransport)
        btnSaveAccount = findViewById(R.id.btnSaveAccount)
        btnCancelEdit = findViewById(R.id.btnCancelEdit)

        val transports = arrayOf("UDP", "TCP", "TLS")
        spinnerTransport.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, transports)
    }

    // --- Tab Navigation with Smooth Fade Animation ---
    private fun switchMainTab(tab: String) {
        tabKeypadView.visibility = View.GONE
        tabRecentsView.visibility = View.GONE
        tabContactsView.visibility = View.GONE
        tabSettingsView.visibility = View.GONE

        btnNavKeypad.setTextColor(0xFFE2E8F0.toInt())
        btnNavRecents.setTextColor(0xFFE2E8F0.toInt())
        btnNavContacts.setTextColor(0xFFE2E8F0.toInt())
        btnNavSettings.setTextColor(0xFFE2E8F0.toInt())

        val targetView: View = when (tab) {
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
                setupDefaultCallModeSpinner()
                renderSettingsAccountsList()
                tabSettingsView
            }
            else -> {
                btnNavKeypad.setTextColor(0xFF7DD3FC.toInt())
                if (currentCall != null) layoutActiveCallScreen else tabKeypadView
            }
        }

        targetView.alpha = 0f
        targetView.visibility = View.VISIBLE
        targetView.animate().alpha(1f).setDuration(220).start()
    }

    // --- Dedicated Active Call Screen Transitions ---
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

        layoutActiveCallScreen.alpha = 0f
        layoutActiveCallScreen.visibility = View.VISIBLE
        layoutActiveCallScreen.animate().alpha(1f).setDuration(250).start()
    }

    private fun hideDedicatedActiveCallScreen() {
        layoutActiveCallScreen.visibility = View.GONE
        switchMainTab("KEYPAD")
    }

    // --- Animated Incoming Call Overlay ---
    private fun showAnimatedIncomingOverlay() {
        tvIncomingLineBadge.text = "📥 Incoming on: ${activeCallLineLabel.ifEmpty { "SIP Line" }}"
        tvIncomingCallerName.text = activeCallName.ifEmpty { activeCallNumber }
        tvIncomingCallerNumber.text = activeCallNumber

        layoutIncomingOverlay.alpha = 0f
        layoutIncomingOverlay.visibility = View.VISIBLE
        layoutIncomingOverlay.animate().alpha(1f).setDuration(250).start()

        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.18f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.18f, 1.0f)
        pulseAnimator?.cancel()
        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(tvIncomingPulseRing, scaleX, scaleY).apply {
            duration = 950
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hideAnimatedIncomingOverlay() {
        pulseAnimator?.cancel()
        layoutIncomingOverlay.visibility = View.GONE
    }

    // --- Simultaneous Multi-Account Registration & Live Ping ---
    private fun getSavedAccounts(): JSONArray {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        return JSONArray(prefs.getString("multi_accounts", "[]") ?: "[]")
    }

    private fun saveAccountsList(array: JSONArray) {
        getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
            .edit().putString("multi_accounts", array.toString()).apply()
    }

    private fun findLabelForAccount(user: String, domain: String): String {
        val accounts = getSavedAccounts()
        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            if (obj.optString("username") == user) {
                return "${obj.optString("label")} ($user)"
            }
        }
        return if (user.isNotEmpty()) "$user@$domain" else "SIP Account"
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

            val authInfo = Factory.instance().createAuthInfo(user, null, pass, null, null, domain, null)
            core.addAuthInfo(authInfo)

            val accountParams = core.createAccountParams()
            accountParams.identityAddress = Factory.instance().createAddress("sip:$user@$domain")

            val serverAddress = Factory.instance().createAddress("sip:$domain")
            serverAddress?.transport = transport
            accountParams.serverAddress = serverAddress
            accountParams.isRegisterEnabled = true

            val linphoneAcc = core.createAccount(accountParams)
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
        if (accounts.length() == 0) return

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

                accountPingMap[key] = if (pingMs >= 0) "${pingMs}ms" else "Active"
                runOnUiThread { refreshTopMultiPingStrip() }
            }
        }
    }

    private fun refreshTopMultiPingStrip() {
        layoutMultiPingStrip.removeAllViews()
        val accounts = getSavedAccounts()

        if (accounts.length() == 0) {
            val tv = TextView(this).apply {
                text = "● No SIP Account (Tap Settings to Add)"
                setTextColor(Color.WHITE)
                textSize = 12f
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
            val dot = when (st) {
                "OK" -> "🟢"
                "WAIT" -> "🟡"
                else -> "🔴"
            }

            val chip = TextView(this).apply {
                text = "$dot $label: ⚡$ping"
                setTextColor(if (st == "OK") Color.parseColor("#6EE7B7") else Color.parseColor("#FDE047"))
                textSize = 11.5f
                setTypeface(null, Typeface.BOLD)
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(22, 8, 22, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 14, 0) }
                setOnClickListener { switchMainTab("SETTINGS") }
            }
            layoutMultiPingStrip.addView(chip)
        }
    }

    // --- Outgoing Call Routing (Ask Every Time vs Default Account) ---
    private fun initiateOutgoingCallFlow(destination: String) {
        val cleanNum = destination.replace("\\s+".toRegex(), "")
        if (cleanNum.isEmpty()) {
            Toast.makeText(this, "Enter a number to call", Toast.LENGTH_SHORT).show()
            return
        }

        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            Toast.makeText(this, "Please add a SIP account in Settings first!", Toast.LENGTH_SHORT).show()
            switchMainTab("SETTINGS")
            return
        }

        if (accounts.length() == 1) {
            dialWithSpecificAccount(cleanNum, accounts.getJSONObject(0))
            return
        }

        val prefMode = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
            .getString("default_call_mode", "ASK") ?: "ASK"

        if (prefMode != "ASK") {
            val idx = prefMode.toIntOrNull() ?: 0
            if (idx in 0 until accounts.length()) {
                dialWithSpecificAccount(cleanNum, accounts.getJSONObject(idx))
                return
            }
        }

        // Ask Every Time Dialog
        val choices = Array(accounts.length()) { i ->
            val obj = accounts.getJSONObject(i)
            val lbl = obj.optString("label", "SIP ${i + 1}")
            val usr = obj.getString("username")
            val dom = obj.getString("domain")
            val ping = accountPingMap["$usr@$dom"] ?: ""
            "📡 $lbl ($usr)  [⚡$ping]"
        }

        AlertDialog.Builder(this)
            .setTitle("📞 Call $cleanNum Using:")
            .setItems(choices) { _, which ->
                dialWithSpecificAccount(cleanNum, accounts.getJSONObject(which))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dialWithSpecificAccount(destination: String, accJson: JSONObject) {
        val label = accJson.optString("label", "SIP")
        val user = accJson.getString("username")
        val domain = accJson.getString("domain")
        activeCallLineLabel = "$label ($user)"

        val matchedAccount = core.accountList.find {
            it.params.identityAddress?.username == user && it.params.identityAddress?.domain == domain
        }
        if (matchedAccount != null) {
            core.defaultAccount = matchedAccount
        }

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

    // --- In-App Contacts Loader & Search ---
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
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: "Unknown"
                    val num = (cursor.getString(numIdx) ?: "").replace("\\s+".toRegex(), "")
                    val key = "$name|$num"
                    if (num.isNotEmpty() && seen.add(key)) {
                        tempList.add(name to num)
                    }
                }
            }

            runOnUiThread {
                phoneContactsList.clear()
                phoneContactsList.addAll(tempList)
                if (tabContactsView.visibility == View.VISIBLE) {
                    renderContactsList(etSearchContact.text.toString())
                }
            }
        }
    }

    private fun resolveContactName(number: String): String {
        val clean = number.takeLast(10)
        if (clean.length < 4) return number
        val found = phoneContactsList.find { it.second.endsWith(clean) }
        return found?.first ?: number
    }

    private fun renderContactsList(query: String) {
        layoutContactsList.removeAllViews()
        val q = query.trim().lowercase(Locale.getDefault())
        val filtered = if (q.isEmpty()) phoneContactsList else {
            phoneContactsList.filter {
                it.first.lowercase(Locale.getDefault()).contains(q) || it.second.contains(q)
            }
        }

        if (filtered.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No contacts found."
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(16, 40, 16, 40)
            }
            layoutContactsList.addView(empty)
            return
        }

        for ((name, num) in filtered.take(150)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(28, 18, 28, 18)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
            }

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = name
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            }

            val tvNum = TextView(this).apply {
                text = num
                setTextColor(Color.parseColor("#BAE6FD"))
                textSize = 12f
            }

            val callIcon = TextView(this).apply {
                text = "📞"
                textSize = 20f
            }

            textCol.addView(tvName)
            textCol.addView(tvNum)
            card.addView(textCol)
            card.addView(callIcon)

            card.setOnClickListener {
                etDialNumber.setText(num)
                switchMainTab("KEYPAD")
                initiateOutgoingCallFlow(num)
            }

            layoutContactsList.addView(card)
        }
    }

    // --- Modern Chart & Table Call History ---
    private fun saveCallToHistory(number: String, name: String, line: String, type: String, duration: String) {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString("call_history", "[]") ?: "[]")

        val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val entry = JSONObject().apply {
            put("number", number)
            put("name", name)
            put("line", line)
            put("type", type)
            put("duration", duration)
            put("time", timeStr)
        }

        val updated = JSONArray()
        updated.put(entry)
        for (i in 0 until minOf(jsonArray.length(), 59)) {
            updated.put(jsonArray.getJSONObject(i))
        }
        prefs.edit().putString("call_history", updated.toString()).apply()
    }

    private fun renderCallHistoryTable(filter: String) {
        currentHistoryFilter = filter
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val jsonArray = JSONArray(prefs.getString("call_history", "[]") ?: "[]")

        // Update Tab Highlight
        val tabMap = mapOf(
            "All" to tabFilterAll,
            "Missed" to tabFilterMissed,
            "Dialed" to tabFilterDialed,
            "Received" to tabFilterReceived
        )
        for ((key, btn) in tabMap) {
            if (key == filter) {
                btn.setBackgroundColor(Color.parseColor("#38BDF8"))
                btn.setTextColor(Color.parseColor("#0F172A"))
            } else {
                btn.setBackgroundColor(Color.parseColor("#1E293B"))
                btn.setTextColor(Color.WHITE)
            }
        }

        var missed = 0
        var dialed = 0
        var received = 0
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
            val rawType = obj.optString("type", "Dialed")
            val normType = when (rawType) {
                "Outgoing" -> "Dialed"
                "Incoming" -> "Received"
                else -> rawType
            }
            if (filter != "All" && normType != filter) continue
            count++

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(16, 18, 16, 18)
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

            for ((textStr, weight, colorHex) in cells) {
                val tv = TextView(this).apply {
                    text = textStr
                    setTextColor(Color.parseColor(colorHex))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                }
                row.addView(tv)
            }

            row.setOnClickListener {
                etDialNumber.setText(num)
                switchMainTab("KEYPAD")
                initiateOutgoingCallFlow(num)
            }

            layoutHistoryTableRows.addView(row)
        }

        if (count == 0) {
            val emptyTv = TextView(this).apply {
                text = "No $filter calls recorded yet."
                setTextColor(Color.parseColor("#E0F2FE"))
                gravity = Gravity.CENTER
                setPadding(16, 40, 16, 40)
            }
            layoutHistoryTableRows.addView(emptyTv)
        }
    }

    // --- Settings Tab: Default Call Mode & Account Edit/Rename/Delete ---
    private fun setupDefaultCallModeSpinner() {
        val accounts = getSavedAccounts()
        val options = mutableListOf("❓ Ask Every Time (Choose SIP)")
        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            options.add("⭐ Default: ${obj.optString("label")} (${obj.getString("username")})")
        }

        spinnerDefaultCallAccount.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)

        val prefMode = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
            .getString("default_call_mode", "ASK") ?: "ASK"
        val selIndex = if (prefMode == "ASK") 0 else ((prefMode.toIntOrNull() ?: 0) + 1).coerceIn(0, options.size - 1)
        spinnerDefaultCallAccount.setSelection(selIndex, false)

        spinnerDefaultCallAccount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val modeVal = if (position == 0) "ASK" else (position - 1).toString()
                getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
                    .edit().putString("default_call_mode", modeVal).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun renderSettingsAccountsList() {
        layoutSavedAccountsContainer.removeAllViews()
        val accounts = getSavedAccounts()

        if (accounts.length() == 0) {
            val tv = TextView(this).apply {
                text = "No accounts added yet. Add one below."
                setTextColor(Color.parseColor("#E0F2FE"))
                textSize = 12f
            }
            layoutSavedAccountsContainer.addView(tv)
            return
        }

        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            val label = obj.optString("label", "SIP")
            val user = obj.getString("username")
            val domain = obj.getString("domain")
            val tr = obj.optString("transport", "UDP")
            val key = "$user@$domain"
            val ping = accountPingMap[key] ?: "--"
            val st = accountStateMap[key] ?: "WAIT"
            val dot = if (st == "OK") "🟢" else "🟡"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ai_pill)
                setPadding(24, 14, 20, 14)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 10) }
            }

            val infoTv = TextView(this).apply {
                text = "$dot $label ($user)\n🌐 $domain ($tr) • ⚡$ping"
                setTextColor(Color.WHITE)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val btnEdit = Button(this).apply {
                text = "✏️"
                textSize = 13f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(95, 95)
                setOnClickListener {
                    editingAccountIndex = i
                    tvFormTitle.text = "✏️ Edit Account: $label"
                    etAccountLabel.setText(label)
                    etUsername.setText(user)
                    etPassword.setText(obj.getString("password"))
                    etDomain.setText(domain)
                    spinnerTransport.setSelection(when (tr) { "TCP" -> 1; "TLS" -> 2; else -> 0 })
                    btnSaveAccount.text = "✅ Update Account"
                    btnCancelEdit.visibility = View.VISIBLE
                }
            }

            val btnDelete = Button(this).apply {
                text = "🗑️"
                textSize = 13f
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(95, 95)
                setOnClickListener {
                    val newArr = JSONArray()
                    for (j in 0 until accounts.length()) {
                        if (j != i) newArr.put(accounts.getJSONObject(j))
                    }
                    saveAccountsList(newArr)
                    registerAllSavedAccountsSimultaneously()
                    setupDefaultCallModeSpinner()
                    renderSettingsAccountsList()
                    Toast.makeText(this@MainActivity, "Deleted $label", Toast.LENGTH_SHORT).show()
                }
            }

            row.addView(infoTv)
            row.addView(btnEdit)
            row.addView(btnDelete)
            layoutSavedAccountsContainer.addView(row)
        }
    }

    private fun resetAccountForm() {
        editingAccountIndex = -1
        tvFormTitle.text = "➕ Add New SIP Account"
        etAccountLabel.setText("")
        etUsername.setText("")
        etPassword.setText("")
        etDomain.setText("")
        btnSaveAccount.text = "💾 Save & Register"
        btnCancelEdit.visibility = View.GONE
    }

    // --- In-Call DTMF Pad Setup ---
    private fun setupInCallDtmfPad() {
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        for (k in keys) {
            val b = Button(this).apply {
                text = k
                setTextColor(Color.WHITE)
                textSize = 15f
                setBackgroundResource(R.drawable.bg_ai_pill)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 135
                    height = 95
                    setMargins(8, 6, 8, 6)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentCall?.sendDtmf(k[0])
                    tvDtmfEntered.append(k)
                }
            }
            gridInCallDtmf.addView(b)
        }
    }

    // --- Ringtone & Vibration ---
    private fun startIncomingRingtone() {
        try {
            stopIncomingRingtone()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
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
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
                    .defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            }
        } catch (_: Exception) {}
    }

    private fun stopIncomingRingtone() {
        try {
            if (incomingRingtone?.isPlaying == true) incomingRingtone?.stop()
        } catch (_: Exception) {}
        incomingRingtone = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator.cancel()
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).cancel()
            }
        } catch (_: Exception) {}
    }

    // --- Network Telemetry ---
    private fun registerNetworkMonitor() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerNetworkCallback(
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                networkCallback
            )
        } catch (_: Exception) {}
    }

    private fun updateNetworkTypeLabel(caps: NetworkCapabilities?) {
        if (caps == null) {
            tvNetworkType.text = "⚠️ Offline"
            return
        }
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                tvNetworkType.text = "📶 Wi-Fi"
                tvNetworkType.setTextColor(0xFF6EE7B7.toInt())
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                tvNetworkType.text = "📡 Mobile Data"
                tvNetworkType.setTextColor(0xFFFDE047.toInt())
            }
            else -> {
                tvNetworkType.text = "🌐 Online"
                tvNetworkType.setTextColor(0xFFFFFFFF.toInt())
            }
        }
    }

    private fun updateNetworkStatusAndFetchIsp() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        updateNetworkTypeLabel(caps)

        if (caps == null) {
            tvPublicIp.text = "🌐 IP: No Network"
            tvIspName.text = "ISP: Offline"
            return
        }

        tvPublicIp.text = "🌐 IP: Fetching..."
        tvIspName.text = "ISP: Detecting..."

        thread {
            var ipResult = "Unavailable"
            var ispResult = "Unknown ISP"
            try {
                val conn = URL("http://ip-api.com/json/?fields=status,query,isp,org").openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (json.optString("status") == "success") {
                    ipResult = json.optString("query", "Unknown")
                    ispResult = json.optString("isp", "").ifEmpty { json.optString("org", "Unknown ISP") }
                }
            } catch (_: Exception) {
                try {
                    val conn2 = URL("https://ipwho.is/").openConnection() as HttpURLConnection
                    conn2.connectTimeout = 4000
                    conn2.readTimeout = 4000
                    val json2 = JSONObject(conn2.inputStream.bufferedReader().use { it.readText() })
                    if (json2.optBoolean("success", false)) {
                        ipResult = json2.optString("ip", "Unknown")
                        ispResult = json2.optJSONObject("connection")?.optString("isp", "Unknown ISP") ?: "Unknown ISP"
                    }
                } catch (_: Exception) {}
            }

            runOnUiThread {
                tvPublicIp.text = "🌐 IP: $ipResult"
                tvIspName.text = "ISP: $ispResult"
            }
        }
    }

    // --- Recordings & Helpers ---
    private fun getRecordingsDirectory(): File {
        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(publicMusic, "SIP_Recordings")
        if (!dir.exists() && !dir.mkdirs()) {
            val fallback = File(getExternalFilesDir(null), "Recordings")
            if (!fallback.exists()) fallback.mkdirs()
            return fallback
        }
        return dir
    }

    private fun prepareRecordFilePath(prefix: String): String {
        val dir = getRecordingsDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(dir, "${prefix}_$timestamp.wav")
        currentRecordFilePath = file.absolutePath
        return file.absolutePath
    }

    private fun showRecordingsDialog() {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) && file.length() > 44 }
            ?.sortedByDescending { it.lastModified() }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "No recordings found in Music/SIP_Recordings", Toast.LENGTH_SHORT).show()
            return
        }

        val names = Array(files.size) { i -> "🎵 ${files[i].name} (${files[i].length() / 1024} KB)" }

        AlertDialog.Builder(this)
            .setTitle("🎧 Saved Call Recordings (Tap to Play)")
            .setItems(names) { _, which ->
                try {
                    stopMediaPlayer()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(files[which].absolutePath)
                        prepare()
                        start()
                    }
                    Toast.makeText(this, "Playing: ${files[which].name}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Cannot play: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Close") { _, _ -> stopMediaPlayer() }
            .show()
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun startLivePing() {
        if (!isPinging) {
            isPinging = true
            pingHandler.post(pingRunnable)
        }
    }

    private fun stopLivePing() {
        isPinging = false
        pingHandler.removeCallbacks(pingRunnable)
    }

    private fun startCallTimer() {
        if (!isTimerRunning) {
            callStartTimeMillis = SystemClock.elapsedRealtime()
            isTimerRunning = true
            timerHandler.post(timerRunnable)
        }
    }

    private fun stopCallTimer() {
        isTimerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

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
        val span = SpannableString(full)
        span.setSpan(RelativeSizeSpan(1.35f), 0, mainDigit.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (subLetters.isNotEmpty()) {
            span.setSpan(RelativeSizeSpan(0.55f), mainDigit.length + 1, full.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return span
    }

    private fun setupDialpad() {
        val dialConfig = listOf(
            Triple(R.id.btn1, "1", ""), Triple(R.id.btn2, "2", "ABC"), Triple(R.id.btn3, "3", "DEF"),
            Triple(R.id.btn4, "4", "GHI"), Triple(R.id.btn5, "5", "JKL"), Triple(R.id.btn6, "6", "MNO"),
            Triple(R.id.btn7, "7", "PQRS"), Triple(R.id.btn8, "8", "TUV"), Triple(R.id.btn9, "9", "WXYZ"),
            Triple(R.id.btnStar, "*", ""), Triple(R.id.btn0, "0", "+"), Triple(R.id.btnHash, "#", "")
        )

        for ((id, digit, letters) in dialConfig) {
            val btn = findViewById<Button>(id)
            btn.text = formatKeyLabel(digit, letters)
            btn.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                etDialNumber.append(digit)
            }
        }

        findViewById<Button>(R.id.btn0).setOnLongClickListener {
            etDialNumber.append("+")
            true
        }

        btnBackspace.setOnClickListener {
            val text = etDialNumber.text.toString()
            if (text.isNotEmpty()) {
                etDialNumber.setText(text.substring(0, text.length - 1))
                etDialNumber.setSelection(etDialNumber.text.length)
            }
        }

        btnBackspace.setOnLongClickListener {
            etDialNumber.setText("")
            true
        }
    }

    private fun requestAppPermissions() {
        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS))
    }

    private fun initLinphoneCore() {
        val factory = Factory.instance()
        factory.setDebugMode(false, "AISipDialer")
        core = factory.createCore(null, null, this)
        core.addListener(coreListener)
        core.start()
    }

    private fun setupListeners() {
        btnNavKeypad.setOnClickListener { switchMainTab("KEYPAD") }
        btnNavRecents.setOnClickListener { switchMainTab("RECENTS") }
        btnNavContacts.setOnClickListener { switchMainTab("CONTACTS") }
        btnNavSettings.setOnClickListener { switchMainTab("SETTINGS") }

        btnRefreshNet.setOnClickListener {
            updateNetworkStatusAndFetchIsp()
            measureAllAccountsPing()
            Toast.makeText(this, "Refreshing Network & All SIP Pings...", Toast.LENGTH_SHORT).show()
        }

        btnRecordingsList.setOnClickListener { showRecordingsDialog() }
        btnMakeCall.setOnClickListener { initiateOutgoingCallFlow(etDialNumber.text.toString()) }
        btnEndCall.setOnClickListener { currentCall?.terminate() }

        // Incoming Overlay Buttons
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

        // In-Call Controls
        btnMute.setOnClickListener {
            core.isMicEnabled = !core.isMicEnabled
            btnMute.text = if (core.isMicEnabled) "🔇\nMute" else "🎙️\nUnmute"
        }

        btnHold.setOnClickListener {
            val call = currentCall ?: return@setOnClickListener
            if (!isOnHold) {
                call.pause()
                isOnHold = true
                btnHold.text = "▶️\nResume"
                btnHold.setBackgroundColor(0xFFD97706.toInt())
            } else {
                call.resume()
                isOnHold = false
                btnHold.text = "⏸️\nHold"
                btnHold.setBackgroundResource(R.drawable.bg_ai_pill)
            }
        }

        btnSpeaker.setOnClickListener {
            val currentDevice = core.outputAudioDevice
            val speaker = core.audioDevices.find { it.type == AudioDevice.Type.Speaker }
            val earpiece = core.audioDevices.find { it.type == AudioDevice.Type.Earpiece }
            if (currentDevice?.type == AudioDevice.Type.Speaker && earpiece != null) {
                core.outputAudioDevice = earpiece
                btnSpeaker.text = "🔊\nSpeaker"
                enableProximitySensor(true)
            } else if (speaker != null) {
                core.outputAudioDevice = speaker
                btnSpeaker.text = "📱\nEarpiece"
                enableProximitySensor(false)
            }
        }

        btnRecord.setOnClickListener {
            val call = currentCall ?: return@setOnClickListener
            if (call.state != Call.State.StreamsRunning && call.state != Call.State.Connected) {
                Toast.makeText(this, "Wait until call connects to record!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!isRecording) {
                try {
                    call.startRecording()
                    isRecording = true
                    btnRecord.text = "⏹\nStop"
                    btnRecord.setBackgroundColor(0xFFDC2626.toInt())
                    lastSavedRecordFileName = File(currentRecordFilePath ?: "").name
                    Toast.makeText(this, "🔴 Recording started...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                try { call.stopRecording() } catch (_: Exception) {}
                isRecording = false
                btnRecord.text = "⏺\nRecord"
                btnRecord.setBackgroundResource(R.drawable.bg_ai_pill)
                Toast.makeText(this, "✅ Saved in Music/SIP_Recordings", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggleInCallDtmf.setOnClickListener {
            layoutInCallDtmf.visibility = if (layoutInCallDtmf.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnInCallAddNote.setOnClickListener {
            currentCall?.let { captureCallMetrics(it) }
            Toast.makeText(
                this,
                "MOS: ${String.format(Locale.US, "%.2f", lastQualityRating)} | Codec: $lastCodec | Loss: ${String.format(Locale.US, "%.1f", lastPacketLoss)}%",
                Toast.LENGTH_LONG
            ).show()
        }

        // Recents Filter Tabs
        tabFilterAll.setOnClickListener { renderCallHistoryTable("All") }
        tabFilterMissed.setOnClickListener { renderCallHistoryTable("Missed") }
        tabFilterDialed.setOnClickListener { renderCallHistoryTable("Dialed") }
        tabFilterReceived.setOnClickListener { renderCallHistoryTable("Received") }

        btnClearHistory.setOnClickListener {
            getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).edit().remove("call_history").apply()
            renderCallHistoryTable(currentHistoryFilter)
            Toast.makeText(this, "Call history cleared", Toast.LENGTH_SHORT).show()
        }

        // Contacts Live Search
        etSearchContact.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderContactsList(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Settings Save / Update Account
        btnSaveAccount.setOnClickListener {
            val label = etAccountLabel.text.toString().trim().ifEmpty { "SIP ${getSavedAccounts().length() + 1}" }
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val domain = etDomain.text.toString().trim()
            val tr = spinnerTransport.selectedItem.toString()

            if (user.isEmpty() || pass.isEmpty() || domain.isEmpty()) {
                Toast.makeText(this, "Please fill Username, Password & Domain", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val accounts = getSavedAccounts()
            val newEntry = JSONObject().apply {
                put("label", label)
                put("username", user)
                put("password", pass)
                put("domain", domain)
                put("transport", tr)
            }

            val updated = JSONArray()
            if (editingAccountIndex in 0 until accounts.length()) {
                for (i in 0 until accounts.length()) {
                    updated.put(if (i == editingAccountIndex) newEntry else accounts.getJSONObject(i))
                }
            } else {
                for (i in 0 until accounts.length()) {
                    updated.put(accounts.getJSONObject(i))
                }
                updated.put(newEntry)
            }

            saveAccountsList(updated)
            resetAccountForm()
            registerAllSavedAccountsSimultaneously()
            setupDefaultCallModeSpinner()
            renderSettingsAccountsList()
            Toast.makeText(this, "✅ Account Saved & Registered!", Toast.LENGTH_SHORT).show()
        }

        btnCancelEdit.setOnClickListener { resetAccountForm() }
    }

    private fun captureCallMetrics(call: Call) {
        lastQualityRating = call.currentQuality
        call.audioStats?.let { stats ->
            lastPacketLoss = stats.receiverLossRate
            lastJitter = stats.jitterBufferSizeMs
            lastDelay = stats.roundTripDelay * 1000f
        }
        call.currentParams?.usedAudioPayloadType?.let {
            lastCodec = "${it.mimeType}/${it.clockRate}"
        }
    }

    private fun showCallQualitySummary() {
        val (qualityEmoji, qualityLabel) = when {
            lastQualityRating >= 4.0f -> "🌟" to "Excellent"
            lastQualityRating >= 3.0f -> "✅" to "Good"
            lastQualityRating >= 2.0f -> "⚠️" to "Fair"
            lastQualityRating > 0f -> "🔴" to "Poor"
            else -> "ℹ️" to "N/A (Short Call)"
        }

        val summary = """
            ⏱️ Call Duration: $lastFormattedDuration
            📡 SIP Line: ${activeCallLineLabel.ifEmpty { "Default" }}
            ────────────────────────
            $qualityEmoji Quality (MOS): ${String.format(Locale.US, "%.2f", lastQualityRating)} / 5.0 ($qualityLabel)
            📉 Packet Loss: ${String.format(Locale.US, "%.2f", lastPacketLoss)}%
            📶 Jitter Buffer: ${String.format(Locale.US, "%.1f", lastJitter)} ms
            ⚡ Latency / Delay: ${String.format(Locale.US, "%.0f", lastDelay)} ms
            🎙️ Audio Codec: $lastCodec
            ────────────────────────
            📁 Recording: $lastSavedRecordFileName
            📂 Folder: Music/SIP_Recordings/
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("📊 Call Summary & Quality")
            .setMessage(summary)
            .setPositiveButton("👍 OK", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopIncomingRingtone()
        pulseAnimator?.cancel()
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager).unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        stopCallTimer()
        stopLivePing()
        stopMediaPlayer()
        enableProximitySensor(false)
        core.removeListener(coreListener)
        core.stop()
    }
}
