package com.krispwork.mcp.lead

import com.krispwork.mcp.lead.email.DraftEmailResult
import com.krispwork.mcp.lead.email.EmailDrafter
import com.krispwork.mcp.lead.email.EmailTemplates
import com.krispwork.mcp.lead.icp.IcpResult
import com.krispwork.mcp.lead.icp.IcpRules
import com.krispwork.mcp.lead.icp.IcpScorer
import org.springframework.stereotype.Service

@Service
class LeadService(
    private val repository: LeadRepository,
    private val rules: IcpRules = IcpRules.DEFAULT,
    private val templates: EmailTemplates = EmailTemplates.DEFAULT,
) {

    fun findLeads(query: LeadQuery): FindLeadsResult {
        val all = repository.findAll()
        val knownHeadcounts = knownHeadcounts(all)

        val wantedCountries = query.countries.map { rules.canonicalCountry(it).lowercase() }.toSet()
        val wantedIndustries = query.industries.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        var filteredOut = 0
        var disqualified = 0
        var belowThreshold = 0
        val scored = mutableListOf<ScoredLead>()

        for (lead in all) {
            val headcount = effectiveHeadcount(lead, knownHeadcounts)
            val inferred = lead.headcount == null && headcount != null

            val passesFilters =
                (wantedCountries.isEmpty() || lead.country?.let { rules.canonicalCountry(it).lowercase() } in wantedCountries) &&
                (wantedIndustries.isEmpty() || wantedIndustries.any { w -> lead.industry?.lowercase()?.contains(w) == true }) &&
                (query.minHeadcount == null || (headcount != null && headcount >= query.minHeadcount)) &&
                (query.maxHeadcount == null || (headcount != null && headcount <= query.maxHeadcount))
            if (!passesFilters) { filteredOut++; continue }

            when (val result = IcpScorer.score(lead, headcount, rules)) {
                is IcpResult.Disqualified -> disqualified++
                is IcpResult.Qualified -> {
                    if (result.score < query.minScore) { belowThreshold++; continue }
                    scored += ScoredLead(
                        leadId = lead.leadId,
                        firstName = lead.firstName,
                        lastName = lead.lastName,
                        title = lead.title,
                        company = lead.company,
                        industry = lead.industry,
                        headcount = headcount,
                        headcountInferred = inferred,
                        country = lead.country,
                        email = result.email,
                        roleEmail = result.roleEmail,
                        score = result.score,
                        reasons = result.reasons,
                    )
                }
            }
        }

        val ranked = scored
            .sortedWith(compareByDescending<ScoredLead> { it.score }.thenBy { it.leadId })
            .take(query.limit.coerceAtLeast(0))

        return FindLeadsResult(
            totalLeads = all.size,
            filteredOut = filteredOut,
            disqualified = disqualified,
            belowThreshold = belowThreshold,
            qualified = scored.size,
            returned = ranked.size,
            minScore = query.minScore,
            leads = ranked,
        )
    }

    /**
     * Drafts a first-touch email for one lead. Never sends anything; output is a pure function of the CSV and
     * the arguments. [personalNote] is inserted verbatim; the server itself never invents company facts.
     */
    fun draftEmail(
        leadId: String,
        senderName: String?,
        senderTitle: String? = null,
        personalNote: String? = null,
        proofPoint: String? = null,
    ): DraftEmailResult {
        val id = leadId.trim()
        val lead = repository.findById(id)
            ?: return refused(id, "lead '$id' not found")

        val all = repository.findAll()
        val headcount = effectiveHeadcount(lead, knownHeadcounts(all))
        return when (val result = IcpScorer.score(lead, headcount, rules)) {
            is IcpResult.Disqualified -> refused(id, result.reason)
            is IcpResult.Qualified -> DraftEmailResult(
                leadId = id,
                drafted = true,
                reason = null,
                score = result.score,
                icpReasons = result.reasons,
                draft = EmailDrafter.draft(
                    lead = lead,
                    qualified = result,
                    senderName = senderName?.trim()?.takeIf { it.isNotEmpty() } ?: templates.defaultSenderName,
                    senderTitle = senderTitle,
                    personalNote = personalNote,
                    proofPoint = proofPoint,
                    headcount = headcount,
                    variant = companyVariant(lead, all),
                    templates = templates,
                ),
            )
        }
    }

    /**
     * Position of [lead] among the rows of the same company (case-insensitive), ordered by leadId. Used to
     * rotate the opening sentence so colleagues do not receive identical emails. 0 when the company is blank.
     */
    private fun companyVariant(lead: Lead, all: List<Lead>): Int {
        val company = lead.company?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return 0
        return all
            .filter { it.company?.trim()?.lowercase() == company }
            .map { it.leadId }
            .sorted()
            .indexOf(lead.leadId)
            .coerceAtLeast(0)
    }

    private fun refused(leadId: String, reason: String) =
        DraftEmailResult(leadId = leadId, drafted = false, reason = reason, score = null, icpReasons = emptyList(), draft = null)

    /** Max known headcount per lower-cased company name, used to fill blank headcount cells. */
    private fun knownHeadcounts(all: List<Lead>): Map<String, Int> = all
        .filter { it.company != null && it.headcount != null }
        .groupBy({ it.company!!.lowercase() }, { it.headcount!! })
        .mapValues { (_, counts) -> counts.max() }

    private fun effectiveHeadcount(lead: Lead, knownHeadcounts: Map<String, Int>): Int? =
        lead.headcount ?: lead.company?.let { knownHeadcounts[it.lowercase()] }
}
