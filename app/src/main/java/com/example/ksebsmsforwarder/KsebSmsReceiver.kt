package com.example.ksebsmsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.provider.Telephony
import android.util.Log

class KsebSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return

        val destination = prefs.getString(KEY_NUMBER, null)?.takeIf { it.isNotBlank() } ?: return
        val filters = prefs.getString(KEY_SENDER_FILTER, "KSEBIT")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (filters.isEmpty()) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress.orEmpty()
        val matchMode = prefs.getInt(KEY_MATCH_MODE, MATCH_CONTAINS)
        val matches = filters.any { keyword ->
            when (matchMode) {
                MATCH_EXACT -> sender.equals(keyword, ignoreCase = true)
                MATCH_STARTS_WITH -> sender.startsWith(keyword, ignoreCase = true)
                else -> sender.contains(keyword, ignoreCase = true)
            }
        }
        if (!matches) return

        // Android delivers the parts of one multipart SMS in this broadcast.
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
        if (body.isBlank()) return

        try {
            val subscriptionId = prefs.getInt(KEY_SIM_SUBSCRIPTION_ID, -1)
            if (subscriptionId < 0) {
                Log.e(TAG, "No sending SIM is configured")
                return
            }
            val manager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                manager.sendTextMessage(destination, null, body, null, null)
            }
        } catch (e: Exception) {
            // Never log SMS content or recipient details.
            Log.e(TAG, "SMS forwarding failed: ${e.javaClass.simpleName}")
        }
    }

    companion object {
        const val PREFS = "kseb_forwarder_settings"
        const val KEY_NUMBER = "recipient_number"
        const val KEY_ENABLED = "forwarding_enabled"
        const val KEY_SENDER_FILTER = "sender_filter"
        const val KEY_MATCH_MODE = "sender_match_mode"
        const val KEY_SIM_SUBSCRIPTION_ID = "sending_sim_subscription_id"

        const val MATCH_CONTAINS = 0
        const val MATCH_EXACT = 1
        const val MATCH_STARTS_WITH = 2

        private const val TAG = "KsebSmsForwarder"
    }
}
