package org.lyaaz.fuckclip

import android.annotation.SuppressLint
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.lyaaz.fuckclip.ui.AppTheme as Theme

class SettingsActivity : ComponentActivity() {

    private var currentUiMode: Int? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        currentUiMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        setContent {
            Theme {
                SettingsScreen()
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
}

@Preview
@Composable
fun SettingsScreenPreview() {
    Theme {
        SettingsScreen()
    }
}

@SuppressLint("QueryPermissionsNeeded")
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = Utils.getPrefs(context)
    val settings = Settings.getInstance(prefs)
    val pm = context.packageManager
    val switchStatus = remember {
        mutableStateMapOf<String, Boolean>()
    }
    val apps = pm.getInstalledApplications(0)
        .filter {
            (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }.map {
            switchStatus[it.packageName] =
                settings.isEnabled(it.packageName)
            AppView(
                icon = it.loadIcon(pm),
                name = it.loadLabel(pm).toString(),
                packageName = it.packageName
            )
        }.sortedWith(
            compareBy({!switchStatus.getOrDefault(it.packageName, false)}, {it.packageName})
        )

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .imePadding()
    ) {
        items(apps) { app ->
            SwitchPreferenceItem(
                name = app.name,
                packageName = app.packageName,
                icon = app.icon,
                checked = switchStatus.getOrDefault(app.packageName, false),
                onCheckedChange = {
                    switchStatus[app.packageName] = it
                    prefs.edit { putBoolean(app.packageName, it) }
                },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
                    fadeOutSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                )
            )
        }
        item {
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(
                    WindowInsets.systemBars
                )
            )
        }
    }
}

@Composable
fun SwitchPreferenceItem(
    name: String,
    packageName: String,
    icon: Drawable,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(48.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) {
        return this.bitmap
    }
    val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

data class AppView(
    val icon: Drawable,
    val name: String,
    val packageName: String
)