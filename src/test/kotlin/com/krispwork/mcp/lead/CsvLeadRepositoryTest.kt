package com.krispwork.mcp.lead

import org.springframework.core.io.ClassPathResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CsvLeadRepositoryTest {

    private val repository = CsvLeadRepository(ClassPathResource("leads.csv"))

    @Test
    fun `reads all leads from csv`() {
        val leads = repository.findAll()
        assertEquals(50, leads.size)
        assertEquals("L001", leads.first().leadId)
        assertEquals("L050", leads.last().leadId)
    }

    @Test
    fun `maps columns and blank cells to null`() {
        val lead = repository.findById("L001")!!
        assertEquals("Leo", lead.firstName)
        assertEquals("Moreau", lead.lastName)
        assertEquals("VP of Sales", lead.title)
        assertEquals("Cloudhaven", lead.company)
        assertEquals("Software", lead.industry)
        assertNull(lead.headcount)
        assertEquals("Germany", lead.country)
        assertEquals("leo.moreau@cloudhaven.example", lead.email)

        val anonymous = repository.findById("L035")!!
        assertNull(anonymous.firstName)
        assertNull(anonymous.lastName)
        assertNull(anonymous.title)
        assertEquals(850, anonymous.headcount)
    }

    @Test
    fun `returns null for unknown id`() {
        assertNull(repository.findById("nope"))
    }
}
