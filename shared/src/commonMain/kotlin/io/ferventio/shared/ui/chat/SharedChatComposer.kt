package io.ferventio.shared.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import io.ferventio.app.domain.ChatChannel
import io.ferventio.app.domain.ChatMessage
import io.ferventio.app.domain.ConfirmedModerationCommand
import io.ferventio.app.domain.ComposerAutocomplete
import io.ferventio.app.domain.ComposerEmoteVisuals
import io.ferventio.app.domain.CustomCommandComposerResolution
import io.ferventio.app.domain.CustomCommandComposerResolver
import io.ferventio.app.domain.CustomCommandContext
import io.ferventio.app.domain.CustomCommandExecutionPlan
import io.ferventio.app.domain.CustomCommandReply
import io.ferventio.app.domain.CustomCommandRisk
import io.ferventio.app.domain.CustomCommandRuntimeContext
import io.ferventio.app.domain.CustomCommandUser
import io.ferventio.app.domain.NukePreviewConfig
import io.ferventio.app.domain.ThirdPartyEmoteAsset
import io.ferventio.shared.chat.TwitchChatMessageScopeException
import io.ferventio.shared.generated.resources.Res
import io.ferventio.shared.generated.resources.chat_command_unavailable
import io.ferventio.shared.generated.resources.chat_custom_command_cancel
import io.ferventio.shared.generated.resources.chat_custom_command_confirm_action
import io.ferventio.shared.generated.resources.chat_custom_command_confirm_body
import io.ferventio.shared.generated.resources.chat_custom_command_confirm_title
import io.ferventio.shared.generated.resources.chat_custom_command_expanded_too_long
import io.ferventio.shared.generated.resources.chat_composer_placeholder
import io.ferventio.shared.generated.resources.chat_emotes
import io.ferventio.shared.generated.resources.chat_message_too_long
import io.ferventio.shared.generated.resources.chat_read_only_sign_in_to_send
import io.ferventio.shared.generated.resources.chat_reply_cancel
import io.ferventio.shared.generated.resources.chat_replying_to
import io.ferventio.shared.generated.resources.chat_send
import io.ferventio.shared.generated.resources.chat_send_failed
import io.ferventio.shared.generated.resources.chat_sending
import io.ferventio.shared.generated.resources.chat_write_scope_required
import io.ferventio.shared.runtime.LocalFerventioRuntimeState
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private const val MAX_CHAT_MESSAGE_LENGTH = 500
private const val WRITE_CHAT_SCOPE = "user:write:chat"

@Composable
fun SharedChatComposer(
    channel: ChatChannel,
    replyTarget: ChatMessage?,
    onCancelReply: () -> Unit,
    onSent: () -> Unit,
    onUserCardCommand: (String) -> Boolean = { false },
    onModerationCommand: (ConfirmedModerationCommand) -> Boolean = { false },
    onNukeCommand: (NukePreviewConfig) -> Boolean = { false },
    emotes: List<ThirdPartyEmoteAsset> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val runtime = LocalFerventioRuntimeState.current
    val authentication = runtime.authentication.state.authentication

    if (authentication == null) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(
                text = stringResource(Res.string.chat_read_only_sign_in_to_send),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val preferences = runtime.settings.preferences
    val rateLimit = runtime.chat.rateLimit(channel.id)
    val localUiPreferences = runtime.localUiPreferences
    val hasWriteScope = authentication.accessLease?.session?.scopes?.contains(WRITE_CHAT_SCOPE) == true
    val draft = localUiPreferences.draft(channel.id)
    val sentMessageHistory = localUiPreferences.sentMessageHistory(channel.id)
    var historyIndex by rememberSaveable(channel.id) { mutableStateOf(-1) }
    var historyScratch by rememberSaveable(channel.id) { mutableStateOf("") }
    var autocompleteIndex by rememberSaveable(channel.id) { mutableStateOf(0) }
    var sending by remember(channel.id) { mutableStateOf(false) }
    var errorMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var pendingCustomCommandPlan by remember(channel.id) {
        mutableStateOf<CustomCommandExecutionPlan?>(null)
    }
    var emotePickerVisible by rememberSaveable(channel.id) { mutableStateOf(false) }
    val trimmed = draft.trim()
    val tooLong = trimmed.length > MAX_CHAT_MESSAGE_LENGTH
    val canSend = hasWriteScope && trimmed.isNotEmpty() && !tooLong && !sending
    val scopeRequiredText = stringResource(Res.string.chat_write_scope_required)
    val sendFailedFormat = stringResource(Res.string.chat_send_failed, "%s")
    val commandUnavailableText = stringResource(Res.string.chat_command_unavailable)
    val customCommandExpandedTooLongText =
        stringResource(Res.string.chat_custom_command_expanded_too_long)
    val currentUserId = authentication.accessLease?.session?.userId
    val channelMessages = runtime.chat.messages(channel.id)
    val suggestions = remember(draft, channelMessages, emotes, currentUserId) {
        ComposerAutocomplete.suggestions(
            input = draft,
            messages = channelMessages,
            profilesById = emptyMap(),
            catalog = emotes,
            recentEmoteKeys = emptyList(),
            favoriteEmoteKeys = emptySet(),
            currentUserId = currentUserId,
            limit = 8,
        )
    }
    val emoteIndex = remember(emotes) { ComposerEmoteVisuals.buildIndex(emotes) }
    val composerRichText = remember(draft, emoteIndex, preferences.showComposerEmoteImages) {
        if (preferences.showComposerEmoteImages) {
            buildSharedComposerRichText(draft, emoteIndex)
        } else {
            null
        }
    }
    val composerVisualTransformation = remember(composerRichText) {
        composerRichText?.let(::SharedComposerVisualTransformation) ?: VisualTransformation.None
    }

    fun updateDraft(value: String, resetHistory: Boolean = true) {
        localUiPreferences.setDraft(channel.id, value)
        if (resetHistory) {
            historyIndex = -1
            historyScratch = ""
        }
        autocompleteIndex = 0
    }

    fun applySuggestion(suggestion: io.ferventio.app.domain.ComposerSuggestion) {
        updateDraft(ComposerAutocomplete.applySuggestion(draft, suggestion))
        errorMessage = null
    }

    fun moveThroughHistory(older: Boolean) {
        if (sentMessageHistory.isEmpty()) return
        if (older) {
            if (historyIndex < 0) historyScratch = draft
            val nextIndex = (historyIndex + 1).coerceAtMost(sentMessageHistory.lastIndex)
            historyIndex = nextIndex
            localUiPreferences.setDraft(channel.id, sentMessageHistory[nextIndex])
        } else {
            if (historyIndex < 0) return
            val nextIndex = historyIndex - 1
            historyIndex = nextIndex
            localUiPreferences.setDraft(
                channel.id,
                if (nextIndex >= 0) sentMessageHistory[nextIndex] else historyScratch,
            )
        }
        autocompleteIndex = 0
        errorMessage = null
    }

    fun finishLocalSubmission(historyText: String) {
        localUiPreferences.recordSentMessage(channel.id, historyText)
        localUiPreferences.setDraft(channel.id, "")
        historyIndex = -1
        historyScratch = ""
        autocompleteIndex = 0
        emotePickerVisible = false
        onSent()
    }

    fun dispatchResolvedSubmission(
        resolvedText: String,
        historyText: String,
        customPlan: CustomCommandExecutionPlan? = null,
    ) {
        if (resolvedText.length > MAX_CHAT_MESSAGE_LENGTH) {
            errorMessage = customCommandExpandedTooLongText
            return
        }
        when (val submission = routeSharedComposerSubmission(resolvedText)) {
            is SharedComposerSubmission.Error -> {
                errorMessage = submission.message
                return
            }
            is SharedComposerSubmission.UserCard -> {
                errorMessage = null
                if (onUserCardCommand(submission.login)) {
                    finishLocalSubmission(historyText)
                } else {
                    errorMessage = commandUnavailableText
                }
                return
            }
            is SharedComposerSubmission.Moderation -> {
                errorMessage = null
                if (onModerationCommand(submission.command)) {
                    finishLocalSubmission(historyText)
                } else {
                    errorMessage = commandUnavailableText
                }
                return
            }
            is SharedComposerSubmission.Nuke -> {
                errorMessage = null
                if (!onNukeCommand(submission.config)) {
                    errorMessage = commandUnavailableText
                }
                return
            }
            is SharedComposerSubmission.Send -> {
                if (
                    customPlan?.risk == CustomCommandRisk.MODERATION ||
                    customPlan?.risk == CustomCommandRisk.MASS_MODERATION
                ) {
                    errorMessage = commandUnavailableText
                    return
                }
            }
        }

        val replyParentMessageId = replyTarget
            ?.serverMessageId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: replyTarget?.id
        val wireText = (routeSharedComposerSubmission(resolvedText) as SharedComposerSubmission.Send).text
        sending = true
        errorMessage = null
        scope.launch {
            try {
                runtime.chatMessages.send(
                    authentication = authentication,
                    channel = channel,
                    message = wireText,
                    replyParentMessageId = replyParentMessageId,
                )
                finishLocalSubmission(historyText)
            } catch (_: TwitchChatMessageScopeException) {
                errorMessage = scopeRequiredText
            } catch (error: Throwable) {
                errorMessage = sendFailedFormat.replace(
                    "%s",
                    error.message.orEmpty().ifBlank { "Twitch error" },
                )
            } finally {
                sending = false
            }
        }
    }

    fun submit() {
        if (!canSend) return
        val outgoingText = trimmed
        val session = authentication.accessLease?.session
        if (session == null) {
            errorMessage = commandUnavailableText
            return
        }
        val customReply = replyTarget?.let { target ->
            CustomCommandReply(
                messageId = target.serverMessageId
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
                    ?: target.id,
                user = CustomCommandUser(
                    id = target.userId,
                    login = target.userLogin,
                    displayName = target.userDisplayName.ifBlank { target.userLogin },
                ),
                text = target.text,
            )
        }
        val customContext = CustomCommandRuntimeContext(
            base = CustomCommandContext(
                channelName = channel.displayName.ifBlank { channel.login },
                channelId = channel.id,
                myName = session.login,
                myId = session.userId,
            ),
            selectedUser = customReply?.user,
            reply = customReply,
            clipboardText = clipboard.getText()?.text,
        )
        when (
            val resolution = CustomCommandComposerResolver.resolve(
                input = outgoingText,
                commands = runtime.settings.customCommands,
                context = customContext,
            )
        ) {
            is CustomCommandComposerResolution.Error -> {
                errorMessage = resolution.message
            }
            is CustomCommandComposerResolution.PassThrough -> {
                dispatchResolvedSubmission(
                    resolvedText = resolution.text,
                    historyText = outgoingText,
                )
            }
            is CustomCommandComposerResolution.Planned -> {
                val plan = resolution.plan
                if (plan.expandedText.length > MAX_CHAT_MESSAGE_LENGTH) {
                    errorMessage = customCommandExpandedTooLongText
                } else if (plan.requiresPreview) {
                    dispatchResolvedSubmission(
                        resolvedText = plan.expandedText,
                        historyText = plan.expandedText,
                        customPlan = plan,
                    )
                } else if (plan.requiresConfirmation) {
                    errorMessage = null
                    pendingCustomCommandPlan = plan
                } else {
                    dispatchResolvedSubmission(
                        resolvedText = plan.expandedText,
                        historyText = plan.expandedText,
                        customPlan = plan,
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rateLimit?.let { currentRateLimit ->
            SharedChatRateLimitBanner(
                rateLimit = currentRateLimit,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }

        replyTarget?.let { target ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.large,
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                Res.string.chat_replying_to,
                                target.userDisplayName.ifBlank { target.userLogin },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = target.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f),
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = onCancelReply, enabled = !sending) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(Res.string.chat_reply_cancel),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { value ->
                            updateDraft(value.take(MAX_CHAT_MESSAGE_LENGTH))
                            errorMessage = null
                        },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) {
                                    return@onPreviewKeyEvent false
                                }
                                when (event.key) {
                                    Key.DirectionDown -> {
                                        if (suggestions.isNotEmpty()) {
                                            autocompleteIndex =
                                                (autocompleteIndex + 1).coerceAtMost(suggestions.lastIndex)
                                        } else {
                                            moveThroughHistory(older = false)
                                        }
                                        true
                                    }
                                    Key.DirectionUp -> {
                                        if (suggestions.isNotEmpty()) {
                                            autocompleteIndex =
                                                (autocompleteIndex - 1).coerceAtLeast(0)
                                        } else {
                                            moveThroughHistory(older = true)
                                        }
                                        true
                                    }
                                    Key.Tab -> suggestions
                                        .getOrNull(autocompleteIndex)
                                        ?.let {
                                            applySuggestion(it)
                                            true
                                        }
                                        ?: false
                                    Key.Enter -> when {
                                        suggestions.isNotEmpty() && !event.isShiftPressed -> {
                                            suggestions.getOrNull(autocompleteIndex)?.let(::applySuggestion)
                                            true
                                        }
                                        preferences.sendOnEnter && !event.isShiftPressed -> {
                                            submit()
                                            true
                                        }
                                        else -> false
                                    }
                                    else -> false
                                }
                            },
                        enabled = !sending,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = if (composerRichText == null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                Color.Transparent
                            },
                        ),
                        visualTransformation = composerVisualTransformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (preferences.sendOnEnter) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = if (preferences.sendOnEnter) {
                            KeyboardActions(onSend = { submit() })
                        } else {
                            KeyboardActions()
                        },
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            Res.string.chat_composer_placeholder,
                                            channel.displayName,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                    )
                                } else {
                                    composerRichText?.let { richText ->
                                        BasicText(
                                            text = richText.annotatedText,
                                            inlineContent = richText.inlineContent,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurface,
                                            ),
                                            maxLines = 4,
                                        )
                                    }
                                }
                                innerTextField()
                            }
                        },
                    )
                    IconButton(
                        onClick = { emotePickerVisible = !emotePickerVisible },
                        enabled = !sending && hasWriteScope,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.InsertEmoticon,
                            contentDescription = stringResource(Res.string.chat_emotes),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            FilledIconButton(
                onClick = { submit() },
                enabled = canSend,
                modifier = Modifier.size(44.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
                ),
            ) {
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(Res.string.chat_send),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (suggestions.isNotEmpty() && !emotePickerVisible) {
            SharedInlineComposerAutocomplete(
                suggestions = suggestions,
                selectedIndex = autocompleteIndex,
                onSelect = ::applySuggestion,
            )
        }

        when {
            !hasWriteScope -> Text(
                text = scopeRequiredText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            tooLong -> Text(
                text = stringResource(Res.string.chat_message_too_long, trimmed.length),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            sending -> Text(
                text = stringResource(Res.string.chat_sending),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        errorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (emotePickerVisible) {
            SharedEmotePickerPanel(
                emotes = emotes,
                onSelect = { asset ->
                    updateDraft(appendEmoteCode(draft, asset.code))
                    errorMessage = null
                },
                onDismiss = { emotePickerVisible = false },
            )
        }
    }

    pendingCustomCommandPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { pendingCustomCommandPlan = null },
            title = {
                Text(stringResource(Res.string.chat_custom_command_confirm_title))
            },
            text = {
                Text(
                    stringResource(
                        Res.string.chat_custom_command_confirm_body,
                        plan.expandedText,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCustomCommandPlan = null
                        dispatchResolvedSubmission(
                            resolvedText = plan.expandedText,
                            historyText = plan.expandedText,
                            customPlan = plan,
                        )
                    },
                ) {
                    Text(stringResource(Res.string.chat_custom_command_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCustomCommandPlan = null }) {
                    Text(stringResource(Res.string.chat_custom_command_cancel))
                }
            },
        )
    }
}

private fun appendEmoteCode(input: String, code: String): String = buildString {
    append(input.trimEnd())
    if (isNotEmpty()) append(' ')
    append(code)
    append(' ')
}


private data class SharedComposerRichText(
    val source: String,
    val visualText: String,
    val annotatedText: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val offsetMapping: OffsetMapping,
)

private class SharedComposerVisualTransformation(
    private val richText: SharedComposerRichText,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        if (text.text == richText.source) {
            TransformedText(AnnotatedString(richText.visualText), richText.offsetMapping)
        } else {
            TransformedText(text, OffsetMapping.Identity)
        }
}

private fun buildSharedComposerRichText(
    input: String,
    index: ComposerEmoteVisuals.Index,
): SharedComposerRichText? {
    val matches = ComposerEmoteVisuals.findMatches(input, index)
    if (matches.isEmpty()) return null

    val visual = StringBuilder(input.length)
    val originalToTransformed = IntArray(input.length + 1)
    val transformedToOriginal = mutableListOf(0)
    val inline = linkedMapOf<String, InlineTextContent>()
    var originalIndex = 0
    var transformedIndex = 0

    val annotated = buildAnnotatedString {
        matches.forEachIndexed { matchIndex, match ->
            while (originalIndex < match.start) {
                val character = input[originalIndex]
                append(character)
                visual.append(character)
                originalIndex += 1
                transformedIndex += 1
                originalToTransformed[originalIndex] = transformedIndex
                transformedToOriginal += originalIndex
            }

            val inlineId = "shared-composer-emote-" + matchIndex + "-" +
                match.asset.provider + ":" + match.asset.id
            appendInlineContent(inlineId, SHARED_COMPOSER_EMOTE_PLACEHOLDER.toString())
            visual.append(SHARED_COMPOSER_EMOTE_PLACEHOLDER)
            inline[inlineId] = InlineTextContent(
                placeholder = Placeholder(
                    width = 1.05.em,
                    height = 1.05.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) {
                AsyncImage(
                    model = match.asset.bestComposerImageUrl(),
                    contentDescription = match.asset.code,
                    modifier = Modifier.size(20.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            transformedIndex += 1
            originalToTransformed[match.start] = transformedIndex - 1
            for (offset in (match.start + 1)..match.endExclusive) {
                originalToTransformed[offset] = transformedIndex
            }
            transformedToOriginal += match.endExclusive
            originalIndex = match.endExclusive
        }

        while (originalIndex < input.length) {
            val character = input[originalIndex]
            append(character)
            visual.append(character)
            originalIndex += 1
            transformedIndex += 1
            originalToTransformed[originalIndex] = transformedIndex
            transformedToOriginal += originalIndex
        }
    }

    val mapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int =
            originalToTransformed[offset.coerceIn(0, originalToTransformed.lastIndex)]

        override fun transformedToOriginal(offset: Int): Int =
            transformedToOriginal[offset.coerceIn(0, transformedToOriginal.lastIndex)]
    }
    return SharedComposerRichText(
        source = input,
        visualText = visual.toString(),
        annotatedText = annotated,
        inlineContent = inline,
        offsetMapping = mapping,
    )
}

private fun ThirdPartyEmoteAsset.bestComposerImageUrl(): String = when {
    imageUrl2x.isNotBlank() -> imageUrl2x
    imageUrl1x.isNotBlank() -> imageUrl1x
    else -> imageUrl3x
}

private const val SHARED_COMPOSER_EMOTE_PLACEHOLDER = '\uFFFC'
