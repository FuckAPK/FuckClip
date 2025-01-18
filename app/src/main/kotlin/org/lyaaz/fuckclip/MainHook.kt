package org.lyaaz.fuckclip

import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "android") {
            return
        }

        runCatching {
            XposedHelpers.findAndHookMethod(
                "com.android.server.clipboard.ClipboardService",
                lpparam.classLoader,
                "clipboardAccessAllowed",
                Int::class.java,
                String::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Boolean::class.java,
                ClipboardAccessAllowedHook
            )
        }.onFailure {
            XposedBridge.log(it)
        }.onSuccess {
            runCatching {
                XposedHelpers.findAndHookMethod(
                    "com.android.server.clipboard.ClipboardService",
                    lpparam.classLoader,
                    "showAccessNotificationLocked",
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                    "com.android.server.clipboard.ClipboardService\$Clipboard",
                    ClipboardNotificationHook
                )
            }.onFailure {
                runCatching {
                    XposedHelpers.findAndHookMethod(
                        "com.android.server.clipboard.ClipboardService",
                        lpparam.classLoader,
                        "showAccessNotificationLocked",
                        String::class.java,
                        Int::class.java,
                        Int::class.java,
                        "com.android.server.clipboard.ClipboardService\$Clipboard",
                        Int::class.java,
                        ClipboardNotificationHook
                    )
                }.onFailure {
                    XposedBridge.log(it)
                }
            }
        }
    }

    object ClipboardAccessAllowedHook : XC_MethodHook() {

        override fun beforeHookedMethod(param: MethodHookParam) {
            val packageName = param.args[1] as String
            prefs.reload()
            if (settings.isEnabled(packageName)) {
                param.result = true
            }
        }
    }

    object ClipboardNotificationHook : XC_MethodHook() {

        override fun beforeHookedMethod(param: MethodHookParam) {
            val packageName = param.args[0] as String
            if (settings.isEnabled(packageName)) {
                param.result = null
            }
        }
    }

    companion object {
        private val prefs: XSharedPreferences by lazy {
            XSharedPreferences(BuildConfig.APPLICATION_ID)
        }
        private val settings: Settings by lazy {
            Settings.getInstance(prefs)
        }
    }
}
