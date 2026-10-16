package com.krispwork.mcp.lead.icp

import com.krispwork.mcp.lead.Lead
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IcpScorerTest {

    private fun lead(
        title: String? = "Head of Operations",
        industry: String? = "Consulting",
        headcount: Int? = 100,
        country: String? = "France",
        email: String? = "a.b@acme.example",
    ) = Lead("X", "A", "B", title, "Acme", industry, headcount, country, email)

    private fun scoreOf(lead: Lead, headcount: Int? = lead.headcount): Int =
        assertIs<IcpResult.Qualified>(IcpScorer.score(lead, headcount)).score

    @Test
    fun `baseline title and headcount band`() {
        // operations title +2, headcount 100 in band +2
        assertEquals(4, scoreOf(lead()))
    }

    @Test
    fun `industry points`() {
        assertEquals(4 + 5, scoreOf(lead(industry = "BPO / Outsourcing")))
        assertEquals(4 + 5, scoreOf(lead(industry = "Contact Center")))
        assertEquals(4 + 3, scoreOf(lead(industry = "Software")))
        assertEquals(4 + 3, scoreOf(lead(industry = "Healthcare Technology")))
        assertEquals(4 + 3, scoreOf(lead(industry = "Recruiting")))
        assertEquals(4 + 2, scoreOf(lead(industry = "HR Services")))
        assertEquals(4, scoreOf(lead(industry = "Retail")))
        assertEquals(4, scoreOf(lead(industry = null)))
    }

    @Test
    fun `headcount band edges`() {
        assertEquals(2, scoreOf(lead(headcount = 49)))
        assertEquals(4, scoreOf(lead(headcount = 50)))
        assertEquals(4, scoreOf(lead(headcount = 199)))
        assertEquals(5, scoreOf(lead(headcount = 200)))
        assertEquals(5, scoreOf(lead(headcount = 5000)))
        assertEquals(2, scoreOf(lead(headcount = 5001)))
        assertEquals(2, scoreOf(lead(headcount = null)))
        // inferred headcount passed explicitly wins over the lead's own blank
        assertEquals(5, scoreOf(lead(headcount = null), headcount = 1400))
    }

    @Test
    fun `title keywords`() {
        for (t in listOf("VP of Sales", "Head of Customer Success", "CTO", "VP of Engineering", "Head of IT",
            "IT Manager", "COO", "Chief Operating Officer", "Chief Revenue Officer", "Director of Support")) {
            assertEquals(4, scoreOf(lead(title = t)), "title $t")
        }
        assertEquals(2, scoreOf(lead(title = "Director of People")))
        assertEquals(2, scoreOf(lead(title = null)))
        // 'it' must match as a whole word, not inside e.g. 'Editor'
        assertEquals(2, scoreOf(lead(title = "Editor")))
    }

    @Test
    fun `remote title gets both title and remote points`() {
        assertEquals(2 + 2 + 1, scoreOf(lead(title = "Head of Remote Work")))
    }

    @Test
    fun `remote friendly country`() {
        assertEquals(5, scoreOf(lead(country = "United States")))
        assertEquals(5, scoreOf(lead(country = "Netherlands")))
        assertEquals(4, scoreOf(lead(country = "Armenia")))
    }

    @Test
    fun `role mailbox is penalised and flagged`() {
        val r = assertIs<IcpResult.Qualified>(IcpScorer.score(lead(email = "sales@acme.example")))
        assertEquals(3, r.score)
        assertTrue(r.roleEmail)
    }

    @Test
    fun `email is lowercased`() {
        val r = assertIs<IcpResult.Qualified>(IcpScorer.score(lead(email = "A.B@ACME.EXAMPLE")))
        assertEquals("a.b@acme.example", r.email)
    }

    @Test
    fun `email hard gate`() {
        assertIs<IcpResult.Disqualified>(IcpScorer.score(lead(email = null)))
        assertIs<IcpResult.Disqualified>(IcpScorer.score(lead(email = "grace.taylor@orbitfintech")))
        assertIs<IcpResult.Disqualified>(IcpScorer.score(lead(email = "not-an-email")))
        assertIs<IcpResult.Disqualified>(IcpScorer.score(lead(email = "someone@gmail.com")))
    }

    @Test
    fun `junior titles are disqualified`() {
        for (t in listOf("Junior Designer", "Marketing Intern", "Office Assistant", "Senior Designer")) {
            assertIs<IcpResult.Disqualified>(IcpScorer.score(lead(title = t)), "title $t")
        }
    }
}
