package com.attendanceio.api.application.agent

/**
 * The paid-plan question shown in the chat when a student runs out of today's messages. It is only
 * a willingness-to-pay check: nothing is sold and the daily limit does not change for anyone.
 *
 * The question is answered either with the app's Yes/No buttons or by typing — a typed reply over
 * the limit is read by [parseAnswer] before the limit message would be sent again.
 */
object AgentSubscriptionOffer {
    const val PRICE_RUPEES = 5

    val BENEFITS = listOf(
        "Full alumni list with every company",
        "Mark attendance just by chatting - the AI marks it for you",
        "A smarter assistant as we keep improving it"
    )

    const val QUESTION = "Would you buy it? Reply yes or no."

    fun limitMessage(limit: Int): String = buildString {
        appendLine("You've used your $limit free AI messages for today. They reset at 12 midnight.")
        appendLine()
        appendLine("We're planning an Attendance IO AI plan for about ₹$PRICE_RUPEES/month:")
        BENEFITS.forEach { appendLine("- $it") }
        appendLine()
        append(QUESTION)
    }

    fun thanks(interested: Boolean): String =
        if (interested) "Thanks! Noted that you'd buy it — we'll let you know when it's ready. Your free messages reset at 12 midnight."
        else "Thanks, noted! Your free messages reset at 12 midnight."

    private val YES = Regex("(?i)^\\s*(yes|yeah|yep|y|ha+n?|haa+|ok(ay)?|sure|of course|definitely|buy|i('| wi)ll buy( it)?)\\s*[.!]*\\s*$")
    private val NO = Regex("(?i)^\\s*(no|nope|nah|n|na+|nahi+|nai|not now|no thanks|nahi chahiye)\\s*[.!]*\\s*$")

    /** True/false for a plain yes/no (English or Hinglish); null for anything else. */
    fun parseAnswer(message: String): Boolean? = when {
        YES.matches(message) -> true
        NO.matches(message) -> false
        else -> null
    }
}
