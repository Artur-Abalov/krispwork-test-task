package com.krispwork.mcp.lead.icp

/**
 * Ideal Customer Profile rules for Krisp-style targeting (call-heavy, mid-market, decision makers).
 *
 * All text matching is case-insensitive substring matching, so new CSV values such as
 * "Contact Center" or "Telecom" are picked up without code changes.
 */
data class IcpRules(
    /** Industry keyword -> points. First match wins; keep the most specific entries first. */
    val industryPoints: List<Pair<String, Int>> = listOf(
        "bpo" to 5, "outsourcing" to 5, "contact center" to 5, "call center" to 5,
        "software" to 3, "saas" to 3, "telecom" to 3, "telehealth" to 3,
        "healthcare technology" to 3, "recruiting" to 3, "communications" to 3, "customer support" to 3,
        "hr services" to 2,
    ),
    val headcountMin: Int = 50,
    val headcountMax: Int = 5000,
    val headcountBandPoints: Int = 2,
    val headcountLargeThreshold: Int = 200,
    val headcountLargePoints: Int = 1,
    val titleKeywords: List<String> = listOf(
        "support", "customer", "operations", "sales", "revenue", "cto", "engineering",
        "it", "coo", "chief operating", "remote",
    ),
    val titlePoints: Int = 2,
    /** Titles containing any of these are disqualified (not decision makers). */
    val juniorTitleKeywords: List<String> = listOf("junior", "intern", "assistant", "designer"),
    val remoteTitleKeyword: String = "remote",
    val remoteTitlePoints: Int = 1,
    val remoteFriendlyCountries: Set<String> = setOf(
        "United States", "United Kingdom", "Germany", "Canada", "Netherlands", "Australia",
    ),
    val remoteCountryPoints: Int = 1,
    val freeMailDomains: Set<String> = setOf(
        "gmail.com", "googlemail.com", "yahoo.com", "outlook.com", "hotmail.com", "live.com",
        "icloud.com", "me.com", "proton.me", "protonmail.com", "mail.ru", "yandex.ru", "yandex.com", "aol.com",
    ),
    val roleMailboxes: Set<String> = setOf("sales", "info", "support", "hello", "contact", "admin", "office", "team"),
    val roleMailboxPenalty: Int = 1,
    /** Country aliases (lower-case) -> canonical name as used in the CSV. */
    val countryAliases: Map<String, String> = mapOf(
        "us" to "United States", "usa" to "United States", "united states of america" to "United States",
        "uk" to "United Kingdom", "gb" to "United Kingdom", "great britain" to "United Kingdom", "england" to "United Kingdom",
        "de" to "Germany", "deutschland" to "Germany",
        "ca" to "Canada", "nl" to "Netherlands", "holland" to "Netherlands",
        "au" to "Australia", "fr" to "France", "es" to "Spain", "in" to "India", "am" to "Armenia",
    ),
) {
    fun canonicalCountry(value: String): String {
        val trimmed = value.trim()
        return countryAliases[trimmed.lowercase()] ?: trimmed
    }

    companion object {
        val DEFAULT = IcpRules()
    }
}
