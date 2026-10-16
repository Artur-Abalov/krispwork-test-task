package com.krispwork.mcp.lead

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
}
