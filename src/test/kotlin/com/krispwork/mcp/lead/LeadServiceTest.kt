package com.krispwork.mcp.lead

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeadServiceTest {

    private val service = LeadService(CsvLeadRepository(ClassPathResource("leads.csv")))

    @Test
    fun `default query ranks BPO ops lead first and excludes disqualified rows`() {
        val result = service.findLeads(LeadQuery())
        val ids = result.leads.map { it.leadId }

        assertEquals(50, result.totalLeads)
        assertEquals("L017", ids.first())
        assertEquals(10, result.leads.first().score)
        assertTrue(result.leads.zipWithNext().all { (a, b) -> a.score >= b.score })
        assertTrue(result.leads.all { it.score >= LeadQuery.DEFAULT_MIN_SCORE })

        assertFalse("L005" in ids, "office assistant must be disqualified")
        assertFalse("L048" in ids, "invalid email must be disqualified")
        assertFalse("L033" in ids, "manufacturing 7800 scores 3, below threshold")
        assertTrue("L030" in ids)
        assertTrue("L014" in ids)
        assertTrue(result.disqualified > 0)
        assertEquals(50, result.filteredOut + result.disqualified + result.belowThreshold + result.qualified)
        assertTrue(result.returned <= result.qualified)
    }

    @Test
    fun `missing headcount is inferred from another row of the same company`() {
        val l001 = service.findLeads(LeadQuery(minScore = 0, limit = 100)).leads.first { it.leadId == "L001" }
        assertEquals(1400, l001.headcount)
        assertTrue(l001.headcountInferred)
        assertTrue(l001.score >= 3 + 3 + 2 + 1)
    }

    @Test
    fun `role mailbox is flagged and email lowercased`() {
        val leads = service.findLeads(LeadQuery(minScore = 0, limit = 100)).leads
        val l002 = leads.first { it.leadId == "L002" }
        assertTrue(l002.roleEmail)
        assertEquals(6, l002.score)
        assertEquals("nina.smith@northwindanalytics.example", leads.first { it.leadId == "L049" }.email)
    }

    @Test
    fun `country alias filter`() {
        val result = service.findLeads(LeadQuery(countries = listOf("US"), minScore = 0, limit = 100))
        assertTrue(result.leads.isNotEmpty())
        assertTrue(result.leads.all { it.country == "United States" })
        assertTrue(result.filteredOut > 0)
    }

    @Test
    fun `industry and headcount filters`() {
        val result = service.findLeads(LeadQuery(industries = listOf("software"), minHeadcount = 200, maxHeadcount = 400, minScore = 0, limit = 100))
        assertTrue(result.leads.isNotEmpty())
        assertTrue(result.leads.all { it.industry == "Software" && it.headcount!! in 200..400 })
        assertNull(result.leads.firstOrNull { it.leadId == "L013" })
        assertNotNull(result.leads.firstOrNull { it.leadId == "L030" })
    }

    @Test
    fun `limit is respected`() {
        assertEquals(3, service.findLeads(LeadQuery(limit = 3)).returned)
        assertEquals(3, service.findLeads(LeadQuery(limit = 3)).leads.size)
    }

    @Test
    fun `draft_email drafts for a qualified lead with bpo angle`() {
        val result = service.draftEmail("L017", "Ana")
        assertTrue(result.drafted)
        assertNull(result.reason)
        assertEquals(10, result.score)
        assertTrue(result.icpReasons.isNotEmpty())
        val draft = assertNotNull(result.draft)
        assertEquals("noah.yilmaz@echocustomerops.example", draft.to)
        assertEquals("bpo", draft.industryAngle)
        assertEquals("operations", draft.titleAngle)
        assertTrue("Echo Customer Ops" in draft.body)
        assertTrue(draft.body.startsWith("Hi Noah,"))
        assertTrue(draft.body.endsWith("Ana\nKrisp"))
        assertTrue("5 to 10 agents" in draft.body, "contact-center ops lead gets the agent pilot CTA")
    }

    @Test
    fun `draft_email refuses disqualified and unknown leads`() {
        val assistant = service.draftEmail("L005", "Ana")
        assertFalse(assistant.drafted)
        assertNull(assistant.draft)
        assertTrue("assistant" in assistant.reason.orEmpty())

        val badEmail = service.draftEmail("L048", "Ana")
        assertFalse(badEmail.drafted)
        assertTrue("invalid email" in badEmail.reason.orEmpty())

        val missing = service.draftEmail("L999", "Ana")
        assertFalse(missing.drafted)
        assertTrue("not found" in missing.reason.orEmpty())
        assertEquals("L999", missing.leadId)
    }

    @Test
    fun `draft_email colleagues at one company get different openings`() {
        // Bluepeak Recruiting: L038 Head of Operations, L044 COO. Talently HR: L012, L046 both Head of Operations.
        val l038 = service.draftEmail("L038", "Ana").draft!!.body
        val l044 = service.draftEmail("L044", "Ana").draft!!.body
        assertNotEquals(l038.split("\n\n")[1], l044.split("\n\n")[1], "openings must differ")

        val l012 = service.draftEmail("L012", "Ana").draft!!.body
        val l046 = service.draftEmail("L046", "Ana").draft!!.body
        assertNotEquals(l012.split("\n\n")[1], l046.split("\n\n")[1], "openings must differ")
        assertFalse("Canada" in l046, "country is never printed")

        // Three Software / IT leads at different companies: openings differ by company name keyword.
        val l013 = service.draftEmail("L013", "Ana").draft!!.body.split("\n\n")[1]
        val l030 = service.draftEmail("L030", "Ana").draft!!.body.split("\n\n")[1]
        val l032 = service.draftEmail("L032", "Ana").draft!!.body.split("\n\n")[1]
        assertNotEquals(l013, l030)
        assertNotEquals(l013, l032)
        assertNotEquals(l030, l032)
        assertFalse("United States" in l030, l030)
    }

    @Test
    fun `draft_email handles role mailbox, inferred headcount, whitespace id and default sender`() {
        val team = service.draftEmail("L035", "")
        assertTrue(team.drafted)
        assertTrue(team.draft!!.body.startsWith("Hello BrightLane Consulting team,"))
        assertTrue(team.draft!!.body.endsWith("The Krisp team"))

        val l001 = service.draftEmail(" L001 ", "Ana", "AE, Krisp", "Congrats on the Berlin office.")
        assertTrue(l001.drafted)
        assertEquals("L001", l001.leadId)
        // L013 is the second Cloudhaven row, so it gets the opening variant that uses the team phrase.
        assertTrue("a 1,400-person team" in service.draftEmail("L013", "Ana").draft!!.body)
        assertFalse("Germany" in service.draftEmail("L013", "Ana").draft!!.body)
        assertTrue("Congrats on the Berlin office." in l001.draft!!.body)
        assertTrue(l001.draft!!.body.endsWith("Ana\nAE, Krisp"))
        val withProof = service.draftEmail("L001", "Ana", null, null, "Approved proof.")
        assertTrue("Approved proof." in withProof.draft!!.body)
        val fromFind = service.findLeads(LeadQuery(minScore = 0, limit = 100)).leads.first { it.leadId == "L001" }
        assertEquals(fromFind.score, l001.score)
        assertEquals(fromFind.reasons, l001.icpReasons)

        assertEquals(service.draftEmail("L017", "Ana"), service.draftEmail("L017", "Ana"))
    }
}
