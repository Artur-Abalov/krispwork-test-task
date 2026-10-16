package com.krispwork.mcp.lead.icp

import com.krispwork.mcp.lead.Lead

sealed interface IcpResult {
    data class Qualified(val score: Int, val reasons: List<String>, val roleEmail: Boolean, val email: String) : IcpResult
    data class Disqualified(val reason: String) : IcpResult
}

/** Pure ICP scoring; no Spring dependencies. */
object IcpScorer {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s.]{2,}$")

    /**
     * @param headcount the effective headcount (possibly inferred from another row of the same company).
     */
    fun score(lead: Lead, headcount: Int? = lead.headcount, rules: IcpRules = IcpRules.DEFAULT): IcpResult {
        // Hard gate: corporate email.
        val email = lead.email?.trim()?.lowercase()
            ?: return IcpResult.Disqualified("missing email")
        if (!EMAIL.matches(email)) return IcpResult.Disqualified("invalid email '$email'")
        val (local, domain) = email.split("@", limit = 2)
        if (domain in rules.freeMailDomains) return IcpResult.Disqualified("free-mail domain '$domain'")

        // Hard gate: seniority.
        val title = lead.title?.lowercase().orEmpty()
        rules.juniorTitleKeywords.firstOrNull { it in title }?.let {
            return IcpResult.Disqualified("non-decision-maker title '${lead.title}' (contains '$it')")
        }

        var score = 0
        val reasons = mutableListOf<String>()

        val industry = lead.industry?.lowercase().orEmpty()
        rules.industryPoints.firstOrNull { (keyword, _) -> keyword in industry }?.let { (keyword, points) ->
            score += points
            reasons += "industry '${lead.industry}' matches '$keyword' +$points"
        }

        if (headcount != null && headcount in rules.headcountMin..rules.headcountMax) {
            score += rules.headcountBandPoints
            reasons += "headcount $headcount in ${rules.headcountMin}-${rules.headcountMax} +${rules.headcountBandPoints}"
            if (headcount >= rules.headcountLargeThreshold) {
                score += rules.headcountLargePoints
                reasons += "headcount >= ${rules.headcountLargeThreshold} +${rules.headcountLargePoints}"
            }
        }

        val titleWords = title.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        rules.titleKeywords.firstOrNull { kw -> if (' ' in kw) kw in title else kw in titleWords }?.let { kw ->
            score += rules.titlePoints
            reasons += "title '${lead.title}' matches '$kw' +${rules.titlePoints}"
        }

        if (rules.remoteTitleKeyword in title) {
            score += rules.remoteTitlePoints
            reasons += "remote-focused title +${rules.remoteTitlePoints}"
        }
        if (lead.country != null && rules.canonicalCountry(lead.country) in rules.remoteFriendlyCountries) {
            score += rules.remoteCountryPoints
            reasons += "remote-friendly country '${lead.country}' +${rules.remoteCountryPoints}"
        }

        val roleEmail = local in rules.roleMailboxes
        if (roleEmail) {
            score -= rules.roleMailboxPenalty
            reasons += "role mailbox '$local@' -${rules.roleMailboxPenalty}"
        }

        return IcpResult.Qualified(score, reasons, roleEmail, email)
    }
}
