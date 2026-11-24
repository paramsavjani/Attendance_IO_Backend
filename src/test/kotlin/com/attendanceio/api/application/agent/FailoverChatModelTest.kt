package com.attendanceio.api.application.agent

import com.attendanceio.api.config.AgentProperties
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.prompt.ChatOptions
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.google.genai.GoogleGenAiChatOptions
import reactor.core.publisher.Flux
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FailoverChatModelTest {
    private val overloaded = RuntimeException("Failed to generate content", RuntimeException("503 . This model is currently experiencing high demand."))

    private fun options(model: String) = GoogleGenAiChatOptions.builder().model(model).build()

    /** Answers with the model's name, unless [behave] returns something else for it. */
    private inner class Fake(val behave: (String) -> Flux<ChatResponse>?) : ChatModel {
        val calls = mutableListOf<String>()
        override fun getOptions(): ChatOptions = options("a")
        private fun answer(m: String) = ChatResponse(listOf(Generation(AssistantMessage(m))))
        override fun call(prompt: Prompt): ChatResponse = stream(prompt).blockLast()!!
        override fun stream(prompt: Prompt): Flux<ChatResponse> {
            val m = prompt.options!!.model!!
            calls += m
            return behave(m) ?: Flux.just(answer(m))
        }
    }

    private fun failover(fake: Fake) = FailoverChatModel(
        fake, listOf("b", "c"), Duration.ofMillis(150), Duration.ofSeconds(60), AgentModelBackoff(AgentProperties())::isRetryable
    )

    private fun FailoverChatModel.ask(model: String = "a") =
        stream(Prompt("hi", options(model))).map { it.result!!.output.text.orEmpty() }.collectList().block(Duration.ofSeconds(5))

    @Test
    fun `an overloaded model is replaced and then tried last`() {
        val fake = Fake { if (it == "a") Flux.error(overloaded) else null }
        val model = failover(fake)
        assertEquals(listOf("b"), model.ask())
        assertTrue(model.primaryIsFailing())
        assertEquals(listOf("b"), model.ask())
        assertEquals(listOf("a", "b", "b"), fake.calls)
    }

    @Test
    fun `a silent model is replaced`() {
        val fake = Fake { if (it != "c") Flux.never() else null }
        assertEquals(listOf("c"), failover(fake).ask())
        assertEquals(listOf("a", "b", "c"), fake.calls)
    }

    @Test
    fun `other errors are not retried on another model`() {
        val fake = Fake { Flux.error(IllegalArgumentException("bad request")) }
        assertFailsWith<IllegalArgumentException> { failover(fake).ask() }
        assertEquals(listOf("a"), fake.calls)
    }

    @Test
    fun `when every model is overloaded the failure still reads as busy`() {
        val fake = Fake { Flux.error(overloaded) }
        val e = assertFailsWith<RuntimeException> { failover(fake).ask() }
        assertTrue(AgentModelBackoff(AgentProperties()).isRetryable(e))
        assertEquals(listOf("a", "b", "c"), fake.calls)
    }

    @Test
    fun `an answer that already started is never replayed on another model`() {
        val fake = Fake { m -> Flux.just(ChatResponse(listOf(Generation(AssistantMessage("part-$m"))))).concatWith(Flux.error(overloaded)) }
        assertFailsWith<RuntimeException> { failover(fake).ask() }
        assertEquals(listOf("a"), fake.calls)
    }

    @Test
    fun `a call against a context cache stays on its model`() {
        val cached = GoogleGenAiChatOptions.builder().model("a").cachedContentName("cachedContents/1").build()
        assertEquals(listOf("a"), failover(Fake { null }).candidates(Prompt("hi", cached)))
    }

    @Test
    fun `the blocking call falls over too`() {
        val fake = Fake { if (it == "a") Flux.error(overloaded) else null }
        assertEquals("b", failover(fake).call(Prompt("hi", options("a"))).result!!.output.text)
    }
}
