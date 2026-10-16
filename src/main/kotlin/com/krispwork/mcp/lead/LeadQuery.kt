package com.krispwork.mcp.lead

data class LeadQuery(
    val minScore: Int = DEFAULT_MIN_SCORE,
    val minHeadcount: Int? = null,
    val maxHeadcount: Int? = null,
    val countries: List<String> = emptyList(),
    val industries: List<String> = emptyList(),
    val limit: Int = DEFAULT_LIMIT,
) {
    companion object {
        const val DEFAULT_MIN_SCORE = 6
        const val DEFAULT_LIMIT = 25
    }
}

data class ScoredLead(
    val leadId: String,
    val firstName: String?,
    val lastName: String?,
    val title: String?,
    val company: String?,
    val industry: String?,
    val headcount: Int?,
    val headcountInferred: Boolean,
    val country: String?,
    val email: String,
    val roleEmail: Boolean,
    val score: Int,
    val reasons: List<String>,
)

data class FindLeadsResult(
    val totalLeads: Int,
    val filteredOut: Int,
    val disqualified: Int,
    val belowThreshold: Int,
    /** Leads at or above minScore, before applying limit. */
    val qualified: Int,
    val returned: Int,
    val minScore: Int,
    val leads: List<ScoredLead>,
)
