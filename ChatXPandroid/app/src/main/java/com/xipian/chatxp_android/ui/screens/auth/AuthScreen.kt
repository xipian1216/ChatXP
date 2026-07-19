package com.xipian.chatxp_android.ui.screens.auth

import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.theme.ChatSendButton
import com.xipian.chatxp_android.ui.theme.ChatSendButtonOn
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import com.xipian.chatxp_android.ui.token.AuthAgreementTopSpacing
import com.xipian.chatxp_android.ui.token.AuthButtonMinHeight
import com.xipian.chatxp_android.ui.token.AuthContentMaxWidth
import com.xipian.chatxp_android.ui.token.AuthFieldMinHeight
import com.xipian.chatxp_android.ui.token.AuthFieldSpacing
import com.xipian.chatxp_android.ui.token.AuthHeaderSpacing
import com.xipian.chatxp_android.ui.token.AuthHorizontalPadding
import com.xipian.chatxp_android.ui.token.AuthSectionSpacing
import com.xipian.chatxp_android.ui.token.AuthTabHeight
import com.xipian.chatxp_android.ui.token.AuthTopPadding
import com.xipian.chatxp_android.ui.token.IconSize
import com.xipian.chatxp_android.ui.token.Space1
import com.xipian.chatxp_android.ui.token.Space2

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onAction: (AuthAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    val snackbarHostState = remember { SnackbarHostState() }
    val unavailableText = stringResource(R.string.auth_forgot_unavailable)
    LaunchedEffect(uiState.showUnavailableNotice) {
        if (uiState.showUnavailableNotice) {
            snackbarHostState.showSnackbar(unavailableText)
            onAction(AuthAction.NoticeShown)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = AuthContentMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = AuthHorizontalPadding,
                        top = AuthTopPadding,
                        end = AuthHorizontalPadding,
                        bottom = AuthSectionSpacing
                    )
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.auth_back),
                        modifier = Modifier.size(IconSize)
                    )
                }
                Spacer(modifier = Modifier.height(AuthHeaderSpacing))
                Text(
                    text = stringResource(
                        if (uiState.selectedTab == AuthTab.LOGIN) {
                            R.string.auth_login_title
                        } else {
                            R.string.auth_register_title
                        }
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(AuthHeaderSpacing))
                AuthTabs(
                    selectedTab = uiState.selectedTab,
                    enabled = !uiState.isSubmitting,
                    onTabSelected = { onAction(AuthAction.SelectTab(it)) }
                )
                Spacer(modifier = Modifier.height(AuthSectionSpacing))
                AuthForm(
                    uiState = uiState,
                    onAction = onAction
                )
                Spacer(modifier = Modifier.height(AuthSectionSpacing))
                Button(
                    onClick = { onAction(AuthAction.Submit) },
                    enabled = !uiState.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = AuthButtonMinHeight),
                    shape = MaterialTheme.shapes.medium,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = ChatSendButton,
                        contentColor = ChatSendButtonOn
                    )
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IconSize),
                            color = ChatSendButtonOn,
                            strokeWidth = Space1
                        )
                    } else {
                        Text(
                            text = stringResource(
                                if (uiState.selectedTab == AuthTab.LOGIN) {
                                    R.string.auth_login_button
                                } else {
                                    R.string.auth_register_button
                                }
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                if (uiState.selectedTab == AuthTab.LOGIN) {
                    TextButton(
                        onClick = { onAction(AuthAction.ForgotPassword) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = stringResource(R.string.auth_forgot_password),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                uiState.globalError?.let { error ->
                    Text(
                        text = stringResource(error.messageRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Space2)
                    )
                }
                Spacer(modifier = Modifier.height(AuthAgreementTopSpacing))
                Text(
                    text = stringResource(R.string.auth_agreement),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun AuthTabs(
    selectedTab: AuthTab,
    enabled: Boolean,
    onTabSelected: (AuthTab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(AuthTabHeight),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(modifier = Modifier.padding(Space1)) {
            AuthTabItem(
                text = stringResource(R.string.auth_tab_login),
                selected = selectedTab == AuthTab.LOGIN,
                enabled = enabled,
                onClick = { onTabSelected(AuthTab.LOGIN) },
                modifier = Modifier.weight(1f)
            )
            AuthTabItem(
                text = stringResource(R.string.auth_tab_register),
                selected = selectedTab == AuthTab.REGISTER,
                enabled = enabled,
                onClick = { onTabSelected(AuthTab.REGISTER) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AuthTabItem(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shadowElevation = if (selected) Space1 else 0.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AuthForm(uiState: AuthUiState, onAction: (AuthAction) -> Unit) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(AuthFieldSpacing)) {
        if (uiState.selectedTab == AuthTab.REGISTER) {
            AuthTextField(
                value = uiState.displayName,
                onValueChange = { onAction(AuthAction.DisplayNameChanged(it)) },
                label = stringResource(R.string.auth_username_label),
                placeholder = stringResource(R.string.auth_username_placeholder),
                fieldError = uiState.displayNameError,
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )
        }
        AuthTextField(
            value = uiState.email,
            onValueChange = { onAction(AuthAction.EmailChanged(it)) },
            label = stringResource(R.string.auth_email_label),
            placeholder = stringResource(R.string.auth_email_placeholder),
            fieldError = uiState.emailError,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            )
        )
        AuthTextField(
            value = uiState.password,
            onValueChange = { onAction(AuthAction.PasswordChanged(it)) },
            label = stringResource(R.string.auth_password_label),
            placeholder = stringResource(R.string.auth_password_placeholder),
            fieldError = uiState.passwordError,
            enabled = !uiState.isSubmitting,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (uiState.selectedTab == AuthTab.LOGIN) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onAction(AuthAction.Submit)
                }
            ),
            visualTransformation = if (uiState.isPasswordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                PasswordVisibilityButton(
                    visible = uiState.isPasswordVisible,
                    onClick = { onAction(AuthAction.TogglePasswordVisibility) }
                )
            }
        )
        if (uiState.selectedTab == AuthTab.REGISTER) {
            AuthTextField(
                value = uiState.confirmPassword,
                onValueChange = { onAction(AuthAction.ConfirmPasswordChanged(it)) },
                label = stringResource(R.string.auth_confirm_password_label),
                placeholder = stringResource(R.string.auth_confirm_password_placeholder),
                fieldError = uiState.confirmPasswordError,
                enabled = !uiState.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onAction(AuthAction.Submit)
                    }
                ),
                visualTransformation = if (uiState.isConfirmPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    PasswordVisibilityButton(
                        visible = uiState.isConfirmPasswordVisible,
                        onClick = { onAction(AuthAction.ToggleConfirmPasswordVisibility) }
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    fieldError: AuthFieldError?,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val errorText = fieldError?.let { stringResource(it.messageRes) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = Space2)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = {
                Text(text = placeholder, style = MaterialTheme.typography.bodyMedium)
            },
            isError = fieldError != null,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = ChatCorner.Dialog,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AuthFieldMinHeight)
                .semantics {
                    contentDescription = label
                    errorText?.let { error(it) }
                }
        )
        errorText?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = Space2, top = Space1)
            )
        }
    }
}

@Composable
private fun PasswordVisibilityButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(
                if (visible) R.drawable.visibility_off else R.drawable.visibility
            ),
            contentDescription = stringResource(
                if (visible) R.string.auth_hide_password else R.string.auth_show_password
            ),
            modifier = Modifier.size(IconSize)
        )
    }
}

@get:StringRes
private val AuthFieldError.messageRes: Int
    get() = when (this) {
        AuthFieldError.REQUIRED -> R.string.auth_error_required
        AuthFieldError.INVALID_EMAIL -> R.string.auth_error_invalid_email
        AuthFieldError.INVALID_USERNAME -> R.string.auth_error_invalid_username
        AuthFieldError.INVALID_PASSWORD_LENGTH -> R.string.auth_error_password_length
        AuthFieldError.PASSWORD_MISMATCH -> R.string.auth_error_password_mismatch
    }

@get:StringRes
private val AuthGlobalError.messageRes: Int
    get() = when (this) {
        AuthGlobalError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
        AuthGlobalError.EMAIL_ALREADY_REGISTERED -> R.string.auth_error_email_registered
        AuthGlobalError.RATE_LIMITED -> R.string.auth_error_rate_limited
        AuthGlobalError.TRANSITION_BUSY -> R.string.auth_error_transition_busy
        AuthGlobalError.NETWORK -> R.string.auth_error_network
        AuthGlobalError.UNKNOWN -> R.string.auth_error_unknown
    }

@Preview(name = "Auth login light", showBackground = true, showSystemUi = true)
@Composable
private fun AuthLoginLightPreview() {
    AuthScreenPreview(AuthUiState(), darkTheme = false)
}

@Preview(name = "Auth login dark", showBackground = true, showSystemUi = true)
@Composable
private fun AuthLoginDarkPreview() {
    AuthScreenPreview(AuthUiState(), darkTheme = true)
}

@Preview(name = "Auth register light", showBackground = true, showSystemUi = true)
@Composable
private fun AuthRegisterLightPreview() {
    AuthScreenPreview(AuthUiState(selectedTab = AuthTab.REGISTER), darkTheme = false)
}

@Preview(name = "Auth register dark", showBackground = true, showSystemUi = true)
@Composable
private fun AuthRegisterDarkPreview() {
    AuthScreenPreview(AuthUiState(selectedTab = AuthTab.REGISTER), darkTheme = true)
}

@Preview(name = "Auth register small", widthDp = 320, heightDp = 568, showBackground = true)
@Composable
private fun AuthRegisterSmallPreview() {
    AuthScreenPreview(AuthUiState(selectedTab = AuthTab.REGISTER), darkTheme = false)
}

@Composable
private fun AuthScreenPreview(state: AuthUiState, darkTheme: Boolean) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
            AuthScreen(
                uiState = state,
                onAction = {},
                onBack = {},
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
