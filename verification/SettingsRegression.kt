import android.content.FakePreferences
import org.lyaaz.fuckclip.Settings as ClipSettings

fun main() {
    val old = FakePreferences().apply { values["example"] = false }
    val rebound = FakePreferences().apply { values["example"] = true }
    check(!ClipSettings.getInstance(old).isEnabled("example"))
    check(ClipSettings.getInstance(rebound).isEnabled("example"))
    println("Settings regressions: replacement Preferences used after rebinding")
}
