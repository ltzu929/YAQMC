package org.yaqmc.android.nativeui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.yaqmc.android.OAuthActivity
import org.yaqmc.android.core.DeepLinkInbox
import org.yaqmc.android.core.DeepLinkManager
import org.yaqmc.android.core.MobileLoginOwner
import org.yaqmc.android.core.MobileLoginPolicy
import org.yaqmc.android.media.PlaybackService
import org.yaqmc.android.R

@UnstableApi
class NativeActivity : ComponentActivity() {
    private val model: NativeViewModel by viewModels()
    private val deepLinks = DeepLinkManager()
    private var links: AutoCloseable? = null
    private val oauth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.finishOAuth(if (it.resultCode == Activity.RESULT_OK) it.data?.dataString else null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinks.accept(intent)
        links = DeepLinkInbox.subscribe { target ->
            lifecycleScope.launch {
                if (target.providerId == "qqmusic") model.navigate(NativeRoute("song", target.entityId))
                else model.notify(R.string.native_unsupported_provider)
            }
        }
        setContent {
            NativeApp(model, login = ::login, share = ::share, reportDrawn = ::reportFullyDrawn)
        }
    }

    override fun onStart() {
        super.onStart()
        PlaybackService.ensureStarted(this)
        model.observe()
    }
    override fun onResume() { super.onResume(); MobileLoginOwner.lifecycle("foreground") }
    override fun onPause() { MobileLoginOwner.lifecycle("background"); super.onPause() }
    override fun onStop() { model.stopObserving(); super.onStop() }
    override fun onDestroy() { links?.close(); super.onDestroy() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); deepLinks.accept(intent) }

    private fun login(method: String) {
        model.startLogin(method, resources.configuration.smallestScreenWidthDp,
            oauth = { prepared ->
                val hosts = prepared.optJSONArray("navigationAllowlist")
                val prefix = prepared.optJSONObject("callbackMatcher")?.text("urlPrefix").orEmpty()
                if (hosts == null || hosts.length() == 0 || prefix.isBlank()) throw CoreFailure("native.login.config", "")
                oauth.launch(Intent(this, OAuthActivity::class.java).apply {
                    putExtra(OAuthActivity.EXTRA_LANGUAGE, application.withNativeLanguage(model.preferences.value).resources.configuration.locales[0].toLanguageTag())
                    putExtra(OAuthActivity.EXTRA_URL, prepared.text("url"))
                    putExtra(OAuthActivity.EXTRA_MOBILE_URL, prepared.text("mobileUrl"))
                    putStringArrayListExtra(OAuthActivity.EXTRA_NAVIGATION_ALLOWLIST, ArrayList((0 until hosts.length()).map { hosts.getString(it) }))
                    putExtra(OAuthActivity.EXTRA_EXTERNAL_NAVIGATION_RULES, prepared.optJSONArray("externalNavigationRules")?.toString() ?: "[]")
                    putExtra(OAuthActivity.EXTRA_CALLBACK_PREFIX, prefix)
                })
            },
            mobile = { authorization ->
                val uri = Uri.Builder().scheme("qqmusic").authority("qq.com").appendPath("ui").appendPath("openUrl")
                    .appendQueryParameter("p", JSONObject().put("url", authorization).toString()).build()
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).setPackage(MobileLoginPolicy.QQ_MUSIC_PACKAGE))
                }.onFailure { model.notify(R.string.native_qq_unavailable) }
            },
        )
    }

    private fun share(track: Track) = model.action {
        val target = model.core.objectResult("catalog_share_song", NativeViewModel.provider(track.providerId).put("id", track.id))
        val text = listOf("${track.title.ifBlank { model.localizedString(R.string.native_unknown_song) }} — ${track.artists}", target.text("canonicalHttpsUrl")).filter(String::isNotBlank).joinToString("\n")
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, model.localizedString(R.string.native_share_song)))
    }
}
