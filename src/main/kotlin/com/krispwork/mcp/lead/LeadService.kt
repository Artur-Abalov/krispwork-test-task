package com.krispwork.mcp.lead

import com.krispwork.mcp.lead.icp.IcpResult
import com.krispwork.mcp.lead.icp.IcpRules
import com.krispwork.mcp.lead.icp.IcpScorer
import org.springframework.stereotype.Service

@Service
class LeadService(
    private val repository: LeadRepository,
    private val rules: IcpRules = IcpRules.DEFAULT,
) {

    fun findLeads(query: LeadQuery): FindLeadsResult {
        val all = repository.findAll()
        val knownHeadcounts = all
            .filter { it.company != null && it.headcount != null }
            .groupBy({ it.company!!.lowercase() }, { it.headcount!! })
            .mapValues { (_, counts) -> counts.max() }

        val wantedCountries = query.countries.map { rules.canonicalCountry(it).lowercase() }.toSet()
        val wantedIndustries = query.industries.map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        var filteredOut = 0
        var disqualified = 0
        var belowThreshold = 0
        val scored = mutableListOf<ScoredLead>()

        for (lead in all) {
            val headcount = lead.headcount ?: lead.company?.let { knownHeadcounts[it.lowercase()] }
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
}
