# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 4 / Kotlin 2 / Java 21 **MCP server** (Spring AI `spring-ai-starter-mcp-server`, stdio transport, SYNC type) that exposes two tools: `find_leads`, which scores and ranks leads from a bundled CSV against an Ideal Customer Profile (ICP) for Krisp-style targeting, and `draft_email`, which produces a deterministic first-touch email draft for one lead (never sends).

## Commands

```bash
./gradlew build                 # compile + all tests
./gradlew test                  # all tests
./gradlew test --tests 'com.krispwork.mcp.lead.icp.IcpScorerTest'                      # one class
./gradlew test --tests 'com.krispwork.mcp.lead.icp.IcpScorerTest.headcount band edges' # one test (backtick names work quoted)
./gradlew bootRun               # start the MCP server on stdio
./gradlew bootJar               # build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar, for an MCP client config
```

There is no linter configured. Kotlin compiles with `-Xjsr305=strict` and `-Xannotation-default-target=param-property` (so `@Value` on constructor params works without `@param:`).

## Stdio transport caveats

Because the server speaks MCP over stdin/stdout, **nothing may be printed to stdout**. `application.properties` sets `web-application-type=none`, turns the banner off, sets `logging.threshold.console=OFF`, and routes logs to `./logs/leads-mcp-${PID}.log` (gitignored). Never add `println` or re-enable console logging in main code; look in `logs/` to debug a running server.

## Architecture

Package `com.krispwork.mcp.lead`, layered strictly:

- **`mcp/LeadTools`** — the only MCP-facing class. `@McpTool find_leads` with `@McpToolParam` optional params; it just maps nullable params onto a `LeadQuery` (defaults live in `LeadQuery.companion`) and returns `FindLeadsResult`, which Spring AI serialises to JSON. `@McpTool draft_email(leadId, senderName, senderTitle?, personalNote?, proofPoint?)` delegates to `LeadService.draftEmail` and returns `DraftEmailResult`. The tool `description` strings document the scoring weights and drafting behaviour to the LLM client — keep them in sync when `IcpRules` / `EmailTemplates` defaults change.
- **`LeadService`** — filtering, headcount inference, scoring, ranking, and `draftEmail`. `draftEmail` looks the lead up by trimmed id, runs the same headcount inference (private `knownHeadcounts` / `effectiveHeadcount` helpers shared with `findLeads`) and `IcpScorer`, refuses with `drafted=false` + `reason` on unknown id or `Disqualified`, otherwise calls `EmailDrafter` with the effective headcount and a `variant` = the lead's position among same-company rows sorted by `leadId` (private `companyVariant`). Blank `senderName` falls back to `EmailTemplates.defaultSenderName`. Filters (country/industry/headcount) are applied *before* scoring and counted as `filteredOut`; then each lead is scored and bucketed into `disqualified` / `belowThreshold` / qualified. Ranking is score desc, then `leadId` asc for determinism, then `limit`. **Headcount inference**: a lead with blank headcount borrows the max known headcount of another row with the same company name (case-insensitive); `ScoredLead.headcountInferred` flags this.
- **`icp/IcpScorer`** — pure, Spring-free `object`. Two hard gates first (corporate email: present, matches regex, not a free-mail domain; title has no junior keyword) → `IcpResult.Disqualified`; otherwise additive points → `IcpResult.Qualified(score, reasons, roleEmail, email)`. Every point adjustment appends a human-readable `reasons` entry; preserve that when adding rules. Title keyword matching lives in the public `IcpScorer.matchTitleKeyword` and is whole-word for single words (so `it` doesn't match "Editor") and substring for multi-word keywords; `EmailDrafter` reuses it.
- **`email/EmailDrafter`** — pure, Spring-free `object`, deterministic (no randomness, no clock). Maps the lead to an *industry group* (first-match over `EmailTemplates.industryGroups`, same keyword order as `IcpRules.industryPoints`) and a *role group* (via the shared `IcpScorer.matchTitleKeyword`). Body = greeting / optional `personalNote` verbatim / opening / one product sentence / optional assurance / optional proof / call to action / `signOff` + name + title, joined by blank lines with `\n`. Opening: `companyNameOpenings` (whole word of the company name, e.g. `remote`, `analytics`) → `openingsByRole[industry to role]` → `openings[industryGroup]` → `openings[GENERIC]`; when the headcount is unknown, variants containing `{team}` are dropped from the list first (falling back to `teamFallback` only if none remain), then `[variant % n]`; `{company}` and `{team}` are substituted, `{team}` = "a 1,400-person team" from the headcount only. The country is never printed, and `{team}` belongs only in openings where the number carries the argument. Product sentence: `roleLinesByIndustry[industry to role]` → `roleLines[role]` → `genericRoleLine`; there is no separate product line any more, so each role line must itself say what Krisp does. Assurance: `assuranceLines[industry]` (healthcare: on-device processing) right after the product sentence. Proof: `proofPoint` param (verbatim) else `proofLines[industry]`. CTA: `callsToActionByIndustry` → `callsToAction[role]` → `callToAction`, with `{link}` → `downloadUrl`; hands-off roles (IT, engineering, operations, COO) get a team pilot, others "try it on your next call"; never mention a day of the week. Signature title: `senderTitle` else `defaultSenderTitle` ("Krisp") unless the name already contains it. Subject: `subjectTemplate` with `{company}` or `subjectFallback`. Role-mailbox or nameless leads get `Hello <company> team,`. Returns `EmailDraft(to, subject, body, industryAngle = industry group, titleAngle = role group)`; the company-name opening does not change `industryAngle`.
- **`email/EmailTemplates`** — data class holding every string the drafter uses, with `EmailTemplates.DEFAULT`. Change copy here, not in the drafter. Role lines must stand alone (no "that means" referring to the previous sentence) and are the only product sentence in the email. `proofLines` is intentionally empty: never add unverified numbers or customer names; approved proof arrives per call via `proofPoint`. Company-name openings may only reflect what the name literally says, never guessed facts. Tests assert on group selection, placeholders and structure, not on prose, so wording can change freely; keep each opening variant list non-empty and keep the body under ~110 words.
- **`icp/IcpRules`** — a data class holding every keyword list, threshold and point value, with `IcpRules.DEFAULT`. Tune scoring here, not in the scorer. Industry matching is first-match-wins over an ordered list, so more specific keywords must come first. `canonicalCountry` resolves aliases (US/UK/DE…) used both for scoring and for the `countries` filter.
- **`CsvLeadRepository`** (`LeadRepository`) — lazily loads `leads.file` (default `classpath:leads.csv`) once into memory with a minimal RFC 4180 parser. Header is matched by lower-cased column name and all nine columns are required; rows with blank `lead_id` are skipped with a warning; blank cells become `null`.

## Tests

Tests are plain `kotlin.test` + JUnit 5 with no Spring context (except the `contextLoads` smoke test), constructing `CsvLeadRepository(ClassPathResource("leads.csv"))` directly. `LeadServiceTest` and `CsvLeadRepositoryTest` assert on specific rows/scores of the real `src/main/resources/leads.csv` (50 leads, L001–L050), so editing that CSV or the default rules will break expected values like "L017 ranks first with score 10" — update tests deliberately. `EmailDrafterTest` builds `Lead`s by hand and asserts on angle selection, greetings and determinism, not on exact template prose, so copy in `EmailTemplates` can be reworded freely.
