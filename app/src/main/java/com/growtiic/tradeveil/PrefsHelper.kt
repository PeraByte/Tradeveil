import android.content.Context

class PrefsHelper(context: Context) {
    private val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    companion object {
        @Volatile private var INSTANCE: PrefsHelper? = null

        fun getInstance(context: Context): PrefsHelper {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsHelper(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }

    var isFirstLaunch: Boolean
        get() = sharedPref.getBoolean("is_first_launch", true)
        set(value) = sharedPref.edit().putBoolean("is_first_launch", value).apply()

    var isLoggedIn: Boolean
        get() = sharedPref.getBoolean("is_logged_in", false)
        set(value) = sharedPref.edit().putBoolean("is_logged_in", value).apply()
}