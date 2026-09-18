package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path

/**
 * Serves the system prompt. When `app.agent.system-prompt-path` points at a file, that file is
 * re-read whenever its modification time changes, so the wording can be tuned on a running
 * instance without a build. Otherwise the prompt bundled under `resources/agent/` is used.
 */
@Component
class AgentSystemPromptLoader(
    private val properties: AgentProperties
) {
    private val logger = LoggerFactory.getLogger(AgentSystemPromptLoader::class.java)

    private val bundled: String by lazy {
        ClassPathResource("agent/system-prompt.md").inputStream.bufferedReader().use { it.readText() }
    }

    @Volatile private var cached: String? = null
    @Volatile private var cachedMtime: Long = -1

    fun load(): String {
        val path = properties.systemPromptPath.trim().takeIf { it.isNotBlank() }?.let(Path::of) ?: return bundled
        return try {
            val mtime = Files.getLastModifiedTime(path).toMillis()
            val current = cached
            if (current != null && mtime == cachedMtime) {
                current
            } else {
                Files.readString(path).also {
                    cached = it
                    cachedMtime = mtime
                    logger.info("agent=PROMPT_RELOADED path={} bytes={}", path, it.length)
                }
            }
        } catch (e: Exception) {
            logger.warn("agent=PROMPT_FALLBACK path={} reason={}", path, e.message)
            cached ?: bundled
        }
    }
}
