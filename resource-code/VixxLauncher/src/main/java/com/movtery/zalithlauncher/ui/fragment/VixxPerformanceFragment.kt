package com.movtery.zalithlauncher.ui.fragment

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.movtery.anim.AnimPlayer
import com.movtery.anim.animations.Animations
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.ui.fragment.SettingsFragment
import com.movtery.zalithlauncher.utils.ZHTools
import java.io.File
import java.util.Locale

class VixxPerformanceFragment : FragmentWithAnim(R.layout.fragment_vixx_performance) {
    companion object {
        const val TAG = "VixxPerformanceFragment"
        private const val REFRESH_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var active = false

    private val refreshTask = object : Runnable {
        override fun run() {
            if (!active || !isAdded || view == null) return
            refreshMetrics()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_vixx_performance, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<TextView>(R.id.openVideoSettings).setOnClickListener { openSettings(0) }
        view.findViewById<TextView>(R.id.openGameSettings).setOnClickListener { openSettings(2) }
        refreshMetrics()
    }

    override fun onResume() {
        super.onResume()
        active = true
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        active = false
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    override fun onDestroyView() {
        handler.removeCallbacks(refreshTask)
        super.onDestroyView()
    }

    private fun refreshMetrics() {
        val context = context ?: return
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)

        val used = (memory.totalMem - memory.availMem).coerceAtLeast(0L)
        val pssMb = Debug.getPss() / 1024L
        val runtime = Runtime.getRuntime()
        val javaUsed = (runtime.totalMemory() - runtime.freeMemory()).coerceAtLeast(0L)
        val javaMax = runtime.maxMemory()

        text(R.id.systemRamValue, "${formatBytes(used)} / ${formatBytes(memory.totalMem)}")
        text(R.id.systemRamDetail, "${formatBytes(memory.availMem)} disponíveis • ${if (memory.lowMemory) "memória baixa" else "nível normal"}")
        text(R.id.processRamValue, "${pssMb} MB")
        text(R.id.processRamDetail, "PSS do processo VIXX Launcher")

        text(R.id.javaHeapValue, "Heap Java: ${formatBytes(javaUsed)} usados / ${formatBytes(javaMax)} máx.")

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            thermalLabel(powerManager.currentThermalStatus)
        } else {
            "Não disponível nesta versão do Android"
        }
        text(R.id.thermalValue, "Estado térmico: $thermal")

        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val battery = batteryIntent?.getIntExtra("level", -1) ?: -1
        val scale = batteryIntent?.getIntExtra("scale", 100) ?: 100
        val percent = if (battery >= 0 && scale > 0) (battery * 100 / scale) else -1
        val status = batteryIntent?.getIntExtra("status", -1) ?: -1
        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
        val batteryTemp = batteryIntent?.getIntExtra("temperature", -1) ?: -1
        val tempText = if (batteryTemp > 0) String.format(Locale.getDefault(), " • bateria %.1f°C", batteryTemp / 10f) else ""
        text(R.id.batteryValue, "Bateria: ${if (percent >= 0) "$percent%" else "indisponível"} • ${if (charging) "carregando" else "não carregando"}$tempText")

        val stat = StatFs(context.filesDir.absolutePath)
        val totalStorage = stat.totalBytes
        val freeStorage = stat.availableBytes
        text(R.id.storageValue, "Armazenamento interno: ${formatBytes(freeStorage)} livres / ${formatBytes(totalStorage)}")
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Normal"
        PowerManager.THERMAL_STATUS_LIGHT -> "Leve"
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderado"
        PowerManager.THERMAL_STATUS_SEVERE -> "Elevado"
        PowerManager.THERMAL_STATUS_CRITICAL -> "Crítico"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergência"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Desligamento iminente"
        else -> "Desconhecido"
    }

    private fun text(id: Int, value: String) {
        view?.findViewById<TextView>(id)?.text = value
    }

    private fun openSettings(tab: Int) {
        val args = Bundle().apply { putInt(SettingsFragment.ARG_INITIAL_TAB, tab) }
        ZHTools.swapFragmentWithAnim(this, SettingsFragment::class.java, SettingsFragment.TAG, args)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        do {
            value /= 1024.0
            unit++
        } while (value >= 1024.0 && unit < units.lastIndex)
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
    }

    override fun slideIn(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.BounceInRight)) }
    }

    override fun slideOut(animPlayer: AnimPlayer) {
        view?.let { animPlayer.apply(AnimPlayer.Entry(it, Animations.FadeOutLeft)) }
    }
}
