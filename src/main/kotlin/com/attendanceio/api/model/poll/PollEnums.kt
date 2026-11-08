package com.attendanceio.api.model.poll

enum class PollType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    YES_NO
}

enum class PollStatus {
    ACTIVE,
    ENDED,
    CLOSED
}

enum class PollReportReason {
    SPAM,
    OFFENSIVE_CONTENT,
    MISLEADING_OR_IRRELEVANT,
    HARASSMENT,
    OTHER
}
