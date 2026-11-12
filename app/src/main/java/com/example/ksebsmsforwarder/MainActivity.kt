package com.example.ksebsmsforwarder

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var numberInput: EditText
    private lateinit var senderInput: EditText
    private lateinit var matchSpinner: Spinner
    private lateinit var simSpinner: Spinner
    private lateinit var enabledSwitch: Switch
    private lateinit var statusText: TextView
    private var subscriptions: List<SubscriptionInfo> = emptyList()
    private var simLabels: List<String> = emptyList()

    private val prefs by lazy {
        getSharedPreferences(KsebSmsReceiver.PREFS, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val scroll = ScrollView(this).apply {
            addView(content)
        }

        content.addView(TextView(this).apply {
            text = "KSEB SMS Forwarder"
            textSize = 24f
        })
        content.addView(TextView(this).apply {
            text = "Automatically forward matching KSEB SMS messages to another mobile number."
            textSize = 15f
            setPadding(0, 10, 0, 20)
        })

        addLabel(content, "Recipient mobile number")
        numberInput = EditText(this).apply {
            hint = "10-digit number or +91 number"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setSingleLine(true)
            setText(prefs.getString(KsebSmsReceiver.KEY_NUMBER, "").orEmpty())
        }
        content.addView(numberInput)

        addLabel(content, "Sender filter keywords")
        senderInput = EditText(this).apply {
            hint = "KSEBIT, KSEB, KSEBILL"
            setSingleLine(false)
            minLines = 2
            setText(prefs.getString(KsebSmsReceiver.KEY_SENDER_FILTER, "KSEBIT").orEmpty())
        }
        content.addView(senderInput)
        content.addView(TextView(this).apply {
            text = "Enter one or more keywords separated by commas. Any matching keyword will qualify."
            textSize = 12f
            setPadding(0, 4, 0, 12)
        })

        addLabel(content, "Sender matching method")
        matchSpinner = Spinner(this)
        matchSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Contains", "Exact match", "Starts with")
        )
        matchSpinner.setSelection(prefs.getInt(KsebSmsReceiver.KEY_MATCH_MODE, 0).coerceIn(0, 2))
        content.addView(matchSpinner)

        addLabel(content, "SIM used to send forwarded SMS")
        simSpinner = Spinner(this)
        content.addView(simSpinner)
        content.addView(TextView(this).apply {
            text = "A single SIM is selected automatically. If you use two SIMs, choose which one should send forwarded messages."
            textSize = 12f
            setPadding(0, 4, 0, 12)
        })

        enabledSwitch = Switch(this).apply {
            text = "Automatic forwarding"
            textSize = 16f
            isChecked = prefs.getBoolean(KsebSmsReceiver.KEY_ENABLED, false)
            setPadding(0, 18, 0, 18)
        }
        content.addView(enabledSwitch)

        content.addView(Button(this).apply {
            text = "Save settings"
            setOnClickListener { saveSettings() }
        })

        statusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, 20, 0, 0)
        }
        content.addView(statusText)
        content.addView(TextView(this).apply {
            text = "Battery-friendly: receives Android SMS events only. No polling, background service, internet access, or message history."
            textSize = 13f
            setPadding(0, 22, 0, 0)
        })

        setContentView(scroll)
        refreshSimOptions()
        updateStatus()
    }

    private fun addLabel(parent: LinearLayout, value: String) {
        parent.addView(TextView(this).apply {
            text = value
            textSize = 16f
            setPadding(0, 14, 0, 4)
        })
    }

    private fun refreshSimOptions() {
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simLabels = listOf("Grant phone permission to list SIMs")
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simLabels)
            return
        }

        try {
            val previousSelection = subscriptions.getOrNull(simSpinner.selectedItemPosition)?.subscriptionId
            val manager = getSystemService(SubscriptionManager::class.java)
            subscriptions = manager.activeSubscriptionInfoList.orEmpty()
            simLabels = when {
                subscriptions.isEmpty() -> listOf("No active SIM found")
                else -> subscriptions.mapIndexed { index, info ->
                    val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM"
                    val name = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${index + 1}"
                    "$name — $carrier (slot ${info.simSlotIndex + 1})"
                }
            }
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simLabels)

            if (subscriptions.isNotEmpty()) {
                val savedId = prefs.getInt(KsebSmsReceiver.KEY_SIM_SUBSCRIPTION_ID, -1)
                val preferredId = previousSelection ?: savedId
                val selectedIndex = subscriptions.indexOfFirst { it.subscriptionId == preferredId }
                    .takeIf { it >= 0 } ?: 0
                simSpinner.setSelection(selectedIndex)
            }
        } catch (_: SecurityException) {
            subscriptions = emptyList()
            simLabels = listOf("Unable to read SIM information")
            simSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, simLabels)
        }
    }

    private fun saveSettings() {
        val number = normalizeIndianNumber(numberInput.text.toString().trim())
        if (number == null) {
            numberInput.error = "Enter a valid mobile number"
            return
        }

        val filters = senderInput.text.toString()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
        if (filters.isEmpty()) {
            senderInput.error = "Enter at least one sender keyword"
            return
        }

        val required = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE
        )
        val missing = required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), REQUEST_PERMISSIONS)
            return
        }

        refreshSimOptions()
        if (subscriptions.isEmpty()) {
            Toast.makeText(this, "No active SIM found. Insert an active SIM and try again.", Toast.LENGTH_LONG).show()
            return
        }

        val selectedIndex = simSpinner.selectedItemPosition
        if (selectedIndex !in subscriptions.indices) {
            Toast.makeText(this, "Please select a valid sending SIM.", Toast.LENGTH_LONG).show()
            return
        }
        val selectedSubscriptionId = subscriptions[selectedIndex].subscriptionId

        prefs.edit()
            .putString(KsebSmsReceiver.KEY_NUMBER, number)
            .putString(KsebSmsReceiver.KEY_SENDER_FILTER, filters.joinToString(","))
            .putInt(KsebSmsReceiver.KEY_MATCH_MODE, matchSpinner.selectedItemPosition)
            .putInt(KsebSmsReceiver.KEY_SIM_SUBSCRIPTION_ID, selectedSubscriptionId)
            .putBoolean(KsebSmsReceiver.KEY_ENABLED, enabledSwitch.isChecked)
            .apply()

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    private fun normalizeIndianNumber(value: String): String? {
        val digits = value.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            value.startsWith("+") && digits.length in 11..15 -> "+$digits"
            else -> null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS &&
            grantResults.isNotEmpty() &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            refreshSimOptions()
            saveSettings()
        } else {
            enabledSwitch.isChecked = false
            Toast.makeText(this, "SMS and phone permissions are required.", Toast.LENGTH_LONG).show()
            updateStatus()
        }
    }

    private fun updateStatus() {
        val active = prefs.getBoolean(KsebSmsReceiver.KEY_ENABLED, false)
        val number = prefs.getString(KsebSmsReceiver.KEY_NUMBER, "").orEmpty()
        val filters = prefs.getString(KsebSmsReceiver.KEY_SENDER_FILTER, "KSEBIT").orEmpty()
        statusText.text = if (active && number.isNotBlank()) {
            "Status: Enabled\nRecipient: $number\nSender filters: $filters"
        } else {
            "Status: Not active. Configure the settings, enable forwarding, and save."
        }
    }

    companion object {
        private const val REQUEST_PERMISSIONS = 1001
    }
}
