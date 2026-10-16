# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 4 / Kotlin 2 / Java 21 **MCP server** (Spring AI `spring-ai-starter-mcp-server`, stdio transport, SYNC type) that exposes a single tool, `find_leads`, which scores and ranks leads from a bundled CSV against an Ideal Customer Profile (ICP) for Krisp-style targeting.

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

- **`mcp/LeadTools`** — the only MCP-facing class. `@McpTool find_leads` with `@McpToolParam` optional params; it just maps nullable params onto a `LeadQuery` (defaults live in `LeadQuery.companion`) and returns `FindLeadsResult`, which Spring AI serialises to JSON. The tool `description` string documents the scoring weights to the LLM client — keep it in sync when `IcpRules` defaults change.
- **`LeadService`** — filtering, headcount inference, scoring, ranking. Filters (country/industry/headcount) are applied *before* scoring and counted as `filteredOut`; then each lead is scored and bucketed into `disqualified` / `belowThreshold` / qualified. Ranking is score desc, then `leadId` asc for determinism, then `limit`. **Headcount inference**: a lead with blank headcount borrows the max known headcount of another row with the same company name (case-insensitive); `ScoredLead.headcountInferred` flags this.
- **`icp/IcpScorer`** — pure, Spring-free `object`. Two hard gates first (corporate email: present, matches regex, not a free-mail domain; title has no junior keyword) → `IcpResult.Disqualified`; otherwise additive points → `IcpResult.Qualified(score, reasons, roleEmail, email)`. Every point adjustment appends a human-readable `reasons` entry; preserve that when adding rules. Title keyword matching is whole-word for single words (so `it` doesn't match "Editor") and substring for multi-word keywords.
- **`icp/IcpRules`** — a data class holding every keyword list, threshold and point value, with `IcpRules.DEFAULT`. Tune scoring here, not in the scorer. Industry matching is first-match-wins over an ordered list, so more specific keywords must come first. `canonicalCountry` resolves aliases (US/UK/DE…) used both for scoring and for the `countries` filter.
- **`CsvLeadRepository`** (`LeadRepository`) — lazily loads `leads.file` (default `classpath:leads.csv`) once into memory with a minimal RFC 4180 parser. Header is matched by lower-cased column name and all nine columns are required; rows with blank `lead_id` are skipped with a warning; blank cells become `null`.

## Tests

Tests are plain `kotlin.test` + JUnit 5 with no Spring context (except the `contextLoads` smoke test), constructing `CsvLeadRepository(ClassPathResource("leads.csv"))` directly. `LeadServiceTest` and `CsvLeadRepositoryTest` assert on specific rows/scores of the real `src/main/resources/leads.csv` (50 leads, L001–L050), so editing that CSV or the default rules will break expected values like "L017 ranks first with score 10" — update tests deliberately.
