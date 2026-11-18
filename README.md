cat << 'EOF' > README.md
<div align="center">

  <img src="data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' width='110' height='110' viewBox='0 0 24 24'><circle cx='12' cy='12' r='10' fill='%2300F5D4'/><path fill='%23090D16' d='M16.5,15.5c-1.1,0 -2.2,-0.2 -3.2,-0.5 -0.3,-0.1 -0.7,0 -0.9,0.2l-1.4,1.4c-2.5,-1.3 -4.6,-3.4 -5.9,-5.9l1.4,-1.4c0.2,-0.2 0.3,-0.6 0.2,-0.9 -0.3,-1 -0.5,-2.1 -0.5,-3.2 0,-0.5 -0.4,-0.9 -0.9,-0.9H6.5c-0.5,0 -0.9,0.4 -0.9,0.9 0,7.2 5.8,13 13,13 0.5,0 0.9,-0.4 0.9,-0.9v-2.9c0,-0.5 -0.4,-0.9 -0.9,-0.9z'/></svg>" alt="AI SIP Dialer Logo" width="105" height="105" />

  <h1>🤖 AI SIP Dialer (v1.5)</h1>

  <p>
    <strong>A Next-Gen Android VoIP & Multi-SIP Client featuring 24/7 Background Service, 5 Cyber-Glass UI Themes, Live Network/IP/ISP Telemetry, Custom Ringtones & JSON Backup/Restore.</strong>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Version-1.5-00F5D4?style=for-the-badge" alt="Version 1.5" />
    <img src="https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Engine-Linphone%20SDK%205.2-FF6F00?style=for-the-badge" alt="Linphone SDK" />
  </p>

</div>

---

## 🇬🇧 English Documentation

### 🚀 What's New in v1.5

- 🇧🇩 **Animated Waving Bangladesh Flag & Live Clock:** Top HUD features a continuously waving Bangladesh flag alongside a real-time digital clock (with seconds) and date.
- 🛡️ **24/7 Background Foreground Service:** Persistent background service keeps all SIP accounts registered and ready to receive incoming calls even when the app is minimized or the screen is locked.
- 📡 **Simultaneous Multi-SIP & Per-Line Live Ping:** Register multiple SIP accounts at once, monitor live ping (`⚡ XX ms`) for every line simultaneously, and assign **custom ringtones** per SIP account.
- 🎨 **5 Selectable AI Glass UI Themes:** Switch between *Luminous Cyber-Glass*, *Emerald Matrix AI*, *Sunset Neon Cyber*, *Royal Obsidian Gold*, and *Arctic Aurora Glass* from Settings.
- 📞 **Dedicated Active Call Screen & Animated Incoming Overlay:**
  - Dialpad smoothly transitions into a full active call view with **Mute**, **Hold/Resume**, **Speaker**, **Record**, **In-Call DTMF**, live **Ping/MOS**, and a Google Dialer-style red **End Call** button.
  - Pulsing incoming call screen clearly shows which SIP line is receiving the call.
- 📇 **In-App Phonebook (Auto 11-Digit Format):** Search and dial contacts directly inside the app—automatically strips `+88` and dashes (`-`) into clean 11-digit numbers.
- 📊 **Interactive Call Analytics & Quality Details:** Visual bar chart, filter tabs (**All**, **Missed**, **Dialed**, **Received**), and tap-to-view detailed **MOS**, **Ping**, **Jitter**, and **Packet Loss** history with one-tap redial.
- ⏺️ **Auto Call Recording & JSON Backup/Restore:** Toggle automatic `.wav` call recording (`Music/SIP_Recordings/`) and export/import all accounts, ringtones, and call logs via `.json` files.

---

## 🇧🇩 বাংলা ডকুমেন্টেশন (Bengali)

### 🚀 সংস্করণ ১.৫ (v1.5)-এর নতুন বৈশিষ্ট্যসমূহ

- 🇧🇩 **বাতাসে উড়ন্ত বাংলাদেশের পতাকা ও লাইভ ঘড়ি:** অ্যাপের একদম উপরে বাতাসে উড়ন্ত বাংলাদেশের পতাকা এবং সেকেন্ডসহ লাইভ সময় ও তারিখ দেখাবে।
- 🛡️ **২৪/৭ ব্যাকগ্রাউন্ড সার্ভিস:** অ্যাপ মিনিমাইজ বা ফোনের স্ক্রিন লক থাকলেও ফোরগ্রাউন্ড সার্ভিসের মাধ্যমে সব SIP অ্যাকাউন্ট সক্রিয় থাকবে এবং ইনকামিং কল আসবে।
- 📡 **একসাথে মাল্টি-SIP, লাইভ পিং ও আলাদা রিংটোন:** যত খুশি SIP অ্যাকাউন্ট একসাথে অ্যাক্টিভ রাখুন, সবার লাইভ পিং (`⚡ XX ms`) একসাথে দেখুন এবং প্রতিটি SIP লাইনের জন্য আলাদা রিংটোন সেট করুন।
- 🎨 **৫টি আকর্ষণীয় UI থিম:** সেটিংস থেকে *Luminous Cyber-Glass*, *Emerald Matrix AI*, *Sunset Neon Cyber*, *Royal Obsidian Gold* এবং *Arctic Aurora Glass*—এই ৫টি থিমের যেকোনোটি ব্যবহার করতে পারবেন।
- 📞 **ডেডিকেটেড ইন-কল স্ক্রিন ও অ্যানিমেটেড ইনকামিং ওভারলে:**
  - কল রিসিভ হলে ডায়ালপ্যাড সরে গিয়ে কলারের নাম, নম্বর, **Mute**, **Hold/Resume**, **Speaker**, **Record**, **DTMF** এবং গুগল ডায়ালারের মতো লাল **End Call** বাটন দেখাবে।
  - কল এলে অ্যানিমেটেড স্ক্রিনে দেখাবে কোন SIP অ্যাকাউন্টে কলটি এসেছে।
- 📇 **ইন-অ্যাপ কন্টাক্ট লিস্ট (অটো ১১-ডিজিট):** অ্যাপের ভেতর থেকেই কন্টাক্ট সার্চ ও কল করা যাবে এবং নম্বর থেকে `+88` ও হাইফেন (`-`) বাদ দিয়ে স্বয়ংক্রিয়ভাবে ১১ ডিজিটে ফরম্যাট করবে।
- 📊 **কল হিস্ট্রি চার্ট ও বিস্তারিত কোয়ালিটি রিপোর্ট:** **All**, **Missed**, **Dialed** ও **Received** ট্যাবের পাশাপাশি যেকোনো কলের ওপর ট্যাপ করলে সেটির **MOS স্কোর**, **Ping**, **Jitter**, **Packet Loss** ও আবার কল করার বাটন দেখাবে।
- ⏺️ **অটো কল রেকর্ডিং ও ব্যাকআপ/রিস্টোর (Export/Import):** সেটিংস থেকে অটো কল রেকর্ডিং অন/অফ করা যাবে এবং সব অ্যাকাউন্ট ও কল হিস্ট্রি `.json` ফাইলে ব্যাকআপ ও রিস্টোর করা যাবে।

---

## 🛠️ Tech Stack & Credits

| Component | Details |
| :--- | :--- |
| **Language** | Kotlin (JVM 17) |
| **VoIP Engine** | `org.linphone:linphone-sdk-android:5.2.98` |
| **Android SDK** | Min API 24 (Android 7.0) — Target API 34 (Android 14+) |
| **Cloud Build** | GitHub Actions (`gradle assembleDebug`) |

<div align="center">
  <p>
    🇧🇩━━━━━━━━━━━━━━━━━━━━🇧🇩<br/>
    <strong>🤖 Developed with Gemini AI</strong><br/>
    <strong>💡 Idea by Akter Hossain</strong><br/>
    <sub>App v1.5</sub>
  </p>
</div>
EOF

git add README.md && git commit -m "Update bilingual README.md for v1.5 release" && git push origin main
