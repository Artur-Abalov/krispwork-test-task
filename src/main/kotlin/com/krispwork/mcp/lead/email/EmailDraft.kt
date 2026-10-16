package com.krispwork.mcp.lead.email

data class EmailDraft(
    val to: String,
    val subject: String,
    val body: String,
    /** Industry keyword that selected the opening line, or null when the generic line was used. */
    val industryAngle: String?,
    /** Title keyword that selected the role line, or null when the generic line was used. */
    val titleAngle: String?,
)

data class DraftEmailResult(
    val leadId: String,
    val drafted: Boolean,
    /** Set when drafted is false: "not found" or the ICP disqualification reason. */
    val reason: String?,
    /** ICP score of the lead, for context. Null when the lead was refused. */
    val score: Int?,
    val icpReasons: List<String>,
    val draft: EmailDraft?,
)
