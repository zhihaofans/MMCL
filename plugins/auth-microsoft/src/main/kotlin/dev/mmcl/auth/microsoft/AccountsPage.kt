package dev.mmcl.auth.microsoft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.mmcl.api.ui.AppNavigator
import dev.mmcl.api.ui.LocalLauncherLocale
import dev.mmcl.api.ui.UiGroup
import dev.mmcl.api.ui.UiIcon
import dev.mmcl.api.ui.UiPage
import dev.mmcl.ui.kit.MmclSpacing
import dev.mmcl.ui.kit.PageScaffold
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/**
 * 账户页：配置 client id、开始设备码登录、登出。
 *
 * 登录任务由 [MicrosoftLoginService] 持有（插件作用域），切到别的页面再回来也能看到进度。
 *
 * @param settings 用于保存 client id；为 `null` 时只读
 * @param clientIdKey client id 的设置键
 */
internal class AccountsPage(
    private val service: MicrosoftLoginService,
    private val provider: MicrosoftAuthProvider,
    /** 当前 client ID；由插件读取设置/系统属性，`null` 表示未配置。 */
    private val clientIdProvider: () -> String?,
) : UiPage {
    override val id: String = PAGE_ID
    override val title: String = "账户"
    override val icon: UiIcon = UiIcon.ACCOUNT
    override val group: UiGroup = UiGroup.MAIN
    override val order: Int = 120

    @Composable
    override fun LocalizedTitle(): String = microsoftTexts(LocalLauncherLocale.current).pageTitle

    @Composable
    override fun Content(navigator: AppNavigator) {
        val locale = LocalLauncherLocale.current
        val texts = microsoftTexts(locale)
        val state by service.state.collectAsState()
        val configured = clientIdProvider() != null

        LaunchedEffect(Unit) { service.refresh() }

        PageScaffold(title = texts.pageTitle, subtitle = texts.subtitle) {
            Column(verticalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
                Text(text = texts.clientIdTitle, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (configured) texts.clientIdConfigured else texts.clientIdMissing,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(
                    text = texts.clientIdHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when (val current = state) {
                    is LoginState.SignedIn ->
                        SignedInSection(texts, current, onSignOut = { service.signOut() })

                    is LoginState.WaitingCode ->
                        WaitingSection(
                            texts = texts,
                            challenge = current.challenge,
                            onCancel = { service.cancel() },
                        )

                    LoginState.Exchanging ->
                        Text(text = texts.exchanging, style = MaterialTheme.typography.bodyMedium)

                    is LoginState.Failed -> {
                        Text(
                            text = texts.failurePrefix.format(failureText(current.reason, locale)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        current.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button(onClick = { service.beginLogin() }, enabled = configured) {
                            Text(texts.retry)
                        }
                    }

                    LoginState.Idle -> {
                        Text(
                            text = if (provider.isSignedIn) texts.signedInAs.format(provider.accountOrNull()?.name.orEmpty()) else texts.notSignedIn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { service.beginLogin() }, enabled = configured) {
                            Text(texts.signIn)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SignedInSection(texts: MicrosoftTexts, state: LoginState.SignedIn, onSignOut: () -> Unit) {
        Text(
            text = texts.signedInAs.format(state.account.name),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = state.account.uuid,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onSignOut) { Text(texts.signOut) }
    }

    @Composable
    private fun WaitingSection(
        texts: MicrosoftTexts,
        challenge: DeviceCodeChallenge,
        onCancel: () -> Unit,
    ) {
        var copied by remember { mutableStateOf(false) }
        Text(text = texts.waitingTitle, style = MaterialTheme.typography.titleSmall)
        Text(text = texts.waitingHint, style = MaterialTheme.typography.bodySmall)
        Text(
            text = challenge.userCode,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = challenge.verificationUri,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MmclSpacing.s)) {
            Button(
                onClick = {
                    runCatching { Desktop.getDesktop().browse(URI.create(challenge.verificationUri)) }
                },
            ) { Text(texts.openBrowser) }
            OutlinedButton(
                onClick = {
                    runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard
                            .setContents(StringSelection(challenge.userCode), null)
                    }
                    copied = true
                },
            ) { Text(if (copied) texts.copied else texts.copyCode) }
            OutlinedButton(onClick = onCancel) { Text(texts.cancel) }
        }
    }

    companion object {
        /** 页面 id。 */
        const val PAGE_ID: String = "dev.mmcl.auth.microsoft.accounts"
    }
}
