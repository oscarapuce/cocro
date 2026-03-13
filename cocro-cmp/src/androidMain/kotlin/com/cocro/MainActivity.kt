package com.cocro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cocro.network.http.AuthApiClient
import com.cocro.network.http.SessionApiClient
import com.cocro.network.http.buildHttpClient
import com.cocro.network.stomp.StompClient
import com.cocro.session.AppViewModel
import com.cocro.ui.App

class MainActivity : ComponentActivity() {

    // BFF base URLs — override via local.properties or build config for prod
    private val httpBaseUrl = "http://10.0.2.2:8080"   // localhost in Android emulator
    private val wsBaseUrl = "ws://10.0.2.2:8080"

    private val httpClient = buildHttpClient()

    private val viewModel = AppViewModel(
        authApiClient = AuthApiClient(httpClient, httpBaseUrl),
        sessionApiClient = SessionApiClient(httpClient, httpBaseUrl),
        stompClientFactory = { StompClient(buildHttpClient()) },
        wsBaseUrl = wsBaseUrl,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App(viewModel) }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onCleared()
    }
}
