package com.lightphone.tide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewModelScope
import com.lightphone.tide.protocol.AuthSnapshot
import com.lightphone.tide.protocol.AuthState
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val tideClient: TideClient = BinderTideClient,
) : LightViewModel<Unit>() {
    private val _authState = MutableStateFlow<AuthSnapshot?>(null)
    val authState = _authState.asStateFlow()

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        viewModelScope.launch {
            _authState.value = tideClient.authSnapshot()
        }
    }

    fun loginActivityComponent(onComponent: (String) -> Unit) {
        viewModelScope.launch {
            tideClient.loginActivity()?.componentName?.let(onComponent)
        }
    }
}

@InitialScreen
class HomeScreen(sealedActivity: SealedLightActivity) : LightScreen<Unit, HomeViewModel>(sealedActivity) {
    override val viewModelClass: Class<HomeViewModel>
        get() = HomeViewModel::class.java

    override fun createViewModel(): HomeViewModel = HomeViewModel()

    @Composable
    override fun Content() {
        val state by viewModel.authState.collectAsState()
        val colors by LightThemeController.colors.collectAsState()

        LightTheme(colors = colors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                LightTopBar(center = LightTopBarCenter.Text("Tide"))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 2f.gridUnitsAsDp()),
                    contentAlignment = Alignment.Center,
                ) {
                    LightText(
                        text = statusMessage(state),
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                    )
                }
                if (state?.canSignIn == true) {
                    LightBottomBar(
                        items = listOf(
                            LightBarButton.Text(
                                text = "SIGN IN",
                                onClick = {
                                    viewModel.loginActivityComponent(::startServerActivity)
                                },
                            ),
                        ),
                    )
                }
            }
        }
    }

    private fun statusMessage(state: AuthSnapshot?): String = when (state?.state) {
        null -> "Checking connection with Tide server…"
        AuthState.MissingConfiguration ->
            "Add your TIDAL developer credentials to local.properties, then rebuild Tide."
        AuthState.Unauthenticated -> "Sign in to connect your TIDAL account."
        AuthState.Authenticating -> "Finishing sign in…"
        AuthState.Authenticated -> "Connected to TIDAL."
        AuthState.Error -> state.errorMessage ?: "TIDAL connection failed."
    }
}
