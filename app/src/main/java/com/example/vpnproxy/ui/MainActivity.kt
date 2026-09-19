package com.example.vpnproxy.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.vpnproxy.R
import com.example.vpnproxy.hotspot.HotspotHelper
import com.example.vpnproxy.vpn.MyVpnService

class MainActivity : AppCompatActivity() {

    private val VPN_REQUEST_CODE = 100
    private lateinit var vlessLinkInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vlessLinkInput = findViewById(R.id.vlessLinkInput)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.startButton).setOnClickListener { prepareAndStartVpn() }
        findViewById<Button>(R.id.stopButton).setOnClickListener { stopVpn() }
        findViewById<Button>(R.id.hotspotButton).setOnClickListener {
            HotspotHelper(this).openHotspotSettings()
        }
    }

    private fun prepareAndStartVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            onActivityResult(VPN_REQUEST_CODE, RESULT_OK, null)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
            startVpnService()
        } else {
            statusText.text = "المستخدم رفض صلاحية VPN"
        }
    }

    private fun startVpnService() {
        val serviceIntent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_START
            putExtra("vless_link", vlessLinkInput.text.toString().trim())
        }
        startForegroundService(serviceIntent)
        statusText.text = "متصل"
    }

    private fun stopVpn() {
        val serviceIntent = Intent(this, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_STOP
        }
        startService(serviceIntent)
        statusText.text = "متوقف"
    }
}
