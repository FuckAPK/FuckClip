package org.lyaaz.fuckclip

import android.app.AppOpsManager
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.InvocationTargetException

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
            // Android 14–17 include attribution/device IDs.
            // Hook the terminal overload, not its forwarding overload.
            // AOSP: frameworks/base/services/core/java/com/android/server/clipboard/ClipboardService.java
            val method = clipboardServiceClass.declaredMethods.singleOrNull {
                it.name == "clipboardAccessAllowed" &&
                    it.returnType == Boolean::class.javaPrimitiveType &&
                    it.parameterTypes.contentEquals(arrayOf(
                        Int::class.java, String::class.java, String::class.java,
                        Int::class.java, Int::class.java, Int::class.java, Boolean::class.java
                    ))
            } ?: run {
                log(Log.WARN, TAG, "Skipping unknown clipboardAccessAllowed signature")
                return
            }
            hook(method).intercept(ClipboardAccessAllowedHooker(clipboardServiceClass))
        }.onFailure {
            val level = if (it is ReflectiveOperationException) Log.WARN else Log.ERROR
            log(level, TAG, "Could not hook clipboardAccessAllowed", it)
        }.onSuccess {
            log(Log.INFO, TAG, "FC: hooked clipboardAccessAllowed")
            hookNotification(clipboardServiceClass)
        }
    }

    private fun hookNotification(clipboardServiceClass: Class<*>) {
        runCatching {
            val method = clipboardServiceClass.declaredMethods.singleOrNull {
                if (it.name != "showAccessNotificationLocked") return@singleOrNull false
                val types = it.parameterTypes
                if (types.size !in 4..5 ||
                    types[0] != String::class.java ||
                    types[1] != Int::class.java ||
                    types[2] != Int::class.java
                ) return@singleOrNull false
                val clipboardName = types[3].name
                when {
                    // Android 14–15.
                    types.size == 4 &&
                        clipboardName == "${clipboardServiceClass.name}\$Clipboard" ->
                        it.returnType == Void.TYPE
                    // Android 16 returns void; Android 17 returns whether a toast was shown.
                    types.size == 5 &&
                        clipboardName == "${clipboardServiceClass.name}\$Clipboard" &&
                        types[4] == Int::class.java ->
                        it.returnType == Void.TYPE || it.returnType == Boolean::class.javaPrimitiveType
                    else -> false
                }
            } ?: run {
                log(Log.WARN, TAG, "Skipping unknown showAccessNotificationLocked signature")
                return
            }
            val suppressedResult = if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            hook(method).intercept(ClipboardNotificationHooker(suppressedResult))
            log(Log.INFO, TAG, "FC: hooked showAccessNotificationLocked")
        }.onFailure {
            log(Log.ERROR, TAG, "Failed to hook showAccessNotificationLocked", it)
        }
    }

    private inner class ClipboardAccessAllowedHooker(serviceClass: Class<*>) : Hooker {
        // Resolve metadata once. If a ROM changes these security checks, do not install the bypass.
        private val appOpsField = serviceClass.getDeclaredField("mAppOps").apply { isAccessible = true }
        private val appOpsClass = appOpsField.type
        private val checkPackage = appOpsClass.getMethod("checkPackage", Int::class.java, String::class.java)
        private val checkOp = appOpsClass.getMethod("checkOp", Int::class.java, Int::class.java, String::class.java)
        private val noteOp = appOpsClass.getMethod(
            "noteOp", Int::class.java, Int::class.java, String::class.java,
            String::class.java, String::class.java
        )

        init {
            if (checkPackage.returnType != Void.TYPE ||
                checkOp.returnType != Int::class.java ||
                noteOp.returnType != Int::class.java
            ) throw NoSuchMethodException("Unknown clipboard security-check signature")
        }

        override fun intercept(chain: Chain): Any? {
            val packageName = chain.getArg(1) as? String ?: return chain.proceed()
            // Writes, unselected packages, and unknown operations retain the entire original path.
            if (chain.getArg(0) != OP_READ_CLIPBOARD || !settings.isEnabled(packageName)) {
                return chain.proceed()
            }
            val service = chain.thisObject
            val uid = chain.getArg(3) as Int
            val appOps = appOpsField.get(service) as AppOpsManager
            try {
                // Match AOSP ordering: authenticate first, even if the device is invalid.
                appOps.checkPackage(uid, packageName)
                if (chain.getArg(5) == DEVICE_ID_INVALID) {
                    return false
                }
                // Only the focus/background gate is bypassed. Preserve both AppOps mode and
                // shouldNoteOp: metadata probes must not become clipboard-read audit events.
                val shouldNoteOp = chain.getArg(6) as Boolean
                val mode = if (!shouldNoteOp) {
                    checkOp.invoke(appOps, OP_READ_CLIPBOARD, uid, packageName)
                } else {
                    noteOp.invoke(appOps, OP_READ_CLIPBOARD, uid, packageName, chain.getArg(2), null)
                }
                return mode == MODE_ALLOWED
            } catch (error: InvocationTargetException) {
                // Reflection must not turn SecurityException (or any AppOps failure) into access.
                throw error.targetException
            }
        }
    }

    private inner class ClipboardNotificationHooker(private val suppressedResult: Any?) : Hooker {
        override fun intercept(chain: Chain): Any? {
            val packageName = chain.getArg(0) as? String ?: return chain.proceed()
            if (settings.isEnabled(packageName)) {
                return suppressedResult
            }
            return chain.proceed()
        }
    }

    companion object {
        private const val TAG = "FuckClip"
        // Hidden AppOpsManager constant: proto_logging/stats/enums/app/enums.proto.
        private const val OP_READ_CLIPBOARD = 29
        private const val MODE_ALLOWED = 0
        // Context.DEVICE_ID_INVALID in Android 14–17.
        private const val DEVICE_ID_INVALID = -1
    }
}
