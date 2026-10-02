package com.syedafridi.productivity_agent.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.syedafridi.productivity_agent.services.AgentForegroundService

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ProductivityAgentBoot"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                Log.i(TAG, "Device boot/update detected ($action). Starting 24/7 AgentForegroundService.")
            } catch (_: Throwable) {
                println("[$TAG] Boot/update detected. Starting 24/7 AgentForegroundService.")
            }

            val serviceIntent = Intent(context, AgentForegroundService::class.java).apply {
                this.action = AgentForegroundService.ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (t: Throwable) {
                try {
                    Log.e(TAG, "Failed to start AgentForegroundService on boot", t)
                } catch (_: Throwable) {
                    println("[$TAG] Failed to start AgentForegroundService on boot: ${t.message}")
                }
            }
        }
    }
}
