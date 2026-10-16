package com.krispwork.mcp.lead.mcp

import com.krispwork.mcp.lead.FindLeadsResult
import com.krispwork.mcp.lead.LeadQuery
import com.krispwork.mcp.lead.LeadService
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class LeadTools(private val leadService: LeadService) {

    @McpTool(
        name = "find_leads",
        description = """Find and rank leads that fit the Ideal Customer Profile (ICP) for Krisp.
Each lead gets a score (max 12): industry fit (+3, BPO/contact center +5), headcount 50-5000 (+2, 200+ another +1),
decision-maker title in support/customer/operations/sales/revenue/CTO/engineering/IT/COO (+2), remote-focused title (+1),
remote-friendly country (+1), role mailbox such as sales@ (-1). Leads with a missing, invalid or free-mail (gmail etc.)
email and junior/intern/assistant/designer titles are disqualified. Results are sorted by score descending and include
the reasons for each score. All parameters are optional.""",
    )
    fun findLeads(
        @McpToolParam(description = "Minimum ICP score to include (default 6, range 0-12)", required = false)
        minScore: Int?,
        @McpToolParam(description = "Only leads whose company headcount is at least this value", required = false)
        minHeadcount: Int?,
        @McpToolParam(description = "Only leads whose company headcount is at most this value", required = false)
        maxHeadcount: Int?,
        @McpToolParam(description = "Only leads in these countries. Full names or aliases (US, UK, DE, ...)", required = false)
        countries: List<String>?,
        @McpToolParam(description = "Only leads whose industry contains one of these terms (case-insensitive), e.g. 'software', 'bpo'", required = false)
        industries: List<String>?,
        @McpToolParam(description = "Maximum number of leads to return (default 25)", required = false)
        limit: Int?,
    ): FindLeadsResult = leadService.findLeads(
        LeadQuery(
            minScore = minScore ?: LeadQuery.DEFAULT_MIN_SCORE,
            minHeadcount = minHeadcount,
            maxHeadcount = maxHeadcount,
            countries = countries.orEmpty(),
            industries = industries.orEmpty(),
            limit = limit ?: LeadQuery.DEFAULT_LIMIT,
        ),
    )
}
