-- Willingness to pay for the AI plan (₹5/month), asked when a student hits the daily message limit.
-- Read-only: run any section on its own.

-- 1. The headline: how many were asked, and how they answered.
SELECT
    COUNT(*)                                        AS students_shown_offer,
    COUNT(*) FILTER (WHERE response = 'YES')        AS said_yes,
    COUNT(*) FILTER (WHERE response = 'NO')         AS said_no,
    COUNT(*) FILTER (WHERE response IS NULL)        AS no_answer,
    ROUND(100.0 * COUNT(*) FILTER (WHERE response = 'YES')
          / NULLIF(COUNT(*) FILTER (WHERE response IS NOT NULL), 0), 1) AS yes_percent_of_answers
FROM agent_subscription_interest;

-- 2. Does heavier use mean more willing to pay? offer_count = times they ran out of messages.
SELECT
    CASE WHEN offer_count = 1 THEN '1 time' WHEN offer_count <= 3 THEN '2-3 times' ELSE '4+ times' END AS hit_limit,
    COUNT(*)                                 AS students,
    COUNT(*) FILTER (WHERE response = 'YES') AS said_yes,
    COUNT(*) FILTER (WHERE response = 'NO')  AS said_no
FROM agent_subscription_interest
GROUP BY 1
ORDER BY MIN(offer_count);

-- 3. Usage: AI questions per day, and how many students used it / ran out (limit = 1).
SELECT
    DATE(m.created_at)                       AS day,
    COUNT(*)                                 AS questions,
    COUNT(DISTINCT c.user_email)             AS students_using_ai,
    COUNT(DISTINCT c.user_email) FILTER (WHERE per_day.n >= 1) AS students_at_limit
FROM agent_message m
JOIN agent_conversation c ON c.id = m.conversation_id
JOIN (
    SELECT c2.user_email, DATE(m2.created_at) AS day, COUNT(*) AS n
    FROM agent_message m2
    JOIN agent_conversation c2 ON c2.id = m2.conversation_id
    WHERE m2.role = 'USER'
    GROUP BY 1, 2
) per_day ON per_day.user_email = c.user_email AND per_day.day = DATE(m.created_at)
WHERE m.role = 'USER'
  AND m.created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY 1
ORDER BY 1 DESC;

-- 4. Who said yes (for a follow-up when the plan is ready).
SELECT user_email, roll_number, offer_count, responded_at
FROM agent_subscription_interest
WHERE response = 'YES'
ORDER BY responded_at DESC;
