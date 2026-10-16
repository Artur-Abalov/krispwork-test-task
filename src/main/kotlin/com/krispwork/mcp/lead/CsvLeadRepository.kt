package com.krispwork.mcp.lead

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Repository
import java.nio.charset.StandardCharsets

/**
 * Reads leads from a CSV resource (configured via `leads.file`) once, on first access,
 * and serves them from memory afterwards.
 */
@Repository
class CsvLeadRepository(
    @Value($$"${leads.file}") private val leadsFile: Resource,
) : LeadRepository {

    private val log = LoggerFactory.getLogger(javaClass)

    private val leads: List<Lead> by lazy { load() }

    override fun findAll(): List<Lead> = leads

    override fun findById(leadId: String): Lead? = leads.firstOrNull { it.leadId == leadId }

    private fun load(): List<Lead> {
        require(leadsFile.exists()) { "Leads file not found: ${leadsFile.description}" }

        val lines = leadsFile.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { seq ->
            seq.filter { it.isNotBlank() }.toList()
        }
        if (lines.isEmpty()) return emptyList()

        val header = parseLine(lines.first()).map { it.trim().lowercase() }
        val index = header.withIndex().associate { (i, name) -> name to i }
        for (required in REQUIRED_COLUMNS) {
            require(required in index) { "Leads CSV is missing required column '$required'" }
        }

        val result = lines.drop(1).mapIndexedNotNull { i, line ->
            val cells = parseLine(line)
            fun col(name: String): String? = cells.getOrNull(index.getValue(name))?.trim()?.takeIf { it.isNotEmpty() }

            val leadId = col("lead_id")
            if (leadId == null) {
                log.warn("Skipping CSV line {}: empty lead_id", i + 2)
                return@mapIndexedNotNull null
            }
            Lead(
                leadId = leadId,
                firstName = col("first_name"),
                lastName = col("last_name"),
                title = col("title"),
                company = col("company"),
                industry = col("industry"),
                headcount = col("headcount")?.toIntOrNull(),
                country = col("country"),
                email = col("email"),
            )
        }
        log.info("Loaded {} leads from {}", result.size, leadsFile.description)
        return result
    }

    /** Minimal RFC 4180 line parser: handles commas inside double quotes and escaped quotes ("") . */
    private fun parseLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { cells += current.toString(); current.setLength(0) }
                else -> current.append(c)
            }
            i++
        }
        cells += current.toString()
        return cells
    }

    private companion object {
        val REQUIRED_COLUMNS = listOf(
            "lead_id", "first_name", "last_name", "title", "company", "industry", "headcount", "country", "email",
        )
    }
}
