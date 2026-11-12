package com.example.ksebsmsforwarder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var numberInput: EditText
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView

    private val prefs by lazy {
        getSharedPreferences(KsebSmsReceiver.PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 36, 28, 24)
        }

        val title = TextView(this).apply {
            text = "KSEB SMS Forwarder"
            textSize = 24f
        }
        layout.addView(title)

        val description = TextView(this).apply {
            text = "Automatically forwards SMS messages from KSEB sender IDs containing KSEBIT."
            textSize = 15f
            setPadding(0, 12, 0, 22)
        }
        layout.addView(description)

        layout.addView(TextView(this).apply {
            text = "Recipient mobile number"
            textSize = 16f
        })

        numberInput = EditText(this).apply {
            hint = "10-digit Indian mobile number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(prefs.getString(KsebSmsReceiver.KEY_NUMBER, "") ?: "")
        }
        layout.addView(numberInput)

        enabledSwitch = Switch(this).apply {
            text = "Automatic forwarding"
            textSize = 16f
            isChecked = prefs.getBoolean(KsebSmsReceiver.KEY_ENABLED, false)
            setPadding(0, 18, 0, 18)
        }
        layout.addView(enabledSwitch)

        val saveButton = Button(this).apply {
            text = "Save settings"
            setOnClickListener { saveSettings() }
        }
        layout.addView(saveButton)

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }
        layout.addView(statusText)

        val note = TextView(this).apply {
            text = "Battery-friendly: listens for Android SMS events only. No polling, permanent service, internet, or message history."
            textSize = 13f
            setPadding(0, 22, 0, 0)
        }
        layout.addView(note)

        setContentView(layout)
        updateStatus()
    }

    private fun saveSettings() {
        val rawNumber = numberInput.text.toString().trim()
        val number = normalizeIndianNumber(rawNumber)

        if (number == null) {
            numberInput.error = "Enter a valid 10-digit Indian mobile number or +91 number"
            return
        }

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS),
                REQUEST_SMS_PERMISSIONS
            )
            return
        }

        prefs.edit()
            .putString(KsebSmsReceiver.KEY_NUMBER, number)
            .putBoolean(KsebSmsReceiver.KEY_ENABLED, enabledSwitch.isChecked)
            .apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun normalizeIndianNumber(value: String): String? {
        val digits = value.filter { it.isDigit() }
        val normalized = when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            value.startsWith("+") && digits.length in 11..15 -> "+$digits"
            else -> return null
        }
        return normalized
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SMS_PERMISSIONS &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            saveSettings()
        } else {
            enabledSwitch.isChecked = false
            Toast.makeText(this, "SMS permissions are required for forwarding", Toast.LENGTH_LONG).show()
            updateStatus()
        }
    }

    private fun updateStatus() {
        val active = prefs.getBoolean(KsebSmsReceiver.KEY_ENABLED, false)
        val number = prefs.getString(KsebSmsReceiver.KEY_NUMBER, "").orEmpty()
        statusText.text = if (active && number.isNotBlank()) {
            "Status: Enabled\nRecipient: $number"
        } else {
            "Status: Not active. Enter a number, enable forwarding, and save."
        }
    }

    companion object {
        private const val REQUEST_SMS_PERMISSIONS = 1001
    }
}
