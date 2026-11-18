package com.sip.dialer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.ContactsContract
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.HapticFeedbackConstants
import android.view.View
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
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var core: Core
    private var currentCall: Call? = null
    private var isRecording = false
    private var currentRecordFilePath: String? = null
    private var lastSavedRecordFileName: String = "Not Recorded"
    private var mediaPlayer: MediaPlayer? = null

    private var proximityWakeLock: PowerManager.WakeLock? = null

    // Active Account Info
    private var activeDomain: String = ""
    private var activeLabel: String = ""

    // Live Call Timer
    private val timerHandler = Handler(Looper.getMainLooper())
    private var callStartTimeMillis = 0L
    private var isTimerRunning = false
    private var lastFormattedDuration = "00:00"

    // Live Server Ping
    private val pingHandler = Handler(Looper.getMainLooper())
    private var isPinging = false

    // Call History Tracking
    private var activeCallNumber = ""
    private var activeCallType = "Outgoing"

    // Quality tracking variables
    private var lastQualityRating = 0f
    private var lastPacketLoss = 0f
    private var lastJitter = 0f
    private var lastDelay = 0f
    private var lastCodec = "Unknown"

    // UI Elements
    private lateinit var tvStatus: TextView
    private lateinit var tvLivePing: TextView
    private lateinit var tvNetworkType: TextView
    private lateinit var tvPublicIp: TextView
    private lateinit var tvIspName: TextView
    private lateinit var btnRefreshNet: TextView
    private lateinit var btnNavKeypad: Button
    private lateinit var btnRecentCalls: Button
    private lateinit var btnToggleAccount: Button
    private lateinit var layoutLoginCard: LinearLayout
    private lateinit var btnSavedAccountsList: Button
    private lateinit var etAccountLabel: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etDomain: EditText
    private lateinit var spinnerTransport: Spinner
    private lateinit var btnLogin: Button
    private lateinit var etDialNumber: EditText
    private lateinit var btnBackspace: Button
    private lateinit var btnCallToggle: Button
    private lateinit var btnContacts: Button
    private lateinit var btnRecordingsList: Button
    private lateinit var layoutInCall: LinearLayout
    private lateinit var tvCallState: TextView
    private lateinit var tvCallTimer: TextView
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnRecord: Button

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isTimerRunning) {
                val elapsedSec = (SystemClock.elapsedRealtime() - callStartTimeMillis) / 1000
                val mins = elapsedSec / 60
                val secs = elapsedSec % 60
                lastFormattedDuration = String.format(Locale.US, "%02d:%02d", mins, secs)
                tvCallTimer.text = "⏱ $lastFormattedDuration"
                timerHandler.postDelayed(this, 1000)
            }
        }
    }

    private val pingRunnable = object : Runnable {
        override fun run() {
            if (isPinging) {
                measureServerPing()
                pingHandler.postDelayed(this, 3000)
            }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread {
                updateNetworkStatusAndFetchIsp()
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            runOnUiThread {
                updateNetworkTypeLabel(networkCapabilities)
            }
        }

        override fun onLost(network: Network) {
            runOnUiThread {
                tvNetworkType.text = "⚠️ No Internet"
                tvPublicIp.text = "🌐 IP: Offline"
                tvIspName.text = "ISP: Disconnected"
            }
        }
    }

    private val pickContactLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
            contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val number = cursor.getString(numberIndex).replace("\\s+".toRegex(), "")
                    etDialNumber.setText(number)
                }
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] != true) {
            Toast.makeText(this, "Microphone permission is required for calls!", Toast.LENGTH_LONG).show()
        }
    }

    private fun setCallButtonState(inCall: Boolean) {
        if (inCall) {
            btnCallToggle.setBackgroundResource(R.drawable.bg_end_call_btn)
            btnCallToggle.text = "📵"
        } else {
            btnCallToggle.setBackgroundResource(R.drawable.bg_call_btn)
            btnCallToggle.text = "📞"
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
                val tag = if (activeLabel.isNotEmpty()) "$activeLabel ($user)" else user
                when (state) {
                    RegistrationState.Ok -> {
                        tvStatus.text = "● $tag ▾"
                        tvStatus.setTextColor(0xFF00F5D4.toInt())
                        layoutLoginCard.visibility = View.GONE
                        startLivePing()
                    }
                    RegistrationState.Progress -> {
                        tvStatus.text = "● Connecting $tag..."
                        tvStatus.setTextColor(0xFFFFD166.toInt())
                    }
                    RegistrationState.Failed -> {
                        tvStatus.text = "● Failed: $tag ▾"
                        tvStatus.setTextColor(0xFFFF4D6D.toInt())
                        stopLivePing()
                    }
                    else -> {
                        tvStatus.text = "● Offline ($tag) ▾"
                        tvStatus.setTextColor(0xFFFF4D6D.toInt())
                        stopLivePing()
                    }
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
                        setCallButtonState(true)
                        activeCallNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                        activeCallType = "Outgoing"
                        lastFormattedDuration = "00:00"
                        lastSavedRecordFileName = "Not Recorded"
                        layoutInCall.visibility = View.VISIBLE
                        tvCallState.text = "📞 Calling $activeCallNumber..."
                        tvCallTimer.text = "⏱ Ringing..."
                        enableProximitySensor(true)
                    }
                    Call.State.IncomingReceived -> {
                        currentCall = call
                        setCallButtonState(true)
                        activeCallNumber = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
                        activeCallType = "Incoming"
                        lastFormattedDuration = "00:00"
                        lastSavedRecordFileName = "Not Recorded"
                        showIncomingCallDialog(call)
                    }
                    Call.State.Connected, Call.State.StreamsRunning -> {
                        currentCall = call
                        setCallButtonState(true)
                        layoutInCall.visibility = View.VISIBLE
                        tvCallState.text = "🟢 Active: $activeCallNumber"
                        startCallTimer()
                        enableProximitySensor(true)
                        captureCallMetrics(call)
                    }
                    Call.State.End, Call.State.Released -> {
                        stopCallTimer()
                        enableProximitySensor(false)
                        setCallButtonState(false)
                        if (isRecording) {
                            try {
                                call.stopRecording()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            isRecording = false
                            btnRecord.text = "⏺ Record"
                            btnRecord.setBackgroundColor(0xFF1E293B.toInt())
                        }
                        if (activeCallNumber.isNotEmpty()) {
                            val finalType = if (lastFormattedDuration == "00:00" && activeCallType == "Incoming") "Missed" else activeCallType
                            saveCallToHistory(activeCallNumber, finalType, lastFormattedDuration)
                            activeCallNumber = ""
                        }
                        layoutInCall.visibility = View.GONE
                        currentCall = null
                        showCallQualitySummary()
                    }
                    Call.State.Error -> {
                        stopCallTimer()
                        enableProximitySensor(false)
                        setCallButtonState(false)
                        isRecording = false
                        btnRecord.text = "⏺ Record"
                        btnRecord.setBackgroundColor(0xFF1E293B.toInt())
                        if (activeCallNumber.isNotEmpty()) {
                            saveCallToHistory(activeCallNumber, "Failed", "00:00")
                            activeCallNumber = ""
                        }
                        layoutInCall.visibility = View.GONE
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
        requestAppPermissions()
        initLinphoneCore()
        setupDialpad()
        setupListeners()
        registerNetworkMonitor()
        updateNetworkStatusAndFetchIsp()
        loadSavedAccountsAndAutoLogin()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvLivePing = findViewById(R.id.tvLivePing)
        tvNetworkType = findViewById(R.id.tvNetworkType)
        tvPublicIp = findViewById(R.id.tvPublicIp)
        tvIspName = findViewById(R.id.tvIspName)
        btnRefreshNet = findViewById(R.id.btnRefreshNet)
        btnNavKeypad = findViewById(R.id.btnNavKeypad)
        btnRecentCalls = findViewById(R.id.btnRecentCalls)
        btnToggleAccount = findViewById(R.id.btnToggleAccount)
        layoutLoginCard = findViewById(R.id.layoutLoginCard)
        btnSavedAccountsList = findViewById(R.id.btnSavedAccountsList)
        etAccountLabel = findViewById(R.id.etAccountLabel)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        etDomain = findViewById(R.id.etDomain)
        spinnerTransport = findViewById(R.id.spinnerTransport)
        btnLogin = findViewById(R.id.btnLogin)
        etDialNumber = findViewById(R.id.etDialNumber)
        btnBackspace = findViewById(R.id.btnBackspace)
        btnCallToggle = findViewById(R.id.btnCallToggle)
        btnContacts = findViewById(R.id.btnContacts)
        btnRecordingsList = findViewById(R.id.btnRecordingsList)
        layoutInCall = findViewById(R.id.layoutInCall)
        tvCallState = findViewById(R.id.tvCallState)
        tvCallTimer = findViewById(R.id.tvCallTimer)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnRecord = findViewById(R.id.btnRecord)

        val transports = arrayOf("UDP", "TCP", "TLS")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, transports)
        spinnerTransport.adapter = adapter
    }

    // --- Network Type (Wi-Fi / Mobile Data) & Live IP + ISP Detection ---
    private fun registerNetworkMonitor() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNetworkTypeLabel(caps: NetworkCapabilities?) {
        if (caps == null) {
            tvNetworkType.text = "⚠️ Offline"
            return
        }
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                tvNetworkType.text = "📶 Wi-Fi"
                tvNetworkType.setTextColor(0xFF00F5D4.toInt())
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                tvNetworkType.text = "📡 Mobile Data"
                tvNetworkType.setTextColor(0xFFFFD166.toInt())
            }
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                tvNetworkType.text = "🔌 Ethernet"
                tvNetworkType.setTextColor(0xFF00BBF9.toInt())
            }
            else -> {
                tvNetworkType.text = "🌐 Connected"
                tvNetworkType.setTextColor(0xFFE0AAFF.toInt())
            }
        }
    }

    private fun updateNetworkStatusAndFetchIsp() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNet = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(activeNet)
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
                val url = URL("http://ip-api.com/json/?fields=status,query,isp,org")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                if (json.optString("status") == "success") {
                    ipResult = json.optString("query", "Unknown")
                    val isp = json.optString("isp", "")
                    val org = json.optString("org", "")
                    ispResult = if (isp.isNotEmpty()) isp else org
                }
            } catch (e: Exception) {
                // Fallback to ipwhois.app
                try {
                    val url2 = URL("https://ipwho.is/")
                    val conn2 = url2.openConnection() as HttpURLConnection
                    conn2.connectTimeout = 4000
                    conn2.readTimeout = 4000
                    val response2 = conn2.inputStream.bufferedReader().use { it.readText() }
                    val json2 = JSONObject(response2)
                    if (json2.optBoolean("success", false)) {
                        ipResult = json2.optString("ip", "Unknown")
                        val connObj = json2.optJSONObject("connection")
                        ispResult = connObj?.optString("isp", "Unknown ISP") ?: "Unknown ISP"
                    }
                } catch (_: Exception) {}
            }

            runOnUiThread {
                tvPublicIp.text = "🌐 IP: $ipResult"
                tvIspName.text = "ISP: $ispResult"
            }
        }
    }

    // --- Multi-Account Management (Add, Switch, Delete) ---
    private fun getSavedAccounts(): JSONArray {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val raw = prefs.getString("multi_accounts", "[]") ?: "[]"
        return JSONArray(raw)
    }

    private fun saveAccountsList(array: JSONArray) {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("multi_accounts", array.toString()).apply()
        btnSavedAccountsList.text = "📋 Accounts (${array.length()})"
    }

    private fun addOrUpdateAccount(label: String, user: String, pass: String, domain: String, transport: String) {
        val accounts = getSavedAccounts()
        val updated = JSONArray()
        var replaced = false

        for (i in 0 until accounts.length()) {
            val obj = accounts.getJSONObject(i)
            if (obj.getString("username") == user && obj.getString("domain") == domain) {
                val newObj = JSONObject().apply {
                    put("label", label)
                    put("username", user)
                    put("password", pass)
                    put("domain", domain)
                    put("transport", transport)
                }
                updated.put(newObj)
                replaced = true
            } else {
                updated.put(obj)
            }
        }

        if (!replaced) {
            val newObj = JSONObject().apply {
                put("label", label)
                put("username", user)
                put("password", pass)
                put("domain", domain)
                put("transport", transport)
            }
            updated.put(newObj)
        }

        saveAccountsList(updated)
        setActiveAccountIndex(updated.length() - 1)
    }

    private fun setActiveAccountIndex(index: Int) {
        getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
            .edit().putInt("active_account_idx", index).apply()
    }

    private fun loadSavedAccountsAndAutoLogin() {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val accounts = getSavedAccounts()

        // Migrate legacy single account if multi_accounts is empty
        if (accounts.length() == 0) {
            val oldUser = prefs.getString("username", "") ?: ""
            val oldPass = prefs.getString("password", "") ?: ""
            val oldDomain = prefs.getString("domain", "") ?: ""
            val oldTransport = prefs.getString("transport", "UDP") ?: "UDP"
            if (oldUser.isNotEmpty() && oldPass.isNotEmpty() && oldDomain.isNotEmpty()) {
                addOrUpdateAccount("Primary", oldUser, oldPass, oldDomain, oldTransport)
            }
        }

        val finalAccounts = getSavedAccounts()
        btnSavedAccountsList.text = "📋 Accounts (${finalAccounts.length()})"

        if (finalAccounts.length() > 0) {
            var idx = prefs.getInt("active_account_idx", 0)
            if (idx < 0 || idx >= finalAccounts.length()) idx = 0
            activateAccountFromJson(finalAccounts.getJSONObject(idx), idx)
        } else {
            layoutLoginCard.visibility = View.VISIBLE
        }
    }

    private fun activateAccountFromJson(acc: JSONObject, index: Int) {
        val label = acc.optString("label", "Account ${index + 1}")
        val user = acc.getString("username")
        val pass = acc.getString("password")
        val domain = acc.getString("domain")
        val transport = acc.optString("transport", "UDP")

        activeLabel = label
        activeDomain = domain
        setActiveAccountIndex(index)

        etAccountLabel.setText(label)
        etUsername.setText(user)
        etPassword.setText(pass)
        etDomain.setText(domain)
        spinnerTransport.setSelection(
            when (transport) {
                "TCP" -> 1
                "TLS" -> 2
                else -> 0
            }
        )

        registerSipAccount(user, pass, domain, transport)
    }

    private fun showMultiAccountSwitcherDialog() {
        val accounts = getSavedAccounts()
        if (accounts.length() == 0) {
            layoutLoginCard.visibility = View.VISIBLE
            Toast.makeText(this, "No saved accounts. Add one below!", Toast.LENGTH_SHORT).show()
            return
        }

        val activeIdx = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE).getInt("active_account_idx", 0)
        val items = Array(accounts.length()) { i ->
            val obj = accounts.getJSONObject(i)
            val lbl = obj.optString("label", "Account")
            val usr = obj.getString("username")
            val dom = obj.getString("domain")
            val tr = obj.optString("transport", "UDP")
            val check = if (i == activeIdx) "✅ " else "⚪ "
            "$check$lbl — $usr@$dom ($tr)"
        }

        AlertDialog.Builder(this)
            .setTitle("🤖 Switch SIP Account")
            .setItems(items) { _, which ->
                activateAccountFromJson(accounts.getJSONObject(which), which)
                Toast.makeText(this, "Switched to ${accounts.getJSONObject(which).optString("label")}", Toast.LENGTH_SHORT).show()
            }
            .setPositiveButton("➕ Add New") { _, _ ->
                etAccountLabel.setText("")
                etUsername.setText("")
                etPassword.setText("")
                layoutLoginCard.visibility = View.VISIBLE
            }
            .setNeutralButton("🗑️ Delete Current") { _, _ ->
                if (activeIdx in 0 until accounts.length()) {
                    val newArr = JSONArray()
                    for (i in 0 until accounts.length()) {
                        if (i != activeIdx) newArr.put(accounts.getJSONObject(i))
                    }
                    saveAccountsList(newArr)
                    if (newArr.length() > 0) {
                        activateAccountFromJson(newArr.getJSONObject(0), 0)
                    } else {
                        core.clearAccounts()
                        core.clearAllAuthInfo()
                        activeDomain = ""
                        tvStatus.text = "● No Account (Tap to Add)"
                        layoutLoginCard.visibility = View.VISIBLE
                    }
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // --- Recordings & Media ---
    private fun getRecordingsDirectory(): File {
        val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val dir = File(publicMusic, "SIP_Recordings")
        if (!dir.exists()) {
            val created = dir.mkdirs()
            if (!created) {
                val fallback = File(getExternalFilesDir(null), "Recordings")
                if (!fallback.exists()) fallback.mkdirs()
                return fallback
            }
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

    private fun startLivePing() {
        if (!isPinging) {
            isPinging = true
            pingHandler.post(pingRunnable)
        }
    }

    private fun stopLivePing() {
        isPinging = false
        pingHandler.removeCallbacks(pingRunnable)
        tvLivePing.text = "⚡ -- ms"
    }

    private fun measureServerPing() {
        val rawDomain = activeDomain.ifEmpty { etDomain.text.toString().trim() }
        if (rawDomain.isEmpty()) return

        val parts = rawDomain.split(":")
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
            } catch (e: Exception) {
                try {
                    val start = SystemClock.elapsedRealtime()
                    val reachable = java.net.InetAddress.getByName(host).isReachable(2000)
                    if (reachable) {
                        pingMs = SystemClock.elapsedRealtime() - start
                    }
                } catch (_: Exception) {}
            }

            runOnUiThread {
                if (pingMs >= 0) {
                    val color = when {
                        pingMs < 80 -> 0xFF00F5D4.toInt()
                        pingMs < 180 -> 0xFFFFD166.toInt()
                        else -> 0xFFFF4D6D.toInt()
                    }
                    tvLivePing.text = "⚡ ${pingMs} ms"
                    tvLivePing.setTextColor(color)
                } else if (currentCall != null && lastDelay > 0f) {
                    tvLivePing.text = "⚡ ${lastDelay.toInt()} ms"
                    tvLivePing.setTextColor(0xFF00F5D4.toInt())
                } else {
                    tvLivePing.text = "⚡ Online"
                    tvLivePing.setTextColor(0xFF90E0EF.toInt())
                }
            }
        }
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
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "BasicSipDialer::ProximityLock"
            )
        }
    }

    private fun enableProximitySensor(enable: Boolean) {
        try {
            if (enable) {
                if (proximityWakeLock?.isHeld == false) {
                    proximityWakeLock?.acquire(60 * 60 * 1000L)
                }
            } else {
                if (proximityWakeLock?.isHeld == true) {
                    proximityWakeLock?.release()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            Triple(R.id.btn1, "1", ""),
            Triple(R.id.btn2, "2", "ABC"),
            Triple(R.id.btn3, "3", "DEF"),
            Triple(R.id.btn4, "4", "GHI"),
            Triple(R.id.btn5, "5", "JKL"),
            Triple(R.id.btn6, "6", "MNO"),
            Triple(R.id.btn7, "7", "PQRS"),
            Triple(R.id.btn8, "8", "TUV"),
            Triple(R.id.btn9, "9", "WXYZ"),
            Triple(R.id.btnStar, "*", ""),
            Triple(R.id.btn0, "0", "+"),
            Triple(R.id.btnHash, "#", "")
        )

        for ((id, digit, letters) in dialConfig) {
            val btn = findViewById<Button>(id)
            btn.text = formatKeyLabel(digit, letters)
            btn.setOnClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                etDialNumber.append(digit)
                currentCall?.sendDtmf(digit[0])
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

    private fun saveCallToHistory(number: String, type: String, duration: String) {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("call_history", "[]") ?: "[]"
        val jsonArray = JSONArray(historyJson)

        val timeStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
        val entry = JSONObject().apply {
            put("number", number)
            put("type", type)
            put("duration", duration)
            put("time", timeStr)
        }

        val updatedArray = JSONArray()
        updatedArray.put(entry)
        for (i in 0 until minOf(jsonArray.length(), 29)) {
            updatedArray.put(jsonArray.getJSONObject(i))
        }

        prefs.edit().putString("call_history", updatedArray.toString()).apply()
    }

    private fun showRecentCallsDialog() {
        val prefs = getSharedPreferences("SipPrefs", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("call_history", "[]") ?: "[]"
        val jsonArray = JSONArray(historyJson)

        if (jsonArray.length() == 0) {
            Toast.makeText(this, "No recent calls yet!", Toast.LENGTH_SHORT).show()
            return
        }

        val displayItems = Array(jsonArray.length()) { i ->
            val obj = jsonArray.getJSONObject(i)
            val icon = when (obj.getString("type")) {
                "Outgoing" -> "📤"
                "Incoming" -> "📥"
                "Missed" -> "❌"
                else -> "⚠️"
            }
            val num = obj.getString("number")
            val dur = obj.getString("duration")
            val time = obj.getString("time")
            "$icon  $num  (⏱ $dur)\n      🕒 $time"
        }

        val numbers = Array(jsonArray.length()) { i ->
            jsonArray.getJSONObject(i).getString("number")
        }

        AlertDialog.Builder(this)
            .setTitle("🕒 Recent Calls (Tap to Call)")
            .setItems(displayItems) { _, which ->
                val selectedNumber = numbers[which]
                etDialNumber.setText(selectedNumber)
                makeCall(selectedNumber)
            }
            .setNeutralButton("Clear History") { _, _ ->
                prefs.edit().remove("call_history").apply()
                Toast.makeText(this, "Call history cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showRecordingsDialog() {
        val dir = getRecordingsDirectory()
        val files = dir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) && file.length() > 44 }
            ?.sortedByDescending { it.lastModified() }

        if (files.isNullOrEmpty()) {
            Toast.makeText(this, "No recordings found in Music/SIP_Recordings", Toast.LENGTH_SHORT).show()
            return
        }

        val names = Array(files.size) { i ->
            val kb = files[i].length() / 1024
            "🎵 ${files[i].name} (${kb} KB)"
        }

        AlertDialog.Builder(this)
            .setTitle("🎧 Saved Call Recordings (Tap to Play)")
            .setItems(names) { _, which ->
                playRecordingFile(files[which])
            }
            .setNegativeButton("Close") { _, _ ->
                stopMediaPlayer()
            }
            .show()
    }

    private fun playRecordingFile(file: File) {
        try {
            stopMediaPlayer()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
            Toast.makeText(this, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun requestAppPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS
        )
        permissionLauncher.launch(needed)
    }

    private fun initLinphoneCore() {
        val factory = Factory.instance()
        factory.setDebugMode(false, "BasicSipDialer")
        core = factory.createCore(null, null, this)
        core.addListener(coreListener)
        core.start()
    }

    private fun setupListeners() {
        // Tapping top status opens multi-account switcher directly
        tvStatus.setOnClickListener {
            showMultiAccountSwitcherDialog()
        }

        btnSavedAccountsList.setOnClickListener {
            showMultiAccountSwitcherDialog()
        }

        btnRefreshNet.setOnClickListener {
            updateNetworkStatusAndFetchIsp()
            measureServerPing()
            Toast.makeText(this, "Refreshing Network & IP...", Toast.LENGTH_SHORT).show()
        }

        btnNavKeypad.setOnClickListener {
            layoutLoginCard.visibility = View.GONE
        }

        btnRecentCalls.setOnClickListener {
            showRecentCallsDialog()
        }

        btnRecordingsList.setOnClickListener {
            showRecordingsDialog()
        }

        btnToggleAccount.setOnClickListener {
            layoutLoginCard.visibility =
                if (layoutLoginCard.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        btnLogin.setOnClickListener {
            val labelInput = etAccountLabel.text.toString().trim()
            val user = etUsername.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            val domain = etDomain.text.toString().trim()
            val transportStr = spinnerTransport.selectedItem.toString()

            if (user.isEmpty() || pass.isEmpty() || domain.isEmpty()) {
                Toast.makeText(this, "Please fill Username, Password & Domain", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val finalLabel = labelInput.ifEmpty { "Account ${getSavedAccounts().length() + 1}" }
            activeLabel = finalLabel
            activeDomain = domain

            addOrUpdateAccount(finalLabel, user, pass, domain, transportStr)
            registerSipAccount(user, pass, domain, transportStr)
        }

        btnContacts.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                pickContactLauncher.launch(intent)
            } else {
                requestAppPermissions()
            }
        }

        btnCallToggle.setOnClickListener {
            if (currentCall != null) {
                currentCall?.terminate()
            } else {
                val number = etDialNumber.text.toString().trim()
                if (number.isEmpty()) {
                    Toast.makeText(this, "Enter a number to call", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                makeCall(number)
            }
        }

        btnMute.setOnClickListener {
            core.isMicEnabled = !core.isMicEnabled
            btnMute.text = if (core.isMicEnabled) "🔇 Mute" else "🎙️ Unmute"
        }

        btnSpeaker.setOnClickListener {
            val currentDevice = core.outputAudioDevice
            val devices = core.audioDevices
            val speaker = devices.find { it.type == AudioDevice.Type.Speaker }
            val earpiece = devices.find { it.type == AudioDevice.Type.Earpiece }

            if (currentDevice?.type == AudioDevice.Type.Speaker && earpiece != null) {
                core.outputAudioDevice = earpiece
                btnSpeaker.text = "🔊 Speaker"
                enableProximitySensor(true)
            } else if (speaker != null) {
                core.outputAudioDevice = speaker
                btnSpeaker.text = "📱 Earpiece"
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
                    btnRecord.text = "⏹ Stop"
                    btnRecord.setBackgroundColor(0xFFC9184A.toInt())
                    lastSavedRecordFileName = File(currentRecordFilePath ?: "").name
                    Toast.makeText(this, "🔴 Recording started...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                try {
                    call.stopRecording()
                } catch (_: Exception) {}
                isRecording = false
                btnRecord.text = "⏺ Record"
                btnRecord.setBackgroundColor(0xFF1E293B.toInt())
                Toast.makeText(this, "✅ Saved in Music/SIP_Recordings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun registerSipAccount(username: String, password: String, domain: String, transportStr: String) {
        core.clearAccounts()
        core.clearAllAuthInfo()

        val transport = when (transportStr) {
            "TCP" -> TransportType.Tcp
            "TLS" -> TransportType.Tls
            else -> TransportType.Udp
        }

        val authInfo = Factory.instance().createAuthInfo(username, null, password, null, null, domain, null)
        core.addAuthInfo(authInfo)

        val accountParams = core.createAccountParams()
        val identity = Factory.instance().createAddress("sip:$username@$domain")
        accountParams.identityAddress = identity

        val serverAddress = Factory.instance().createAddress("sip:$domain")
        serverAddress?.transport = transport
        accountParams.serverAddress = serverAddress
        accountParams.isRegisterEnabled = true

        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account
    }

    private fun makeCall(destination: String) {
        val domain = activeDomain.ifEmpty { etDomain.text.toString().trim() }
        if (domain.isEmpty()) {
            Toast.makeText(this, "Please select or add a SIP account first!", Toast.LENGTH_SHORT).show()
            return
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

    private fun showIncomingCallDialog(call: Call) {
        val caller = call.remoteAddress.username ?: call.remoteAddress.asStringUriOnly()
        AlertDialog.Builder(this)
            .setTitle("📲 Incoming SIP Call")
            .setMessage("Caller: $caller")
            .setPositiveButton("✅ Answer") { _, _ ->
                val params = core.createCallParams(call)
                params?.isAudioEnabled = true
                params?.recordFile = prepareRecordFilePath("InCall")
                call.acceptWithParams(params)
            }
            .setNegativeButton("❌ Decline") { _, _ ->
                call.decline(Reason.Declined)
            }
            .setCancelable(false)
            .show()
    }

    private fun captureCallMetrics(call: Call) {
        lastQualityRating = call.currentQuality
        val stats = call.audioStats
        if (stats != null) {
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
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {}
        stopCallTimer()
        stopLivePing()
        stopMediaPlayer()
        enableProximitySensor(false)
        core.removeListener(coreListener)
        core.stop()
    }
}
