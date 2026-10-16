package com.krispwork.mcp.lead.email

import com.krispwork.mcp.lead.Lead
import com.krispwork.mcp.lead.icp.IcpResult
import com.krispwork.mcp.lead.icp.IcpScorer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EmailDrafterTest {

    private fun lead(
        firstName: String? = "Noah",
        title: String? = "Head of Operations",
        company: String? = "Echo Customer Ops",
        industry: String? = "BPO / Outsourcing",
        headcount: Int? = 3100,
        country: String? = "India",
        email: String? = "noah@echocustomerops.example",
    ) = Lead("L1", firstName, "Yilmaz", title, company, industry, headcount, country, email)

    private fun draft(
        lead: Lead,
        sender: String = "Ana",
        senderTitle: String? = null,
        note: String? = null,
        variant: Int = 0,
    ) = EmailDrafter.draft(
        lead, IcpScorer.score(lead) as IcpResult.Qualified, sender,
        senderTitle = senderTitle, personalNote = note, headcount = lead.headcount, variant = variant,
    )

    @Test
    fun `bpo operations lead gets bpo opening and industry-specific role line`() {
        val d = draft(lead())
        assertEquals("bpo", d.industryAngle)
        assertEquals("operations", d.titleAngle)
        assertEquals("noah@echocustomerops.example", d.to)
        assertEquals(EmailTemplates.DEFAULT.subjectTemplate.replace("{company}", "Echo Customer Ops"), d.subject)
        assertFalse("{" in d.subject)
        assertTrue(d.body.startsWith("Hi Noah,\n\n"))
        assertTrue("Echo Customer Ops" in d.body)
        assertTrue(EmailTemplates.DEFAULT.roleLinesByIndustry.getValue("bpo" to "operations") in d.body)
        assertTrue(d.body.endsWith("Best,\nAna\n${EmailTemplates.DEFAULT.defaultSenderTitle}"), d.body)
    }

    @Test
    fun `call to action follows role, carries the download link and never says tomorrow`() {
        val t = EmailTemplates.DEFAULT
        fun cta(d: EmailDraft) = d.body.split("\n\n").let { it[it.size - 2] }

        val bpoOps = draft(lead())
        assertEquals(t.callsToActionByIndustry.getValue("bpo" to "operations").replace("{link}", t.downloadUrl), cta(bpoOps))

        val it = draft(lead(title = "Head of IT", industry = "Software"))
        assertEquals(t.callsToAction.getValue("it").replace("{link}", t.downloadUrl), cta(it))

        val sales = draft(lead(title = "VP of Sales", industry = "Software"))
        assertEquals(t.callToAction.replace("{link}", t.downloadUrl), cta(sales))

        // Operations and COO do not take the calls themselves, so they get a team pilot, not "your next call".
        val ops = draft(lead(title = "Head of Operations", industry = "Software"))
        assertEquals(t.callsToAction.getValue("operations").replace("{link}", t.downloadUrl), cta(ops))
        val recruitingCoo = draft(lead(title = "COO", industry = "Recruiting"))
        assertEquals(t.callsToActionByIndustry.getValue("recruiting" to "coo").replace("{link}", t.downloadUrl), cta(recruitingCoo))

        for (d in listOf(bpoOps, it, sales, ops, recruitingCoo)) {
            assertTrue(t.downloadUrl in d.body)
            assertFalse("tomorrow" in d.body.lowercase())
            assertFalse("{link}" in d.body)
        }
    }

    @Test
    fun `company name keyword picks the opening before industry and rotates by variant`() {
        val remote0 = draft(lead(company = "Polaris Remote Teams", industry = "Software", title = "Head of IT"))
        val remote1 = draft(lead(company = "Polaris Remote Teams", industry = "Software", title = "Head of IT"), variant = 1)
        val plain = draft(lead(company = "Cloudhaven", industry = "Software", title = "Head of IT"))
        val analytics = draft(lead(company = "Northwind Analytics", industry = "Software", title = "IT Manager"))
        fun opening(d: EmailDraft) = d.body.split("\n\n")[1]

        assertEquals("software", remote0.industryAngle, "angle reporting is unchanged by the company-name opening")
        assertNotEquals(opening(remote0), opening(plain))
        assertNotEquals(opening(analytics), opening(plain))
        assertNotEquals(opening(analytics), opening(remote0))
        assertNotEquals(opening(remote0), opening(remote1))
        assertTrue("Polaris Remote Teams" in opening(remote0))
        // Only whole words count: "Remoteworks" is not "remote", "Datadog" is not "data".
        assertEquals(opening(plain).replace("Cloudhaven", "Remoteworks"), opening(draft(lead(company = "Remoteworks", industry = "Software", title = "Head of IT"))))
    }

    @Test
    fun `sender title defaults to the company and is skipped when the name already names it`() {
        val t = EmailTemplates.DEFAULT
        assertTrue(draft(lead(), sender = "Artur").body.endsWith("Best,\nArtur\n${t.defaultSenderTitle}"))
        assertTrue(draft(lead(), sender = "Artur, Krisp").body.endsWith("Best,\nArtur, Krisp"))
        assertTrue(draft(lead(), sender = t.defaultSenderName).body.endsWith("Best,\n${t.defaultSenderName}"))
        assertTrue(draft(lead(), sender = "Artur", senderTitle = "AE, Krisp").body.endsWith("Best,\nArtur\nAE, Krisp"))
    }

    @Test
    fun `role groups and whole-word title matching`() {
        assertEquals("it", draft(lead(title = "Head of IT", industry = "Software")).titleAngle)
        assertEquals("coo", draft(lead(title = "Chief Operating Officer")).titleAngle)
        assertEquals("coo", draft(lead(title = "COO")).titleAngle)
        assertEquals("engineering", draft(lead(title = "VP of Engineering")).titleAngle)
        assertEquals("support", draft(lead(title = "Head of Customer Success")).titleAngle)
        assertNull(draft(lead(title = "Editor")).titleAngle, "'it' must not match inside 'Editor'")
    }

    @Test
    fun `role line falls back from industry pair to role to generic`() {
        val pair = draft(lead(industry = "Software", title = "VP of Sales"))
        assertTrue(EmailTemplates.DEFAULT.roleLinesByIndustry.getValue("software" to "sales") in pair.body)

        val roleOnly = draft(lead(industry = "Manufacturing", title = "VP of Sales"))
        assertNull(roleOnly.industryAngle)
        assertTrue(EmailTemplates.DEFAULT.roleLines.getValue("sales") in roleOnly.body)

        val generic = draft(lead(industry = "Manufacturing", title = "Director of People"))
        assertNull(generic.titleAngle)
        assertTrue(EmailTemplates.DEFAULT.genericRoleLine in generic.body)
        assertFalse("that means" in generic.body)
    }

    @Test
    fun `team phrase prints the headcount only, and variants needing it are skipped without one`() {
        val t = EmailTemplates.DEFAULT
        fun opening(d: EmailDraft) = d.body.split("\n\n")[1]
        // Software variant 1 is the one place the number carries the argument (many people -> many rooms).
        val full = draft(lead(title = "Head of IT", industry = "Software", headcount = 1400, country = "Germany"), variant = 1)
        assertTrue("a 1,400-person team" in full.body, full.body)
        assertFalse("Germany" in full.body, "country is never printed")
        assertFalse("in the United States" in draft(lead(title = "Head of IT", industry = "Software", headcount = 270, country = "United States"), variant = 1).body)

        // Without a headcount the {team} variant is skipped, so the rotation runs over the remaining variants.
        val withoutTeam = t.openings.getValue("software").filterNot { "{team}" in it }
        val noHeadcount = draft(lead(title = "Head of IT", industry = "Software", headcount = null), variant = 1)
        assertEquals(withoutTeam[1 % withoutTeam.size].replace("{company}", "Echo Customer Ops"), opening(noHeadcount))
        assertFalse("-person" in noHeadcount.body)

        val nothing = draft(lead(headcount = null, country = null, company = null), variant = 1)
        assertTrue("your company" in nothing.body)
        assertFalse("{" in nothing.body)

        // A template whose only variants need {team} still renders, using the fallback.
        val onlyTeam = EmailTemplates(openings = mapOf("generic" to listOf("Hello {team} at {company}.")))
        val fallback = EmailDrafter.draft(lead(industry = "Manufacturing", headcount = null), IcpScorer.score(lead()) as IcpResult.Qualified, "Ana", headcount = null, templates = onlyTeam)
        assertTrue("Hello ${onlyTeam.teamFallback} at Echo Customer Ops." in fallback.body)
    }

    @Test
    fun `role-specific opening beats the industry one but not the company-name one`() {
        val t = EmailTemplates.DEFAULT
        fun opening(d: EmailDraft) = d.body.split("\n\n")[1]
        val sales = draft(lead(title = "VP of Sales", company = "Cloudhaven", industry = "Software"))
        assertEquals(t.openingsByRole.getValue("software" to "sales")[0].replace("{company}", "Cloudhaven"), opening(sales))
        assertFalse("candidate" in sales.body.lowercase(), "a sales lead is not told about candidates")
        val it = draft(lead(title = "Head of IT", company = "Cloudhaven", industry = "Software"))
        assertEquals(t.openings.getValue("software")[0].replace("{company}", "Cloudhaven"), opening(it))
        val named = draft(lead(title = "VP of Sales", company = "Northwind Analytics", industry = "Software"))
        assertTrue(opening(named) in t.companyNameOpenings.first { it.first == "analytics" }.second.map { it.replace("{company}", "Northwind Analytics") })
    }

    @Test
    fun `healthcare gets the on-device assurance right after the product sentence`() {
        val t = EmailTemplates.DEFAULT
        val health = draft(lead(title = "Head of Operations", company = "Quantum Health Tech", industry = "Healthcare Technology", headcount = 510, country = "United States"), variant = 1)
        val paragraphs = health.body.split("\n\n")
        assertEquals(t.roleLinesByIndustry.getValue("healthcare" to "operations"), paragraphs[2])
        assertEquals(t.assuranceLines.getValue("healthcare"), paragraphs[3])
        assertEquals(t.callsToActionByIndustry.getValue("healthcare" to "operations").replace("{link}", t.downloadUrl), paragraphs[4])
        assertFalse("510" in health.body, "headcount and country are not decoration")
        assertFalse("United States" in health.body)
        assertTrue(paragraphs[1].startsWith("At Quantum Health Tech"), paragraphs[1])

        assertFalse(t.assuranceLines.getValue("healthcare") in draft(lead()).body)
    }

    @Test
    fun `variant rotates the opening and wraps around`() {
        val v0 = draft(lead(), variant = 0)
        val v1 = draft(lead(), variant = 1)
        val v2 = draft(lead(), variant = 2)
        val size = EmailTemplates.DEFAULT.openings.getValue("bpo").size
        assertNotEquals(v0.body, v1.body)
        assertNotEquals(v1.body, v2.body)
        assertEquals(v0, draft(lead(), variant = size))
        assertEquals(v0, draft(lead(), variant = -5))
    }

    @Test
    fun `personal note and sender title are inserted verbatim`() {
        val d = draft(lead(), sender = "Ana Petrosyan", senderTitle = "Account Executive, Krisp", note = "Saw you are hiring 40 agents in Manila.")
        val paragraphs = d.body.split("\n\n")
        assertEquals("Hi Noah,", paragraphs[0])
        assertEquals("Saw you are hiring 40 agents in Manila.", paragraphs[1])
        assertEquals(1, d.body.windowed("Saw you are hiring".length).count { it == "Saw you are hiring" })
        assertTrue(d.body.endsWith("Best,\nAna Petrosyan\nAccount Executive, Krisp"))

        val blank = draft(lead(), note = "   ", senderTitle = " ")
        assertEquals(draft(lead()), blank)
    }

    @Test
    fun `proof line appears only when configured or passed, and the passed one wins`() {
        assertFalse("contact centers" in draft(lead()).body)
        val templates = EmailTemplates(proofLines = mapOf("bpo" to "Approved claim about contact centers."))
        val withProof = EmailDrafter.draft(lead(), IcpScorer.score(lead()) as IcpResult.Qualified, "Ana", templates = templates)
        assertTrue("Approved claim about contact centers." in withProof.body)

        val passed = EmailDrafter.draft(
            lead(), IcpScorer.score(lead()) as IcpResult.Qualified, "Ana",
            proofPoint = "  Verified customer sentence.  ", templates = templates,
        )
        val paragraphs = passed.body.split("\n\n")
        assertEquals("Verified customer sentence.", paragraphs[3], "proof sits right after the product sentence")
        assertFalse("contact centers" in passed.body)
        assertEquals(draft(lead()), draft(lead()).also { EmailDrafter.draft(lead(), IcpScorer.score(lead()) as IcpResult.Qualified, "Ana", proofPoint = "  ") })
    }

    @Test
    fun `missing name or role mailbox uses team greeting`() {
        assertTrue(draft(lead(firstName = null)).body.startsWith("Hello Echo Customer Ops team,"))
        assertTrue(draft(lead(email = "info@echocustomerops.example")).body.startsWith("Hello Echo Customer Ops team,"))
        val noCompany = draft(lead(firstName = " ", company = null))
        assertTrue(noCompany.body.startsWith("Hello,\n"))
        assertEquals(EmailTemplates.DEFAULT.subjectFallback, noCompany.subject)
    }

    @Test
    fun `output is deterministic, short and uses LF line endings`() {
        val a = draft(lead())
        assertEquals(a, draft(lead()))
        assertFalse('\r' in a.body)
        val samples = listOf(
            a,
            draft(lead(title = "Head of IT", industry = "Software", headcount = 1400, country = "Germany"), variant = 1),
            draft(lead(title = "COO", company = "Northwind Analytics", industry = "Software", country = "United States"), variant = 1),
            draft(lead(title = "Head of Operations", company = "Quantum Health Tech", industry = "Healthcare Technology", headcount = 510), variant = 1),
            draft(lead(title = "Director of People", industry = "Manufacturing")),
        )
        for (d in samples) {
            val words = d.body.split(Regex("\\s+")).count { it.isNotEmpty() }
            assertTrue(words <= 110, "body has $words words:\n${d.body}")
        }
    }
}
