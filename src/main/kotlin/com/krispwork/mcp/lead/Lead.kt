package com.krispwork.mcp.lead

data class Lead(
    val leadId: String,
    val firstName: String?,
    val lastName: String?,
    val title: String?,
    val company: String?,
    val industry: String?,
    val headcount: Int?,
    val country: String?,
    val email: String?,
)
