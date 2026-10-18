package org.lyaaz.fuckclip

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lyaaz.ui.PreferenceGroup
import org.lyaaz.ui.SettingsList
import org.lyaaz.ui.SettingsLoading
import org.lyaaz.ui.SettingsMessage
import org.lyaaz.ui.SettingsScaffold
import org.lyaaz.ui.SettingsSearchField
import org.lyaaz.ui.SwitchPreferenceItem
import org.lyaaz.ui.theme.AppTheme as Theme

class SettingsActivity : ComponentActivity(), XposedServiceHelper.OnServiceListener {

    companion object {
        private var mService: XposedService? = null
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: Settings
    private var isReady by mutableStateOf(false)
    private var currentUiMode: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mService?.let { onServiceBind(it) }
        XposedServiceHelper.registerListener(this)
        enableEdgeToEdge()
        currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        setContent {
            Theme(applyStatusBarColor = true) {
                SettingsScaffold(stringResource(R.string.app_name)) {
                    if (isReady) {
                        SettingsScreen(prefs, settings)
                    } else {
                        ServiceUnavailable()
                    }
                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val newUiMode = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (newUiMode != currentUiMode) {
            recreate()
        }
    }

    override fun onServiceBind(service: XposedService) {
        mService = service
        prefs = service.getRemotePreferences("${BuildConfig.APPLICATION_ID}_preferences")
        settings = Settings.getInstance(prefs)
        isReady = true
    }

    override fun onServiceDied(service: XposedService) {
        mService = null
        isReady = false
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    Theme(applyStatusBarColor = true) {
        SettingsScaffold(stringResource(R.string.app_name)) {
            SettingsScreen(null, null)
        }
    }
}

@Composable
private fun ServiceUnavailable() {
    SettingsList {
        item {
            SettingsMessage(
                title = stringResource(R.string.service_unavailable),
                description = stringResource(R.string.service_unavailable_description)
            )
        }
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Composable
fun SettingsScreen(prefs: SharedPreferences?, settings: Settings?) {
    key(prefs, settings) {
        SettingsScreenContent(prefs, settings)
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Composable
private fun SettingsScreenContent(prefs: SharedPreferences?, settings: Settings?) {
    val context = LocalContext.current
    val pm = context.packageManager
    val switchStatus = remember(settings) {
        mutableStateMapOf<String, Boolean>()
    }
    var isLoading by remember(settings) { mutableStateOf(true) }
    val apps by produceState<List<AppView>>(initialValue = emptyList(), key1 = settings) {
        val loaded = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(0)
                .filter {
                    (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                }.map {
                    AppView(
                        icon = it.loadIcon(pm),
                        name = it.loadLabel(pm).toString(),
                        packageName = it.packageName,
                        enabled = settings?.isEnabled(it.packageName) ?: false
                    )
                }.sortedWith(
                    compareBy<AppView> { !it.enabled }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                        .thenBy { it.packageName }
                )
        }
        switchStatus.putAll(loaded.associate { it.packageName to it.enabled })
        value = loaded
        isLoading = false
    }

    var query by rememberSaveable { mutableStateOf("") }
    var enabledOnly by rememberSaveable { mutableStateOf(false) }
    val enabledCount by remember {
        derivedStateOf { switchStatus.count { it.value } }
    }
    val visibleApps by remember(apps) {
        derivedStateOf {
            val search = query.trim()
            apps.filter { app ->
                (!enabledOnly || switchStatus[app.packageName] == true) &&
                    (app.name.contains(search, ignoreCase = true) ||
                        app.packageName.contains(search, ignoreCase = true))
            }
        }
    }

    SettingsList {
        item(key = "summary") {
            PreferenceGroup(highlighted = true) {
                Column(
                    Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.clipboard_access),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        stringResource(R.string.clipboard_access_description),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.enabled_summary, enabledCount, apps.size),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
        item(key = "search") {
            SettingsSearchField(
                query = query,
                onQueryChange = { query = it },
                label = stringResource(R.string.search_apps),
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        item(key = "filters") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !enabledOnly,
                    onClick = { enabledOnly = false },
                    label = { Text(stringResource(R.string.all_apps)) }
                )
                FilterChip(
                    selected = enabledOnly,
                    onClick = { enabledOnly = true },
                    label = { Text(stringResource(R.string.enabled_apps)) }
                )
            }
        }
        if (isLoading) {
            item(key = "loading") {
                SettingsLoading(stringResource(R.string.loading_apps))
            }
        } else if (visibleApps.isEmpty()) {
            item(key = "empty") {
                Text(
                    stringResource(
                        when {
                            apps.isEmpty() -> R.string.no_apps
                            query.isNotBlank() -> R.string.no_search_results
                            else -> R.string.no_enabled_apps
                        }
                    ),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(visibleApps, key = { it.packageName }) { app ->
            val checked = switchStatus.getOrDefault(app.packageName, false)
            PreferenceGroup(
                modifier = Modifier.animateItem(),
                highlighted = checked
            ) {
                SwitchPreferenceItem(
                    title = app.name,
                    summary = app.packageName,
                    checked = checked,
                    onCheckedChange = {
                        prefs?.edit { putBoolean(app.packageName, it) }
                        switchStatus[app.packageName] = it
                    },
                    leadingContent = {
                        Image(
                            bitmap = remember(app.icon) { app.icon.toBitmap().asImageBitmap() },
                            contentDescription = null,
                            modifier = Modifier.size(44.dp)
                        )
                    },
                    titleMaxLines = 1,
                    summaryMinLines = 2,
                    summaryMaxLines = 2,
                    summaryVerticallyCentered = true
                )
            }
        }
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap
    }
    val width = if (intrinsicWidth > 0) intrinsicWidth else 1
    val height = if (intrinsicHeight > 0) intrinsicHeight else 1
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

data class AppView(
    val icon: Drawable,
    val name: String,
    val packageName: String,
    val enabled: Boolean = false
)
