package com.krispwork.mcp.lead.mcp

import com.krispwork.mcp.lead.FindLeadsResult
import com.krispwork.mcp.lead.LeadQuery
import com.krispwork.mcp.lead.LeadService
import com.krispwork.mcp.lead.email.DraftEmailResult
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

    @McpTool(
        name = "draft_email",
        description = """Draft a short first-touch email for one lead, identified by leadId (use find_leads first to pick one).
Drafting only: nothing is sent. The text is deterministic (same input always gives the same output) and built from fixed
templates: the opening is chosen by a keyword in the company name (e.g. "remote", "analytics"), else by industry and role
(e.g. software sales), else by industry, and rotated between colleagues at the same company; the single product sentence
is chosen by industry and role; healthcare leads get one extra sentence that audio is processed on the device; the call to
action depends on the role (IT/engineering/operations/COO: run it with one team for a week, with industry-specific
wording for contact centers, healthcare and recruiting; everyone else: try it on your next call) and always carries the
download link. The headcount is printed only in the few openings where the number supports the argument; the country is
never printed. The server never invents company facts or proof: pass one researched, verifiable detail in
personalNote and one approved proof sentence (a named customer or a sourced number) in proofPoint; both are inserted
verbatim. The sign-off is the sender's name plus senderTitle, or "Krisp" when no title is given. Leads that fail the ICP
hard gates (missing, invalid or free-mail email; junior/intern/assistant/designer title) and unknown ids are refused
with drafted=false and a reason. The result also includes the lead's ICP score and reasons for context.""",
    )
    fun draftEmail(
        @McpToolParam(description = "Lead id as returned by find_leads, e.g. L017", required = true)
        leadId: String,
        @McpToolParam(description = "Sender's name for the sign-off, e.g. 'Ana Petrosyan'. Use a real person; cold email from a faceless team gets fewer replies.", required = true)
        senderName: String,
        @McpToolParam(description = "Sender's job title, printed under the name, e.g. 'Account Executive, Krisp'", required = false)
        senderTitle: String?,
        @McpToolParam(description = "One short, verified sentence about this company or person (a hiring post, a product launch). Inserted verbatim right after the greeting. Do not invent one.", required = false)
        personalNote: String?,
        @McpToolParam(description = "One approved proof sentence, e.g. a named customer or a sourced number, inserted verbatim after the product sentence. Do not invent one; omit if you have none.", required = false)
        proofPoint: String?,
    ): DraftEmailResult = leadService.draftEmail(leadId, senderName, senderTitle, personalNote, proofPoint)
}
