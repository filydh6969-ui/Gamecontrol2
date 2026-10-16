package com.gamecontrol

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.concurrent.Executors

/**
 * Modo TECLADO Bluetooth. Los botones del control se convierten en teclas
 * con el mapeo por defecto de RetroArch:
 *   Cruceta / stick izq. -> flechas
 *   A -> Z, B -> X, X -> A, Y -> S   (posiciones del control Xbox)
 *   LB -> Q, RB -> W, LT -> E, RT -> R
 *   Back -> Shift derecho (Select), Start -> Enter
 *   Stick derecho -> I / J / K / L
 */
@SuppressLint("MissingPermission")
class HidGamepad(
    private val context: Context,
    private val onStatus: (String) -> Unit
) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var hid: BluetoothHidDevice? = null
    @Volatile private var host: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val descriptor: ByteArray = intArrayOf(
        0x05, 0x01, 0x09, 0x06, 0xA1, 0x01,   // Keyboard, Application
        0x85, 0x01,                           // Report ID 1
        0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7,   // Modificadores
        0x15, 0x00, 0x25, 0x01,
        0x75, 0x01, 0x95, 0x08, 0x81, 0x02,
        0x95, 0x01, 0x75, 0x08, 0x81, 0x01,   // Reservado
        0x95, 0x05, 0x75, 0x01,               // LEDs (salida)
        0x05, 0x08, 0x19, 0x01, 0x29, 0x05,
        0x91, 0x02,
        0x95, 0x01, 0x75, 0x03, 0x91, 0x01,
        0x95, 0x06, 0x75, 0x08,               // 6 teclas
        0x15, 0x00, 0x25, 0x65,
        0x05, 0x07, 0x19, 0x00, 0x29, 0x65,
        0x81, 0x00,
        0xC0
    ).map { it.toByte() }.toByteArray()

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            onStatus(if (registered) "Listo: vincula desde el iPad" else "No registrado")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    host = device
                    onStatus("Conectado: " + (device?.name ?: "dispositivo"))
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == host) host = null
                    onStatus("Desconectado")
                }
            }
        }
    }

    fun start() {
        val a = adapter
        if (a == null || !a.isEnabled) {
            onStatus("Activa el Bluetooth")
            return
        }
        a.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    val h = proxy as BluetoothHidDevice
                    hid = h
                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "Gamecontrol Keyboard",
                        "Teclado Bluetooth",
                        "Gamecontrol",
                        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                        descriptor
                    )
                    val ok = h.registerApp(sdp, null, null, executor, callback)
                    if (!ok) onStatus("Este celular no soporta HID Device")
                }
            }

            override fun onServiceDisconnected(profile: Int) {
                hid = null
                host = null
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    fun connect(device: BluetoothDevice) {
        val ok = hid?.connect(device) ?: false
        onStatus(if (ok) "Conectando..." else "No se pudo conectar")
    }

    // Mantiene la misma firma que la versión gamepad; la convierte a teclas.
    fun send(lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int, hat: Int, buttons: Int) {
        val h = hid ?: return
        val d = host ?: return

        val up = hat == 1 || hat == 2 || hat == 8 || ly < 64
        val down = hat in 4..6 || ly > 192
        val right = hat in 2..4 || lx > 192
        val left = hat in 6..8 || lx < 64

        val keys = ArrayList<Int>()
        if (up) keys.add(0x52)
        if (down) keys.add(0x51)
        if (left) keys.add(0x50)
        if (right) keys.add(0x4F)

        fun on(bit: Int) = (buttons shr bit) and 1 == 1
        if (on(0)) keys.add(0x1D)   // A -> Z
        if (on(1)) keys.add(0x1B)   // B -> X
        if (on(2)) keys.add(0x04)   // X -> A
        if (on(3)) keys.add(0x16)   // Y -> S
        if (on(4)) keys.add(0x14)   // LB -> Q
        if (on(5)) keys.add(0x1A)   // RB -> W
        if (lt > 127) keys.add(0x08) // LT -> E
        if (rt > 127) keys.add(0x15) // RT -> R
        if (on(7)) keys.add(0x28)   // Start -> Enter
        if (ry < 64) keys.add(0x0C)  // I
        if (ry > 192) keys.add(0x0E) // K
        if (rx < 64) keys.add(0x0D)  // J
        if (rx > 192) keys.add(0x0F) // L

        val r = ByteArray(8)
        r[0] = if (on(6)) 0x20 else 0x00   // Back -> Shift derecho (Select)
        val k = keys.take(6)
        for (i in k.indices) r[2 + i] = k[i].toByte()
        h.sendReport(d, 1, r)
    }

    fun stop() {
        hid?.let {
            it.unregisterApp()
            adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it)
        }
        hid = null
        host = null
    }
}
