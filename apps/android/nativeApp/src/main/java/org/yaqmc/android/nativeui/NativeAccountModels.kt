package org.yaqmc.android.nativeui

import org.json.JSONObject
import org.yaqmc.android.R

internal data class AccountUi(
    val state: String = "guest", val nickname: String = "", val avatar: String = "",
    val qr: String = "", val attempt: String = "", val canFavorite: Boolean = false,
    val identity: String = "", val entitlement: JSONObject? = null, val revision: Long = 0,
    val capabilities: JSONObject = JSONObject(), val expiresAt: Long? = null,
    val upstreamCode: Int? = null,
) {
    val authenticated: Boolean get() = state == "authenticated"
    val loginPending: Boolean get() = state in setOf("starting-login", "waiting-for-scan", "waiting-for-confirmation")
    val needsLogin: Boolean get() = state in setOf("guest", "session-expired", "reauthentication-required", "expired", "cancelled", "rejected")
    fun canRead(kind: String): Boolean = capabilities.optBoolean(when (kind) {
        "favorites" -> "favoriteRead"; "recent" -> "recentHistoryRead"; else -> "playlistRead"
    })
    fun canWrite(track: Track): Boolean = authenticated && capabilities.optBoolean("playlistWrite") && track.writable

    companion object {
        fun parse(value: JSONObject): AccountUi {
            val profile = value.optJSONObject("profile")
            val capabilities = value.optJSONObject("capabilities") ?: JSONObject()
            return AccountUi(value.text("state", "guest"), profile?.text("nickname").orEmpty(), profile?.text("avatarUrl").orEmpty(),
                value.text("qrImageDataUri"), value.text("attemptId"), capabilities.optBoolean("favoriteWrite"),
                profile?.text("maskedIdentity").orEmpty(), value.optJSONObject("entitlement"), value.optLong("revision"), capabilities,
                if (value.isNull("expiresAtMs")) null else value.optLong("expiresAtMs"),
                if (value.isNull("upstreamCode")) null else value.optInt("upstreamCode"))
        }
    }
}

internal data class PlaylistPickerState(val track: Track, val playlists: List<CatalogItem> = emptyList(), val cursor: String? = null,
    val loaded: Boolean = false, val loading: Boolean = true, val error: NativeText? = null, val stale: Boolean = false)

internal fun JSONObject.canEdit(capability: String): Boolean = optJSONObject("capabilities")?.optBoolean(capability) == true

internal fun validPlaylistTitle(value: String): Boolean {
    val title = value.trim()
    return title.isNotBlank() && title.codePointCount(0, title.length) <= 80 && title.codePoints().noneMatch {
        Character.getType(it) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt())
    }
}

internal fun membershipTier(value: String): Int = when (value) {
    "free" -> R.string.native_tier_free; "green-diamond" -> R.string.native_tier_green
    "super-vip" -> R.string.native_tier_super; else -> R.string.native_unknown
}
internal fun membershipState(value: String): Int = when (value) {
    "active" -> R.string.native_membership_active; "expired" -> R.string.native_membership_expired
    "inactive" -> R.string.native_membership_inactive; else -> R.string.native_unknown
}
internal fun secondaryEntitlement(value: String): Int? = when (value) {
    "luxury-green-diamond" -> R.string.native_secondary_luxury
    "annual-green-diamond" -> R.string.native_secondary_annual
    "annual-luxury-green-diamond" -> R.string.native_secondary_annual_luxury
    "star" -> R.string.native_secondary_star; "annual-star" -> R.string.native_secondary_annual_star
    "eight-platform" -> R.string.native_secondary_eight; "twelve-platform" -> R.string.native_secondary_twelve
    "family" -> R.string.native_secondary_family; "child" -> R.string.native_secondary_child
    "trial" -> R.string.native_secondary_trial; "couple" -> R.string.native_secondary_couple
    "ad-free" -> R.string.native_secondary_ad_free; else -> null
}
