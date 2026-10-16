package com.krispwork.mcp.lead.email

/**
 * All copy used by [EmailDrafter]. Fixed strings only, so a draft is fully reproducible.
 *
 * Leads are mapped to an *industry group* and a *role group*. Keyword lists mirror
 * [com.krispwork.mcp.lead.icp.IcpRules]: industry keywords are first-match-wins in order, title keywords use the
 * same whole-word / multi-word rule as the scorer. Copy is then looked up by group.
 *
 * Placeholders: `{company}` -> company name (or "your company"); `{team}` -> "a 3,100-person team" built from the
 * headcount only (an opening variant that uses `{team}` is skipped when the headcount is unknown); `{link}` ->
 * [downloadUrl]. The country is never printed: it adds words and no argument. Use `{team}` only where the number
 * carries the point (many people -> many rooms), never as decoration.
 *
 * Never put unverified numbers or customer names here. [proofLines] is empty on purpose; fill it only with claims
 * the business has approved, or pass an approved `proofPoint` per call.
 */
data class EmailTemplates(
    val industryGroups: List<Pair<String, String>> = listOf(
        "bpo" to BPO, "outsourcing" to BPO, "contact center" to BPO, "call center" to BPO,
        "software" to SOFTWARE, "saas" to SOFTWARE,
        "telecom" to COMMS, "communications" to COMMS,
        "telehealth" to HEALTH, "healthcare technology" to HEALTH,
        "recruiting" to RECRUITING, "hr services" to RECRUITING,
        "customer support" to BPO,
    ),
    val roleGroups: List<Pair<String, String>> = listOf(
        "support" to SUPPORT, "customer" to SUPPORT,
        "operations" to OPERATIONS,
        "sales" to SALES, "revenue" to SALES,
        "cto" to ENGINEERING, "engineering" to ENGINEERING,
        "it" to IT,
        "coo" to COO, "chief operating" to COO,
        "remote" to REMOTE,
    ),
    /**
     * Opening variants keyed by a whole word in the *company name*, checked before everything else. This is the only
     * company-level differentiation the data allows: it reads what the company calls itself (e.g. "Polaris Remote
     * Teams", "Northwind Analytics") and nothing more. First match wins; each list is rotated by variant like
     * [openings].
     */
    val companyNameOpenings: List<Pair<String, List<String>>> = listOf(
        "remote" to listOf(
            "A company built around remote teams like {company} lives on video calls, and everyone is dialling in from a different room.",
            "{company} works distributed by design, which means the team is on calls from home offices, cafes and coworking desks, with the audio to match.",
        ),
        "analytics" to listOf(
            "Analytics teams like {company} spend hours walking customers through dashboards on calls, and nobody follows a number they could not quite hear.",
            "At {company} the team explains findings to customers over calls all week, and background noise makes a careful analysis sound sloppy.",
        ),
        "data" to listOf(
            "Data teams like {company} spend hours walking customers through results on calls, and nobody follows a number they could not quite hear.",
            "At {company} the team explains findings to customers over calls all week, and background noise makes a careful analysis sound sloppy.",
        ),
    ),
    /**
     * Opening variants per (industry group, role group), checked after [companyNameOpenings] and before [openings].
     * Use it where the industry opening talks about things the role does not care about (a VP of Sales does not
     * think about candidates).
     */
    val openingsByRole: Map<Pair<String, String>, List<String>> = mapOf(
        (SOFTWARE to SALES) to listOf(
            "Sales at {company} runs on demos and discovery calls, and a prospect who has to ask you to repeat yourself is already half out of the conversation.",
            "Every demo {company} gives is also a test of how the call sounds, and the rep is rarely in a quiet room when it starts.",
        ),
    ),
    /**
     * Opening sentence per industry group, several variants each. The variant is chosen by the lead's position
     * among its company's rows (sorted by leadId), so two people at one company never get the same opening.
     * Variants that use `{team}` are only eligible when the headcount is known.
     */
    val openings: Map<String, List<String>> = mapOf(
        BPO to listOf(
            "Running a contact center like {company} means hundreds of calls a day, often from busy floors or home offices where noise is out of your control.",
            "At {company} agents are on the phone for most of the day, and background noise on the floor is the one quality problem that scripts and coaching cannot fix.",
            "Every call {company} handles is judged on how it sounds, and the floor is rarely quiet.",
        ),
        SOFTWARE to listOf(
            "Software teams like {company} live on calls with customers and each other, and noise in those calls costs focus on both ends.",
            "With {team}, {company} runs a good part of its week over video, and every open-plan office or home kitchen shows up on the customer's side.",
            "At {company} support, sales and engineering all spend hours a day on calls, and they are rarely all in one quiet place.",
        ),
        COMMS to listOf(
            "For a communications business like {company}, call quality is the product, and background noise is the fastest way to lose a customer's trust.",
            "{company} sells clear communication, so being heard clearly on every internal and customer call is table stakes.",
            "Customers of {company} notice a noisy line immediately, and with people on calls all day there are a lot of chances for that to happen.",
        ),
        HEALTH to listOf(
            "Clinicians and patients at {company} need to hear each other clearly, and most of them are not calling from a quiet room.",
            "At {company} a missed word on a patient call is more than an annoyance, and most of those calls happen far from a quiet office.",
            "Patient calls at {company} come from waiting rooms, cars and kitchens, and the team has to understand every one of them the first time.",
        ),
        RECRUITING to listOf(
            "Recruiting and HR teams like {company} spend their day on interviews and screening calls where every missed word costs time.",
            "At {company} recruiters run back-to-back interviews, and a noisy line on either side makes the candidate repeat themselves and the interviewer guess.",
            "Candidates judge {company} on the first screening call, and with recruiters dialling from wherever they are, the line is not always clean.",
        ),
        GENERIC to listOf(
            "Teams at {company} spend a good part of the day on calls, and background noise is usually the first thing that makes those calls harder than they need to be.",
            "With {team}, {company} has plenty of people on calls every day, and the room behind each of them is rarely quiet.",
            "At {company} people take calls from offices, homes and the road, and the audio quality changes with every location.",
        ),
    ),
    /**
     * Role sentence per (industry group, role group). Falls back to [roleLines] when the pair is absent.
     * This is the only product sentence in the email, so each line must say what Krisp does (removes noise, echo
     * and other voices, in any calling app, on both sides) in words that fit the reader.
     */
    val roleLinesByIndustry: Map<Pair<String, String>, String> = mapOf(
        (BPO to OPERATIONS) to "Krisp removes the floor noise, echo and other voices from every agent's calls in whatever dialler you already run, so one quality standard holds across every site and work-from-home seat.",
        (BPO to COO) to "Krisp removes background noise and other voices from every agent's calls, on both sides, so you hold one call-quality standard across every site without a headset refresh.",
        (BPO to SUPPORT) to "Krisp strips noise and other voices from both sides of each agent's call, so fewer repeats and shorter handle times without retraining anyone.",
        (BPO to IT) to "Krisp is one lightweight install that removes noise and echo from calls in whatever dialler and softphone you already run, with nothing for agents to configure.",
        (SOFTWARE to OPERATIONS) to "Krisp removes background noise, echo and other voices from support, sales and internal calls in every app you already use, without adding another tool to manage.",
        (SOFTWARE to COO) to "Krisp removes background noise, echo and other voices from support, sales and internal calls in every app you already use, without adding another tool to manage.",
        (SOFTWARE to SALES) to "Krisp removes the office noise and other voices from both ends of demos and discovery calls, so the prospect hears the product, not the room.",
        (SOFTWARE to SUPPORT) to "Krisp removes background noise and other voices from both ends of every support call, so customers are not asked to repeat themselves and tickets close faster.",
        (RECRUITING to OPERATIONS) to "Krisp removes background noise and other voices from both sides of interviews and client calls, so candidates are heard the first time and notes are complete.",
        (RECRUITING to COO) to "Krisp removes background noise and other voices from both sides of interviews and client calls, so candidates are heard the first time and notes are complete.",
        (HEALTH to SUPPORT) to "Krisp removes background noise and other voices from both ends of a patient call, whether the clinician is in a clinic or at a kitchen table.",
        (HEALTH to OPERATIONS) to "Krisp removes background noise and other voices from every clinician's and coordinator's calls, whether they dial from a clinic, a car or home.",
    ),
    /** Role sentence per role group, used when there is no industry-specific line. Must stand alone. */
    val roleLines: Map<String, String> = mapOf(
        SUPPORT to "Krisp removes background noise and other voices from both sides of every customer call, so fewer repeats and shorter calls without changing how anyone works.",
        OPERATIONS to "Krisp removes background noise, echo and other voices from calls in every app you already run, giving you one call-quality standard across sites and home offices with no hardware to roll out.",
        COO to "Krisp removes background noise, echo and other voices from calls company-wide in one rollout, without a headset refresh or a process change.",
        SALES to "Krisp removes the room noise and other voices from both ends of discovery and demo calls, so prospects hear the pitch, not the room behind it.",
        ENGINEERING to "Krisp is one lightweight install that removes noise, echo and other voices from calls in every app you already run, so there is nothing to integrate.",
        IT to "Krisp is one lightweight install that removes noise, echo and other voices from calls in every app you already run, with nothing for users to configure.",
        REMOTE to "Krisp removes background noise, echo and other voices from calls in any app, so home offices, cafes and coworking spaces all sound like a quiet room.",
    ),
    val genericRoleLine: String = "Krisp removes background noise, echo and other voices from calls in any app, on both sides of the conversation, with nothing to configure.",
    /**
     * One sentence per industry group that answers the segment's first objection before it is raised, printed right
     * after the product sentence. Healthcare always asks where the audio goes, so it is told that processing happens
     * on the device. Only state things that are true of the product.
     */
    val assuranceLines: Map<String, String> = mapOf(
        HEALTH to "Processing happens on the device itself, so no call audio is sent to Krisp or anywhere else.",
    ),
    /** Optional proof point per industry group. Empty by default; fill only with approved, sourced claims. */
    val proofLines: Map<String, String> = emptyMap(),
    val downloadUrl: String = "https://krisp.ai/download",
    /** Call to action per (industry group, role group); falls back to [callsToAction], then [callToAction]. */
    val callsToActionByIndustry: Map<Pair<String, String>, String> = mapOf(
        (BPO to OPERATIONS) to "Run it with 5 to 10 agents for a week: install from {link} (free plan, nothing to change on the dialler) and reply with what you hear on their recordings.",
        (BPO to COO) to "Run it with 5 to 10 agents for a week: install from {link} (free plan, nothing to change on the dialler) and reply with what you hear on their recordings.",
        (BPO to SUPPORT) to "Run it with 5 to 10 agents for a week: install from {link} (free plan, nothing to change on the dialler) and reply with what you hear on their recordings.",
        (HEALTH to OPERATIONS) to "Run it with one care team for a week: install from {link} (free plan, nothing to connect to your systems) and reply with what the clinicians say.",
        (HEALTH to COO) to "Run it with one care team for a week: install from {link} (free plan, nothing to connect to your systems) and reply with what the clinicians say.",
        (HEALTH to SUPPORT) to "Run it with one care team for a week: install from {link} (free plan, nothing to connect to your systems) and reply with what the clinicians say.",
        (RECRUITING to OPERATIONS) to "Run it with a few recruiters for a week: install from {link} (free plan, nothing to set up) and reply with what they hear on their interviews.",
        (RECRUITING to COO) to "Run it with a few recruiters for a week: install from {link} (free plan, nothing to set up) and reply with what they hear on their interviews.",
    ),
    /**
     * Call to action per role group. Roles that do not take the calls themselves (IT, engineering, operations, COO)
     * get a team pilot; everyone else is asked to try it on their own next call.
     */
    val callsToAction: Map<String, String> = mapOf(
        IT to "Give it to one team for a week: install from {link} (free plan, no admin setup) and reply with what they say about it.",
        ENGINEERING to "Give it to one team for a week: install from {link} (free plan, no admin setup) and reply with what they say about it.",
        OPERATIONS to "Run it with one team for a week: install from {link} (free plan, nothing to roll out) and reply with what they hear.",
        COO to "Run it with one team for a week: install from {link} (free plan, nothing to roll out) and reply with what they hear.",
    ),
    val callToAction: String = "Try it on your next call: install from {link} (a couple of minutes, and there is a free plan) and reply if you hear the difference.",
    val signOff: String = "Best,",
    val defaultSenderName: String = "The Krisp team",
    /** Printed under the sender's name when no title is given, so a bare first name never signs the email alone. */
    val defaultSenderTitle: String = "Krisp",
    /** Subject with a company; `{company}` is substituted. Kept plain so it does not read like a campaign. */
    val subjectTemplate: String = "Call quality at {company}",
    val subjectFallback: String = "Call quality on your team's calls",
    /** Used for `{team}` when no variant without it exists and the headcount is unknown. */
    val teamFallback: String = "the team",
) {
    companion object {
        const val BPO = "bpo"
        const val SOFTWARE = "software"
        const val COMMS = "communications"
        const val HEALTH = "healthcare"
        const val RECRUITING = "recruiting"
        const val GENERIC = "generic"

        const val SUPPORT = "support"
        const val OPERATIONS = "operations"
        const val COO = "coo"
        const val SALES = "sales"
        const val ENGINEERING = "engineering"
        const val IT = "it"
        const val REMOTE = "remote"

        const val TEAM = "{team}"

        val DEFAULT = EmailTemplates()
    }
}
