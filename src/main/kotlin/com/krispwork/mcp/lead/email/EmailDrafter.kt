package com.krispwork.mcp.lead.email

import com.krispwork.mcp.lead.Lead
import com.krispwork.mcp.lead.icp.IcpResult
import com.krispwork.mcp.lead.icp.IcpScorer
import java.util.Locale

/** Pure, deterministic email drafting; no Spring dependencies, no randomness, no clock. */
object EmailDrafter {

    /**
     * @param headcount effective headcount (possibly inferred), used only for the `{team}` phrase; when null, opening
     *   variants that need it are skipped. The country is never printed.
     * @param variant position of this lead among its company's rows; selects the opening sentence so that
     *   colleagues at one company receive different openings. Any non-negative int is valid.
     * @param personalNote optional sentence supplied by the caller, inserted verbatim as its own paragraph.
     *   The server never invents company facts; this is the only place a researched detail can enter.
     * @param proofPoint optional approved proof sentence (a customer, a number), inserted verbatim after the
     *   product line. Overrides [EmailTemplates.proofLines] for this draft. Never invented by the server.
     */
    fun draft(
        lead: Lead,
        qualified: IcpResult.Qualified,
        senderName: String,
        senderTitle: String? = null,
        personalNote: String? = null,
        proofPoint: String? = null,
        headcount: Int? = lead.headcount,
        variant: Int = 0,
        templates: EmailTemplates = EmailTemplates.DEFAULT,
    ): EmailDraft {
        val company = lead.company.clean()
        val firstName = lead.firstName.clean()

        val greeting = when {
            firstName != null && !qualified.roleEmail -> "Hi $firstName,"
            company != null -> "Hello $company team,"
            else -> "Hello,"
        }

        val industry = lead.industry?.lowercase().orEmpty()
        val industryGroup = templates.industryGroups.firstOrNull { (kw, _) -> kw in industry }?.second
        val roleKeyword = IcpScorer.matchTitleKeyword(lead.title?.lowercase().orEmpty(), templates.roleGroups.map { it.first })
        val roleGroup = templates.roleGroups.firstOrNull { it.first == roleKeyword }?.second

        val companyWords = company?.lowercase()?.split(Regex("[^a-z0-9]+"))?.toSet().orEmpty()
        val candidates = templates.companyNameOpenings.firstOrNull { (kw, _) -> kw in companyWords }?.second
            ?: (industryGroup?.let { ig -> roleGroup?.let { rg -> templates.openingsByRole[ig to rg] } })
            ?: templates.openings[industryGroup ?: EmailTemplates.GENERIC]
            ?: templates.openings.getValue(EmailTemplates.GENERIC)
        // The headcount is only printed where a variant asks for it; without a headcount those variants are skipped.
        val team = headcount?.let { "a ${fmt(it)}-person team" }
        val openings = if (team == null) candidates.filterNot { EmailTemplates.TEAM in it }.ifEmpty { candidates } else candidates
        val opening = openings[variant.coerceAtLeast(0) % openings.size]
            .replace("{company}", company ?: "your company")
            .replace(EmailTemplates.TEAM, team ?: templates.teamFallback)

        val roleLine = (industryGroup?.let { ig -> roleGroup?.let { rg -> templates.roleLinesByIndustry[ig to rg] } })
            ?: roleGroup?.let { templates.roleLines[it] }
            ?: templates.genericRoleLine

        val assurance = industryGroup?.let { templates.assuranceLines[it] }.clean()
        val proof = proofPoint.clean() ?: industryGroup?.let { templates.proofLines[it] }.clean()

        val cta = (industryGroup?.let { ig -> roleGroup?.let { rg -> templates.callsToActionByIndustry[ig to rg] } })
            ?: roleGroup?.let { templates.callsToAction[it] }
            ?: templates.callToAction
        val callToAction = cta.replace("{link}", templates.downloadUrl)

        val title = senderTitle.clean()
            ?: templates.defaultSenderTitle.takeUnless { senderName.contains(it, ignoreCase = true) }
        val signature = listOfNotNull(templates.signOff, senderName, title).joinToString("\n")

        val body = listOfNotNull(
            greeting,
            personalNote.clean(),
            opening,
            roleLine,
            assurance,
            proof,
            callToAction,
            signature,
        ).joinToString("\n\n")

        val subject = if (company != null) templates.subjectTemplate.replace("{company}", company)
        else templates.subjectFallback

        return EmailDraft(
            to = qualified.email,
            subject = subject,
            body = body,
            industryAngle = industryGroup,
            titleAngle = roleGroup,
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun fmt(n: Int): String = String.format(Locale.ROOT, "%,d", n)
}
