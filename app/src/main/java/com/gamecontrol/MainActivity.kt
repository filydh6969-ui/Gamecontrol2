package com.gamecontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

@SuppressLint("MissingPermission")
class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var pad: HidGamepad
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            text = "Iniciando..."
            setPadding(16, 0, 0, 0)
        }
        pad = HidGamepad(this) { msg -> runOnUiThread { status.text = msg } }

        val visible = Button(this).apply {
            text = "Visible"
            textSize = 11f
            isAllCaps = false
            setOnClickListener { makeVisible() }
        }
        val devices = Button(this).apply {
            text = "Dispositivos"
            textSize = 11f
            isAllCaps = false
            setOnClickListener { pickDevice() }
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(visible)
            addView(devices)
            addView(status)
        }

        val root = FrameLayout(this)
        root.addView(GamepadView(this, pad))
        root.addView(
            bar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
        )
        setContentView(root)
        ensurePermissions()
    }

    private fun needed(): List<String> =
        if (Build.VERSION.SDK_INT >= 31) listOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN
        ) else emptyList()

    private fun missing(): List<String> =
        needed().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun ensurePermissions() {
        val m = missing()
        if (m.isEmpty()) startPad() else requestPermissions(m.toTypedArray(), 1)
    }

    private fun startPad() {
        if (started) return
        started = true
        pad.start()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (missing().isEmpty()) startPad() else status.text = "Faltan permisos de Bluetooth"
    }

    private fun makeVisible() {
        if (missing().isNotEmpty()) { ensurePermissions(); return }
        val i = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(i)
    }

    private fun pickDevice() {
        if (missing().isNotEmpty()) { ensurePermissions(); return }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        val list = adapter.bondedDevices.toList()
        if (list.isEmpty()) {
            status.text = "Vincula primero el iPad"
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Conectar a")
            .setItems(list.map { it.name ?: it.address }.toTypedArray()) { _, i ->
                pad.connect(list[i])
            }
            .show()
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pad.stop()
    }
}
