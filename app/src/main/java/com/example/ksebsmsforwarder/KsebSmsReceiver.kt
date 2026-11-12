package com.example.ksebsmsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.provider.Telephony
import android.util.Log

class KsebSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        val destination = prefs.getString(KEY_NUMBER, null) ?: return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        if (!sender.contains("KSEBIT", ignoreCase = true)) return

        // Reassemble multipart SMS before forwarding.
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        try {
            val manager = context.getSystemService(SmsManager::class.java)
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                manager.sendTextMessage(destination, null, body, null, null)
            }
        } catch (e: Exception) {
            // Do not store message contents or personal data in logs.
            Log.e(TAG, "KSEB SMS forwarding failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val PREFS = "kseb_forwarder_settings"
        const val KEY_NUMBER = "recipient_number"
        const val KEY_ENABLED = "forwarding_enabled"
        private const val TAG = "KsebSmsForwarder"
    }
}
