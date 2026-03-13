package com.cocro.network.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

val appJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun buildHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(appJson) }
    install(WebSockets)
    install(Logging) {
        logger = object : Logger {
            override fun log(message: String) = println("[Ktor] $message")
        }
        level = LogLevel.INFO
    }
}
