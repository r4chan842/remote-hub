package app.remotehub.data

import io.ktor.client.engine.HttpClientEngine

expect fun platformHttpEngine(): HttpClientEngine
