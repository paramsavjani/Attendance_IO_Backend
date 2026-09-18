package com.attendanceio.api.external.langfuse

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Langfuse credentials and endpoint (`app.langfuse.*`), env-backed only. When any of them is
 * blank, [isConfigured] is false and [LangfuseClient] is a no-op: tracing is observability, never
 * a requirement for the assistant to answer.
 */
@ConfigurationProperties(prefix = "app.langfuse")
data class LangfuseProperties(
    val host: String = "",
    val publicKey: String = "",
    val secretKey: String = "",
    /** Shown as the trace environment in the UI, e.g. "prod" / "dev". */
    val environment: String = ""
) {
    fun isConfigured(): Boolean = host.isNotBlank() && publicKey.isNotBlank() && secretKey.isNotBlank()
}
