package com.phonehub.livemap

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LiveMap 控制台（独立测试 App）
 * =================================
 * 填好电脑的 IP/端口 → 点「开始共享」→ 手机进入前台服务，每 2 秒把位置 POST 给
 * 电脑端 `cs_apps/LiveMap/pc/map_server.py`（默认 5678）。
 * 然后在电脑浏览器打开 `http://127.0.0.1:5678/` 看地图。
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_LOC = 1001
        private const val PREF = "livemap"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var ipEt: EditText
    private lateinit var portEt: EditText
    private lateinit var startBtn: Button
    private lateinit var statusTv: TextView
    private lateinit var urlTv: TextView

    private val ui = Handler(Looper.getMainLooper())
    private var pendingStart = false

    private val refresh = object : Runnable {
        override fun run() {
            renderStatus()
            ui.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREF, MODE_PRIVATE)
        setContentView(buildUi())
        renderStatus()
    }

    override fun onResume() {
        super.onResume()
        ui.post(refresh)
        renderStatus()
    }

    override fun onPause() {
        super.onPause()
        ui.removeCallbacks(refresh)
    }

    // ============================== 界面 ==============================

    private fun buildUi(): View {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF151515.toInt())
            setPadding(pad, pad, pad, pad)
        }

        fun label(text: String, size: Float = 14f, color: Int = 0xFFB0B0B0.toInt()): TextView =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
            }

        root.addView(label("LiveMap · 手机实时位置 → 电脑地图", 18f, 0xFF00E676.toInt()))
        root.addView(label("独立测试 App（不会影响 PhoneHub 主程序）", 12f, 0xFF808080.toInt()))
        root.addView(space(pad))
        root.addView(label("① 先在电脑上启动地图服务：", 13f))
        root.addView(label("      python map_server.py            （默认端口 5678）", 13f, 0xFF4FC3F7.toInt()))
        root.addView(space(pad))

        root.addView(label("② 填电脑的局域网 IP 与端口：", 13f))
        ipEt = EditText(this).apply {
            setText(prefs.getString("ip", "192.168.3.9"))
            hint = "电脑 IP，如 192.168.3.9"
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF777777.toInt())
            setBackgroundColor(0xFF262626.toInt())
        }
        root.addView(ipEt)
        portEt = EditText(this).apply {
            setText(prefs.getInt("port", 5678).toString())
            hint = "端口，默认 5678"
            inputType = InputType.TYPE_CLASS_NUMBER
            setTextColor(0xFFE0E0E0.toInt())
            setHintTextColor(0xFF777777.toInt())
            setBackgroundColor(0xFF262626.toInt())
        }
        root.addView(portEt)
        root.addView(space(pad))

        startBtn = Button(this).apply {
            text = "开始共享位置"
            setOnClickListener { toggle() }
        }
        root.addView(startBtn)

        root.addView(space(pad))
        urlTv = label("", 12f, 0xFF4FC3F7.toInt())
        root.addView(urlTv)

        root.addView(space(pad))
        root.addView(label("状态", 13f, 0xFF9E9E9E.toInt()))
        statusTv = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFD0D0D0.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundColor(0xFF1E1E1E.toInt())
            setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            gravity = Gravity.START
        }
        root.addView(
            statusTv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(space(pad))
        root.addView(
            label(
                "说明：位置只发给上面填的电脑，不经任何服务器。\n" +
                        "断网时会先缓存，恢复后按顺序自动补发，不丢轨迹。",
                11f, 0xFF808080.toInt()
            )
        )

        val scroll = ScrollView(this)
        scroll.addView(root)
        return scroll
    }

    private fun space(px: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px / 2)
    }

    // ============================== 起停 ==============================

    private fun toggle() {
        if (LiveLocationPusher.running) {
            stopSharing()
        } else {
            startSharing()
        }
    }

    private fun startSharing() {
        val ip = ipEt.text.toString().trim()
        val port = portEt.text.toString().trim().toIntOrNull() ?: 5678
        if (ip.isBlank()) {
            Toast.makeText(this, "请先填电脑 IP", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.edit().putString("ip", ip).putInt("port", port).apply()

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingStart = true
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), REQ_LOC
            )
            return
        }
        doStart(ip, port)
    }

    private fun doStart(ip: String, port: Int) {
        val it = Intent(this, LiveLocationPusher::class.java)
            .putExtra(LiveLocationPusher.EXTRA_IP, ip)
            .putExtra(LiveLocationPusher.EXTRA_PORT, port)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(it)
        else startService(it)
        Toast.makeText(this, "已开始共享 → $ip:$port", Toast.LENGTH_SHORT).show()
        ui.postDelayed({ renderStatus() }, 400)
    }

    private fun stopSharing() {
        stopService(Intent(this, LiveLocationPusher::class.java))
        Toast.makeText(this, "已停止共享", Toast.LENGTH_SHORT).show()
        ui.postDelayed({ renderStatus() }, 400)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted && pendingStart) {
                pendingStart = false
                val ip = ipEt.text.toString().trim()
                val port = portEt.text.toString().trim().toIntOrNull() ?: 5678
                doStart(ip, port)
            } else if (!granted) {
                Toast.makeText(this, "没有定位权限，无法共享位置", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ============================== 状态显示 ==============================

    private fun renderStatus() {
        startBtn.text = if (LiveLocationPusher.running) "停止共享" else "开始共享位置"

        val ip = ipEt.text.toString().trim()
        val port = portEt.text.toString().trim().toIntOrNull() ?: 5678
        urlTv.text = "电脑上打开地图： http://$ip:$port/"

        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append(if (LiveLocationPusher.running) "运行中" else "已停止").append('\n')
        sb.append("目标   ").append(
            LiveLocationPusher.target.ifBlank { "$ip:$port" }
        ).append('\n')
        sb.append("成功   ").append(LiveLocationPusher.pushedOk).append(" 次\n")
        sb.append("失败   ").append(LiveLocationPusher.pushedFail).append(" 次\n")
        sb.append("待补发 ").append(LiveLocationPusher.pendingCount).append(" 点\n")
        if (LiveLocationPusher.lastFixMs > 0) {
            sb.append("定位   ").append("%.6f, %.6f".format(
                LiveLocationPusher.lastLat, LiveLocationPusher.lastLon)).append('\n')
            sb.append("速度   ").append("%.1f km/h".format(LiveLocationPusher.lastSpeedKmh)).append('\n')
            sb.append("取点   ").append(timeFmt.format(Date(LiveLocationPusher.lastFixMs))).append('\n')
        } else {
            sb.append("定位   等待第一次定位…\n")
        }
        if (LiveLocationPusher.lastPushMs > 0) {
            sb.append("推送   ").append(timeFmt.format(Date(LiveLocationPusher.lastPushMs))).append('\n')
        }
        LiveLocationPusher.lastError?.let { sb.append("⚠ ").append(it) }
        statusTv.text = sb.toString()
    }
}
