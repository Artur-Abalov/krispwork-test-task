package com.krispwork.mcp.lead

interface LeadRepository {
    fun findAll(): List<Lead>
    fun findById(leadId: String): Lead?
}
