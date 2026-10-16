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
 * Hace que el celular se anuncie como un gamepad Bluetooth HID.
 * Reporte (ID 1, 9 bytes): LX, LY, RX, RY, LT, RT, hat, botones(2 bytes)
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
        0x05, 0x01,             // Usage Page (Generic Desktop)
        0x09, 0x05,             // Usage (Game Pad)
        0xA1, 0x01,             // Collection (Application)
        0x85, 0x01,             //   Report ID (1)
        0x09, 0x01,             //   Usage (Pointer)
        0xA1, 0x00,             //   Collection (Physical)
        0x09, 0x30, 0x09, 0x31, //     X, Y
        0x09, 0x32, 0x09, 0x35, //     Z, Rz
        0x15, 0x00,             //     Logical Min 0
        0x26, 0xFF, 0x00,       //     Logical Max 255
        0x75, 0x08,             //     Report Size 8
        0x95, 0x04,             //     Report Count 4
        0x81, 0x02,             //     Input (Data, Var, Abs)
        0xC0,                   //   End Collection
        0x09, 0x33, 0x09, 0x34, //   Rx, Ry (gatillos)
        0x15, 0x00,
        0x26, 0xFF, 0x00,
        0x75, 0x08,
        0x95, 0x02,
        0x81, 0x02,
        0x09, 0x39,             //   Hat switch
        0x15, 0x01,
        0x25, 0x08,
        0x35, 0x00,
        0x46, 0x3B, 0x01,
        0x65, 0x14,
        0x75, 0x04,
        0x95, 0x01,
        0x81, 0x42,             //   Input (Data, Var, Abs, Null state)
        0x75, 0x04,             //   Relleno 4 bits
        0x95, 0x01,
        0x81, 0x03,
        0x05, 0x09,             //   Usage Page (Button)
        0x19, 0x01,
        0x29, 0x10,             //   16 botones
        0x15, 0x00,
        0x25, 0x01,
        0x75, 0x01,
        0x95, 0x10,
        0x81, 0x02,
        0xC0                    // End Collection
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
                        "Gamepad",
                        "Android Gamepad",
                        "Gamecontrol",
                        BluetoothHidDevice.SUBCLASS2_GAMEPAD,
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

    fun send(lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int, hat: Int, buttons: Int) {
        val h = hid ?: return
        val d = host ?: return
        val r = ByteArray(9)
        r[0] = lx.toByte()
        r[1] = ly.toByte()
        r[2] = rx.toByte()
        r[3] = ry.toByte()
        r[4] = lt.toByte()
        r[5] = rt.toByte()
        r[6] = hat.toByte()
        r[7] = (buttons and 0xFF).toByte()
        r[8] = ((buttons shr 8) and 0xFF).toByte()
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
