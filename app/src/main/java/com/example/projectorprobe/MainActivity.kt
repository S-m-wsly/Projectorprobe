package com.example.projectorprobe

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.WallpaperManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.media.MediaDrm
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.StatFs
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var out: TextView
    private lateinit var list: LinearLayout
    private val log = StringBuilder()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        out = TextView(this)
        out.textSize = 14f
        out.setPadding(0, 16, 0, 16)
        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(40, 30, 40, 30)
        val root = ScrollView(this)
        root.addView(list)
        setContentView(root)
        home()
        info()
    }

    private fun note(s: String) {
        log.append(s).append('\n')
        out.text = log
    }

    private fun btn(t: String, lp: (() -> Unit)? = null, a: () -> Unit) {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.setOnClickListener {
            try {
                a()
            } catch (e: Throwable) {
                note("ERROR $t: ${e.javaClass.simpleName} ${e.message}")
            }
        }
        if (lp != null) b.setOnLongClickListener { lp(); true }
        list.addView(b)
    }

    private fun screen(title: String, body: () -> Unit) {
        list.removeAllViews()
        val t = TextView(this)
        t.text = title
        t.textSize = 22f
        list.addView(t)
        (out.parent as? ViewGroup)?.removeView(out)
        list.addView(out)
        body()
    }

    private fun home(): Unit = screen("Projector Probe") {
        btn("Show device info") { info() }
        btn("Test: video protection (Widevine) and Netflix") { widevine() }
        btn("Test: Wi-Fi off, then back on") { wifiToggle() }
        btn("Test: Wi-Fi scan and current network") { wifiScan() }
        btn("Test: Wi-Fi pop-up panel") {
            note("Tried Wi-Fi pop-up panel")
            startActivity(Intent("android.settings.panel.action.INTERNET_CONNECTIVITY"))
        }
        btn("Test: volume down, then restore") { volume() }
        btn("Test: brightness permission") {
            note("Can change brightness: ${Settings.System.canWrite(this)}")
        }
        btn("Open brightness permission screen") {
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        }
        btn("Test: wallpaper allowed?") {
            val wm = WallpaperManager.getInstance(this)
            note("Wallpaper supported=${wm.isWallpaperSupported} canSet=${wm.isSetWallpaperAllowed}")
        }
        btn("List TV inputs (HDMI / USB)") { inputs() }
        btn("Test: Watch Next (exact show you were watching)") { watchNext() }
        btn("Open usage access screen") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        btn("Test: recently used apps") { recent() }
        btn("Open notification access screen") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        btn("Test: what is playing now") { nowPlaying() }
        btn("Explore projector screens") { packages(false) }
        btn("Clear report") {
            log.setLength(0)
            out.text = ""
        }
    }

    private fun info() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        note("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        note("RAM total ${mi.totalMem / 1048576} MB, free ${mi.availMem / 1048576} MB, low-RAM device: ${am.isLowRamDevice}")
        val st = StatFs(Environment.getDataDirectory().path)
        note("Storage free ${st.availableBytes / 1048576} MB of ${st.totalBytes / 1048576} MB")
        val dm = resources.displayMetrics
        note("Screen ${dm.widthPixels}x${dm.heightPixels}, ${dm.densityDpi} dpi")
        val homes = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
        ).map { it.activityInfo.packageName }
        note("Home apps: ${homes.joinToString()}")
        note("TV mode: ${packageManager.hasSystemFeature("android.software.leanback")}, touchscreen: ${packageManager.hasSystemFeature("android.hardware.touchscreen")}")
    }

    @Suppress("DEPRECATION")
    private fun wifiToggle() {
        val w = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val was = w.isWifiEnabled
        val ok = w.setWifiEnabled(!was)
        note("Wi-Fi: asked to turn ${if (was) "OFF" else "ON"}, call returned $ok")
        val h = Handler(mainLooper)
        h.postDelayed({
            note("Wi-Fi state 2.5s later: ${if (w.isWifiEnabled) "ON" else "OFF"} (changed: ${w.isWifiEnabled != was})")
            w.setWifiEnabled(was)
            note("Wi-Fi restored to ${if (was) "ON" else "OFF"}")
        }, 2500)
    }

    @Suppress("DEPRECATION")
    private fun wifiScan() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            note("Allow location if asked, then press this test again")
            return
        }
        val w = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ci = w.connectionInfo
        note("Connected network: ${ci?.ssid}, signal ${ci?.rssi} dBm")
        note("Scan started: ${w.startScan()}")
        Handler(mainLooper).postDelayed({
            try {
                val r = w.scanResults
                note("Networks found: ${r.size}")
                r.take(5).forEach { note("  ${it.SSID} ${it.level} dBm") }
            } catch (e: Throwable) {
                note("ERROR reading scan: ${e.javaClass.simpleName}")
            }
        }, 3500)
    }

    private fun watchNext() {
        if (Build.VERSION.SDK_INT < 26) {
            note("Watch Next needs Android 8 or newer")
            return
        }
        val c = contentResolver.query(
            TvContract.WatchNextPrograms.CONTENT_URI,
            arrayOf(TvContract.WatchNextPrograms.COLUMN_TITLE, TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME),
            null, null, null
        )
        if (c == null) {
            note("Watch Next: this projector has no such data source")
            return
        }
        c.use {
            note("Watch Next entries readable: ${it.count}")
            var n = 0
            while (it.moveToNext() && n < 8) {
                note("  ${it.getString(1)}: ${it.getString(0)}")
                n++
            }
        }
    }

    private fun recent() {
        val um = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val st = um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 2 * 86400000L, now)
        if (st.isNullOrEmpty()) {
            note("Recently used apps: no data. Open the usage access screen, allow Projector Probe, then test again")
            return
        }
        note("Recently used apps (newest first):")
        st.sortedByDescending { it.lastTimeUsed }.take(6).forEach {
            note("  ${it.packageName}, ${(now - it.lastTimeUsed) / 60000} min ago")
        }
    }

    private fun nowPlaying() {
        val mm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val l = mm.getActiveSessions(ComponentName(this, MediaListener::class.java))
        note("Active media sessions: ${l.size}")
        l.forEach {
            note("  ${it.packageName}: ${it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)} (state ${it.playbackState?.state})")
        }
    }

    private fun widevine() {
        val uuid = java.util.UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
        if (!MediaDrm.isCryptoSchemeSupported(uuid)) {
            note("Widevine: not supported on this projector")
        } else {
            val d = MediaDrm(uuid)
            try {
                note("Widevine security level: ${d.getPropertyString("securityLevel")}  (L1 = HD allowed, L3 = about 480p)")
                note("Widevine version ${d.getPropertyString("version")}, vendor ${d.getPropertyString("vendor")}")
                try {
                    note("HDCP: ${d.getPropertyString("hdcpLevel")} (max ${d.getPropertyString("maxHdcpLevel")})")
                } catch (e: Throwable) {
                    note("HDCP: not reported")
                }
            } finally {
                if (Build.VERSION.SDK_INT >= 28) d.close() else d.release()
            }
        }
        for ((pkg, what) in listOf("com.netflix.mediaclient" to "phone-style", "com.netflix.ninja" to "TV-style")) {
            try {
                val i = packageManager.getPackageInfo(pkg, 0)
                note("Netflix $what app installed, version ${i.versionName}")
            } catch (e: Exception) {
                note("Netflix $what app: not installed")
            }
        }
    }

    private fun volume() {
        val a = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = AudioManager.STREAM_MUSIC
        val v = a.getStreamVolume(s)
        val max = a.getStreamMaxVolume(s)
        a.setStreamVolume(s, maxOf(0, v - 1), 0)
        val v2 = a.getStreamVolume(s)
        a.setStreamVolume(s, v, 0)
        note("Volume: was $v of $max, after change $v2, restored ${a.getStreamVolume(s)}")
    }

    private fun inputs(): Unit = screen("TV inputs") {
        btn("< Back") { home() }
        val tm = getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val l = tm.tvInputList
        note("TV inputs found: ${l.size}")
        for (i in l) {
            val id = i.id
            note("  $id type=${i.type} passthrough=${i.isPassthroughInput}")
            btn("Open input: ${id.substringAfterLast('/')}", { note("WORKS: input $id") }) {
                note("Tried input $id")
                startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id)))
            }
        }
    }

    private fun packages(all: Boolean): Unit = screen("Projector screens") {
        btn("< Back") { home() }
        btn(if (all) "Show only likely ones" else "Show all system apps") { packages(!all) }
        val keys = listOf("setting", "tv", "input", "source", "mirror", "cast", "display", "network", "wifi", "projector", "keystone", "hdmi", "screen", "picture", "sound", "launcher")
        val pm = packageManager
        val pk = pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)
            .filter { (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 }
            .filter { p -> p.activities?.any { it.exported } == true }
            .filter { q -> all || keys.any { k -> q.packageName.contains(k, true) } }
            .sortedBy { it.packageName }
        note("Packages listed: ${pk.size}")
        for (p in pk) btn(p.packageName) { activities(p.packageName, all) }
    }

    private fun activities(pkg: String, all: Boolean): Unit = screen(pkg) {
        btn("< Back") { packages(all) }
        note("Press OK to open a screen. Come back, then HOLD OK on it to mark WORKS.")
        val pm = packageManager
        val info = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
        for (a in info.activities.orEmpty().filter { it.exported }) {
            val label = a.loadLabel(pm).toString()
            btn("$label  (${a.name.substringAfterLast('.')})", { note("WORKS: $pkg/${a.name}") }) {
                note("Tried $pkg/${a.name}")
                startActivity(Intent().setClassName(pkg, a.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
