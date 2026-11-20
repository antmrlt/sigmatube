package app.sigmatube.data.auth

import android.content.Context
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class AccountInfo(val name: String?, val handle: String?, val avatar: String?)

/**
 * Holds the YouTube session cookies captured from the sign-in WebView.
 * They live in app-private storage only (backup is disabled in the manifest).
 */
class AccountManager(context: Context) {

    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    private val _cookies = MutableStateFlow(prefs.getString(KEY_COOKIES, null))
    val cookies: StateFlow<String?> = _cookies

    val isLoggedIn: Boolean get() = _cookies.value?.let { sapisid(it) != null } == true

    fun saveCookies(cookies: String) {
        prefs.edit().putString(KEY_COOKIES, cookies).apply()
        _cookies.value = cookies
    }

    fun logout() {
        prefs.edit().remove(KEY_COOKIES).apply()
        _cookies.value = null
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    companion object {
        private const val KEY_COOKIES = "cookies"

        /** The SAPISID (or its secure variant) cookie is what signs authenticated InnerTube calls. */
        fun sapisid(cookies: String): String? = cookies.split(";")
            .map { it.trim() }
            .firstNotNullOfOrNull { part ->
                val (k, v) = part.split("=", limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
                v.takeIf { k == "SAPISID" || k == "__Secure-3PAPISID" }
            }
    }
}
