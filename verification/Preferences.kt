package android.content

interface SharedPreferences {
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getString(key: String, default: String?): String?
    fun edit(): Editor
    interface Editor { fun putString(key: String, value: String?): Editor; fun apply() }
}

class FakePreferences : SharedPreferences {
    val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
    override fun getString(key: String, default: String?) = values[key] as? String ?: default
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changes = mutableMapOf<String, String?>()
        override fun putString(key: String, value: String?): SharedPreferences.Editor { changes[key] = value; return this }
        override fun apply() { changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value } }
    }
}
