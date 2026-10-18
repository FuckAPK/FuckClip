package org.lyaaz.fuckclip

import android.content.SharedPreferences
import android.content.FakePreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

typealias Drawable = String
object ApplicationInfo { const val FLAG_SYSTEM = 1 }
class ProbeApp(val packageName: String, val name: String = packageName) {
    val flags = 0
    fun loadIcon(pm: ProbePackageManager) = "icon:$packageName"
    fun loadLabel(pm: ProbePackageManager) = name
}
class LoadGate {
    val started = AtomicBoolean()
    val release = CountDownLatch(1)
}
class ProbePackageManager {
    @Volatile var apps = listOf(ProbeApp("old"))
    @Volatile var gate: LoadGate? = null
    fun getInstalledApplications(flags: Int): List<ProbeApp> {
        val result = apps
        gate?.let { current ->
            current.started.set(true)
            check(current.release.await(10, TimeUnit.SECONDS)) { "Loader gate timed out" }
        }
        return result
    }
}
class ProbeContext { val packageManager = ProbePackageManager() }
object LocalContext { val current = ProbeContext() }
object Probe {
    var apps = emptyList<AppView>()
    var settings: Settings? = null
    lateinit var switches: SnapshotStateMap<String, Boolean>
    var visibleApps = emptyList<AppView>()
    var enabledCount = 0
    lateinit var search: (String) -> Unit
    lateinit var filterEnabled: (Boolean) -> Unit
}

// PRODUCTION_SCREEN

@Composable
private fun SettingsScreenContent(prefs: SharedPreferences?, settings: Settings?) {
    // PRODUCTION_LOADER
    Probe.apps = apps
    Probe.settings = settings
    Probe.switches = switchStatus
    Probe.visibleApps = visibleApps
    Probe.enabledCount = enabledCount
    Probe.search = { query = it }
    Probe.filterEnabled = { enabledOnly = it }
}

// PRODUCTION_APP_VIEW

class UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun remove(index: Int, count: Int) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun onClear() {}
}

fun main() = runBlocking {
    val clock = BroadcastFrameClock()
    val recomposer = Recomposer(coroutineContext + clock)
    val runner = launch(clock) { recomposer.runRecomposeAndApplyChanges() }
    val composition = Composition(UnitApplier(), recomposer)
    val oldPrefs = FakePreferences().apply { values["old"] = true }
    val oldSettings = Settings.getInstance(oldPrefs)
    val session = mutableStateOf<Pair<SharedPreferences, Settings>>(oldPrefs to oldSettings)
    val pending = LoadGate()
    val pm = LocalContext.current.packageManager
    suspend fun tick() {
        Snapshot.sendApplyNotifications()
        yield()
        if (clock.hasAwaiters) clock.sendFrame(System.nanoTime())
        yield()
    }
    suspend fun loaded(packageName: String) = withTimeout(5000) {
        while (Probe.apps.firstOrNull()?.packageName != packageName) { tick(); delay(1) }
    }
    suspend fun rebound(prefs: SharedPreferences, settings: Settings) = withTimeout(5000) {
        session.value = prefs to settings
        while (Probe.settings !== settings) { tick(); delay(1) }
    }
    try {
        composition.setContent { SettingsScreen(session.value.first, session.value.second) }
        loaded("old")
        check(Probe.apps.single().packageName == "old" && Probe.switches["old"] == true)

        val replacement = FakePreferences().apply { values["new"] = false }
        pm.apps = listOf(ProbeApp("new")); pm.gate = pending
        rebound(replacement, Settings.getInstance(replacement))
        check(Probe.apps.isEmpty() && Probe.switches.isEmpty()) { "Stale rows remain editable after rebinding" }
        withTimeout(5000) { while (!pending.started.get()) { tick(); delay(1) } }

        // A second bind must cancel the pending publication from the previous session.
        val latest = FakePreferences().apply { values["latest"] = true }
        pm.apps = listOf(ProbeApp("latest")); pm.gate = null
        rebound(latest, Settings.getInstance(latest))
        check(Probe.apps.none { it.packageName != "latest" })
        loaded("latest")
        check(Probe.switches["latest"] == true)
        Probe.switches["latest"] = false; latest.values["latest"] = false
        pending.release.countDown()
        repeat(20) { tick(); delay(1) }
        check(Probe.apps.single().packageName == "latest")
        check(Probe.switches.keys.toSet() == setOf("latest") && Probe.switches["latest"] == false)
        check(latest.values["latest"] == false && replacement.values["new"] == false)

        // Rebinding to a fresh Settings wrapper for the same Preferences also resets both states.
        val lastGate = LoadGate()
        pm.gate = lastGate
        rebound(latest, Settings.getInstance(latest))
        check(Probe.apps.isEmpty() && Probe.switches.isEmpty())
        lastGate.release.countDown()
        loaded("latest")
        check(Probe.switches["latest"] == false)

        pm.gate = null
        pm.apps = listOf(
            ProbeApp("com.example.notes", "笔记"),
            ProbeApp("org.example.chat", "Messenger"),
            ProbeApp("org.example.mail", "Mail")
        )
        val filterPrefs = FakePreferences().apply { values["org.example.chat"] = true }
        rebound(filterPrefs, Settings.getInstance(filterPrefs))
        loaded("org.example.chat")
        suspend fun expectVisible(vararg packages: String) = withTimeout(5000) {
            while (Probe.visibleApps.map { it.packageName } != packages.toList()) {
                tick(); delay(1)
            }
        }
        expectVisible("org.example.chat", "org.example.mail", "com.example.notes")
        check(Probe.enabledCount == 1)
        Probe.search("  MESSENger  ")
        expectVisible("org.example.chat")
        Probe.search("COM.EXAMPLE")
        expectVisible("com.example.notes")
        Probe.search("笔记")
        expectVisible("com.example.notes")
        Probe.filterEnabled(true)
        expectVisible()
        Probe.search("")
        expectVisible("org.example.chat")
        Probe.switches["org.example.chat"] = false
        expectVisible()
        check(Probe.enabledCount == 0)
        Probe.switches["com.example.notes"] = true
        expectVisible("com.example.notes")
        check(Probe.enabledCount == 1)
        Probe.filterEnabled(false)
        expectVisible("org.example.chat", "org.example.mail", "com.example.notes")
        Probe.search("missing")
        expectVisible()
        println("App filtering: name, package, Chinese, case and whitespace; allowed-only reacts to toggles; totals update; empty results")
        println("Compose rebinding: old rows removed immediately; cancelled loaders cannot overwrite new state; user edits survive; same Preferences rebinding")
    } finally {
        pending.release.countDown()
        pm.gate?.release?.countDown()
        composition.dispose(); recomposer.cancel(); runner.cancelAndJoin()
    }
}
