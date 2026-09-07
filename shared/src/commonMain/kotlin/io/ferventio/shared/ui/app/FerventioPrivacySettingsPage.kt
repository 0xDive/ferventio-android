package io.ferventio.shared.ui.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.ferventio.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FerventioPrivacySettingsPage(
    info: FerventioAboutInfo,
    platformInfo: FerventioPrivacyPlatformInfo,
) {
    val uriHandler = LocalUriHandler.current
    val operatorName = info.privacyOperatorName.trim().ifBlank {
        stringResource(Res.string.privacy_operator_fallback)
    }
    val privacyContact = info.privacyContact.trim().ifBlank {
        stringResource(Res.string.privacy_contact_fallback)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PrivacySection(title = stringResource(Res.string.privacy_scope_title)) {
            PrivacyParagraph(
                stringResource(
                    Res.string.privacy_scope_operator,
                    operatorName,
                    privacyContact,
                ),
            )
            PrivacyParagraph(stringResource(Res.string.privacy_scope_account))
        }

        PrivacySection(title = stringResource(Res.string.privacy_local_title)) {
            PrivacyParagraph(stringResource(Res.string.privacy_local_content))
            PrivacyParagraph(
                stringResource(
                    when (platformInfo.platform) {
                        FerventioPrivacyPlatform.IOS -> Res.string.privacy_local_credentials_ios
                        FerventioPrivacyPlatform.ANDROID -> Res.string.privacy_local_credentials_android
                        FerventioPrivacyPlatform.OTHER -> Res.string.privacy_local_credentials_generic
                    },
                ),
            )
            PrivacyParagraph(
                stringResource(
                    when (platformInfo.crashReporting) {
                        FerventioPrivacyCrashReporting.NONE -> Res.string.privacy_crash_none
                        FerventioPrivacyCrashReporting.LOCAL_ONLY -> Res.string.privacy_crash_local_only
                        FerventioPrivacyCrashReporting.FIREBASE_CRASHLYTICS -> Res.string.privacy_crash_firebase
                        FerventioPrivacyCrashReporting.OTHER -> Res.string.privacy_crash_other
                    },
                ),
            )
        }

        PrivacySection(title = stringResource(Res.string.privacy_network_title)) {
            PrivacyParagraph(stringResource(Res.string.privacy_network_twitch))
            PrivacyParagraph(stringResource(Res.string.privacy_network_emotes))
            PrivacyParagraph(
                stringResource(
                    when (platformInfo.pushTransport) {
                        FerventioPrivacyPushTransport.APNS -> Res.string.privacy_push_apns
                        FerventioPrivacyPushTransport.FCM -> Res.string.privacy_push_fcm
                        FerventioPrivacyPushTransport.EMBEDDED_SOCKET -> Res.string.privacy_push_embedded_socket
                        FerventioPrivacyPushTransport.NONE -> Res.string.privacy_push_none
                        FerventioPrivacyPushTransport.OTHER -> Res.string.privacy_push_other
                    },
                ),
            )
            PrivacyParagraph(stringResource(Res.string.privacy_network_server))
        }

        PrivacySection(title = stringResource(Res.string.privacy_sharing_title)) {
            PrivacyParagraph(stringResource(Res.string.privacy_sharing_no_sale))
            PrivacyParagraph(stringResource(Res.string.privacy_sharing_services))
            if (platformInfo.crashReporting == FerventioPrivacyCrashReporting.FIREBASE_CRASHLYTICS) {
                PrivacyParagraph(stringResource(Res.string.privacy_sharing_firebase))
            }
            PrivacyParagraph(stringResource(Res.string.privacy_sharing_export))
        }

        PrivacySection(title = stringResource(Res.string.privacy_retention_title)) {
            PrivacyParagraph(stringResource(Res.string.privacy_retention_local))
            PrivacyParagraph(stringResource(Res.string.privacy_retention_transport))
            PrivacyParagraph(stringResource(Res.string.privacy_retention_server))
        }

        PrivacySection(title = stringResource(Res.string.privacy_controls_title)) {
            PrivacyParagraph(stringResource(Res.string.privacy_controls_sessions))
            PrivacyParagraph(stringResource(Res.string.privacy_controls_local_data))
            PrivacyParagraph(
                stringResource(
                    Res.string.privacy_controls_requests,
                    privacyContact,
                ),
            )
        }

        PrivacySection(title = stringResource(Res.string.privacy_changes_title)) {
            PrivacyParagraph(
                stringResource(
                    Res.string.privacy_changes_effective_date,
                    FERVENTIO_PRIVACY_POLICY_EFFECTIVE_DATE,
                ),
            )
            if (info.privacyPolicyUrl.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { uriHandler.openUri(info.privacyPolicyUrl) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.privacy_open_published_policy))
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun PrivacyParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Normal,
    )
}
