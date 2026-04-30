package org.lyaaz.fuckclip

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam

class MainHook : XposedModule() {

    private lateinit var prefs: SharedPreferences
    private lateinit var settings: Settings

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        prefs = getRemotePreferences("${BuildConfig.APPLICATION_ID}_preferences")
        settings = Settings.getInstance(prefs)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val classLoader = param.classLoader

        val clipboardServiceClass = runCatching {
            Class.forName(
                "com.android.server.clipboard.ClipboardService",
                false,
                classLoader
            )
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to find ClipboardService", it)
        }.getOrNull() ?: return

        runCatching {
            val method = clipboardServiceClass.getDeclaredMethod(
                "clipboardAccessAllowed",
                Int::class.java,
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Boolean::class.java,
            )
            hook(method).intercept(ClipboardAccessAllowedHooker())
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook clipboardAccessAllowed", it)
        }.onSuccess {
            log(Log.INFO, TAG, "FC: hooked clipboardAccessAllowed")
            hookNotification(clipboardServiceClass, classLoader)
        }
    }

    private fun hookNotification(
        clipboardServiceClass: Class<*>,
        classLoader: ClassLoader
    ) {
        val clipboardClass = runCatching {
            classLoader.loadClass("com.android.server.clipboard.ClipboardService\$Clipboard")
        }.getOrNull() ?: return

        runCatching {
            val method = clipboardServiceClass.getDeclaredMethod(
                "showAccessNotificationLocked",
                String::class.java,
                Int::class.java,
                Int::class.java,
                clipboardClass
            )
            hook(method).intercept(ClipboardNotificationHooker())
        }.onFailure {
            runCatching {
                val method = clipboardServiceClass.getDeclaredMethod(
                    "showAccessNotificationLocked",
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                    clipboardClass,
                    Int::class.java
                )
                hook(method).intercept(ClipboardNotificationHooker())
            }.onFailure {
                log(Log.ERROR, TAG, "Failed to hook showAccessNotificationLocked", it)
            }.onSuccess {
                log(Log.INFO, TAG, "FC: hooked showAccessNotificationLocked")
            }
        }.onSuccess {
            log(Log.INFO, TAG, "FC: hooked showAccessNotificationLocked")
        }
    }

    private inner class ClipboardAccessAllowedHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val packageName = chain.getArg(1) as String
            if (settings.isEnabled(packageName)) {
                return true
            }
            return chain.proceed()
        }
    }

    private inner class ClipboardNotificationHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            val packageName = chain.getArg(0) as String
            if (settings.isEnabled(packageName)) {
                return null
            }
            return chain.proceed()
        }
    }

    companion object {
        private const val TAG = "FuckClip"
    }
}
