package com.cocro

import androidx.compose.ui.window.ComposeUIViewController
import com.cocro.network.http.AuthApiClient
import com.cocro.network.http.SessionApiClient
import com.cocro.network.http.buildHttpClient
import com.cocro.network.stomp.StompClient
import com.cocro.session.AppViewModel
import com.cocro.ui.App

// BFF base URLs — override via Xcode build settings or xcconfig for prod
private val httpBaseUrl = "http://localhost:8080"
private val wsBaseUrl = "ws://localhost:8080"

private val viewModel = AppViewModel(
    authApiClient = AuthApiClient(buildHttpClient(), httpBaseUrl),
    sessionApiClient = SessionApiClient(buildHttpClient(), httpBaseUrl),
    stompClientFactory = { StompClient(buildHttpClient()) },
    wsBaseUrl = wsBaseUrl,
)

fun MainViewController() = ComposeUIViewController {
    App(viewModel)
}
