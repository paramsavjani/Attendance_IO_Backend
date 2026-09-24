package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Serves the system prompts: one for signed-in students, one for the public demo, which has no
 * access to anybody's attendance and says so.
 *
 * When the matching `…-prompt-path` property points at a file, that file is re-read whenever its
 * modification time changes, so wording can be tuned on a running instance without a build.
 * Otherwise the prompt bundled under `resources/agent/` is used.
 */
@Component
class AgentSystemPromptLoader(
    private val properties: AgentProperties
) {
    private val logger = LoggerFactory.getLogger(AgentSystemPromptLoader::class.java)

    private val bundled = ConcurrentHashMap<String, String>()
    private val overrides = ConcurrentHashMap<String, Override>()

    private class Override(@Volatile var text: String, @Volatile var mtime: Long)

    /** The prompt for a signed-in student. */
    fun load(): String = resolve("agent/system-prompt.md", properties.systemPromptPath)

    /** The prompt for an anonymous visitor on the public demo. */
    fun loadPublic(): String = resolve("agent/public-system-prompt.md", properties.publicSystemPromptPath)

    /** The prompt for [caller], whoever they are. */
    fun loadFor(caller: AgentCaller): String = if (caller.isPublic) loadPublic() else load()

    private fun resolve(resource: String, overridePath: String): String {
        val path = overridePath.trim().takeIf { it.isNotBlank() }?.let(Path::of) ?: return bundled(resource)
        return try {
            val mtime = Files.getLastModifiedTime(path).toMillis()
            val current = overrides[resource]
            if (current != null && current.mtime == mtime) {
                current.text
            } else {
                Files.readString(path).also { text ->
                    overrides[resource] = Override(text, mtime)
                    logger.info("agent=PROMPT_RELOADED path={} bytes={}", path, text.length)
                }
            }
        } catch (e: Exception) {
            logger.warn("agent=PROMPT_FALLBACK path={} reason={}", path, e.message)
            overrides[resource]?.text ?: bundled(resource)
        }
    }

    private fun bundled(resource: String): String = bundled.computeIfAbsent(resource) {
        ClassPathResource(it).inputStream.bufferedReader().use { reader -> reader.readText() }
    }
}
