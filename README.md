# krispwork-mcp

An [MCP](https://modelcontextprotocol.io) server that exposes one tool, `find_leads`, which scores and ranks
leads from a bundled CSV against an Ideal Customer Profile (ICP) for Krisp-style targeting.

Built with Spring Boot 4, Spring AI MCP server starter, Kotlin 2 and Java 21. It speaks MCP over **stdio**.

## Stack and why

Kotlin on the JVM with Spring Boot 4 and the Spring AI MCP server starter.

I picked Kotlin/JVM for two reasons:

- **I know it well**, so I could focus on the scoring logic and the MCP contract rather than on the language.
- **It is robust against messy data and my own mistakes.** Null-safety in the type system makes every blank
  CSV cell an explicit `String?` that the compiler forces me to handle. Data classes, sealed results
  (`IcpResult.Disqualified` / `Qualified`) and exhaustive `when` keep the scoring rules honest. Strict JSR-305
  mode means Spring's nullability annotations are enforced too.

Spring AI's MCP starter was the shortest path to a working server: annotate a method with `@McpTool`, and the
tool schema, JSON serialisation and stdio transport are handled.

**In a real production setting I would choose differently.** The right language is the one the company
already runs, so that the team can own, review and extend the server without a context switch. For Krisp that
would most likely be **TypeScript**, assuming the MCP ecosystem there is mature enough for the use case. The
official MCP TypeScript SDK is the reference implementation and is the most actively maintained, so that
assumption is probably safe today. The trade-off would be giving up some of the compile-time strictness
described above, which I would compensate for with a schema validator such as Zod at the CSV and tool
boundaries.

## Prerequisites

- **JDK 21** (Temurin, Zulu, etc.). Check with `java -version`.
- Nothing else. The Gradle wrapper (`./gradlew`) downloads Gradle itself.

## Quick start

```bash
# 1. Build the fat jar (also compiles and runs the tests)
./gradlew bootJar

# 2. Point your MCP client at it (see "Connect an MCP client" below)
#    The jar is at build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar
```

To run it directly from Gradle instead of a jar:

```bash
./gradlew bootRun
```

The process will start and wait silently on stdin for MCP JSON-RPC messages. That is expected.
Press `Ctrl+C` to stop it.

## Connect an MCP client

The server uses the stdio transport, so a client launches it as a subprocess. Use the **absolute** path to the jar.

### Claude Desktop

Edit `claude_desktop_config.json`
(macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "leads": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/krispwork-mcp/build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar"]
    }
  }
}
```

Restart Claude Desktop. The `find_leads` tool appears in the tools list.

### Claude Code

```bash
claude mcp add leads -- java -jar /absolute/path/to/krispwork-mcp/build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar
```

### MCP Inspector (manual testing)

```bash
npx @modelcontextprotocol/inspector java -jar build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar
```

This opens a browser UI where you can call `find_leads` with arbitrary parameters.

### Any other client

Launch `java -jar <path-to-jar>` and speak MCP over its stdin/stdout. If `java` is not on the client's `PATH`,
use the full path to the binary (for example `/usr/bin/java` or `$JAVA_HOME/bin/java`).

## Transport: why stdio, and what it costs

MCP supports two transports. This server uses **stdio**: the client launches the jar as a child process and
exchanges JSON-RPC messages over its stdin and stdout. The alternative is **Streamable HTTP**, where the server
listens on a port and clients connect over the network.

### Why stdio here

- **Zero deployment.** Nothing to host, no port, no TLS, no firewall rules. The user builds a jar and points
  their client at it.
- **Security comes for free.** The process is only reachable by the client that spawned it, runs as that user,
  and dies with the client. There is no network surface and therefore no authentication to design.
- **Fits the data.** The leads live in a CSV on the same machine. There is no shared state or database that
  would benefit from a central server.
- **Simplest config for desktop clients.** Claude Desktop, Claude Code, Cursor and the Inspector all support
  stdio with a `command` + `args` entry.

### Caveats of stdio

- **stdout is sacred.** Anything printed to stdout that is not an MCP message corrupts the protocol stream and
  the client disconnects. That is why `application.properties` disables the banner, sets
  `web-application-type=none`, turns console logging off and writes logs to `./logs/`. A stray `println` or a
  library that logs to stdout will break the server. Use stderr or the log file.
- **One client per process.** Each client launches its own JVM. Two clients mean two JVMs, each loading the CSV
  and each paying the JVM startup cost (a few seconds for Spring Boot).
- **Local only.** The client and the server must be on the same machine. You cannot share one running instance
  with a team or call it from a remote agent.
- **No independent lifecycle.** The server starts when the client starts it and stops when the client exits.
  You cannot restart it, scale it or monitor it separately.
- **The client needs a JDK.** Every user has to have Java 21 installed and on the client's `PATH`, or the config
  has to point at an absolute `java` binary.
- **Debugging is indirect.** You cannot curl it. Use the log file or the MCP Inspector.

### What switching to HTTP would require

If the server ever needs to be shared, hosted or called remotely, the switch is mostly configuration plus a
real authentication story.

1. **Dependency.** Replace `spring-ai-starter-mcp-server` with `spring-ai-starter-mcp-server-webmvc`
   (or `-webflux`). This pulls in an embedded web server.
2. **Properties.** Remove `spring.main.web-application-type=none` and `spring.ai.mcp.server.stdio=true`.
   Set `spring.ai.mcp.server.protocol=STREAMABLE` and a `server.port`. Console logging can be turned back on
   because stdout is no longer the protocol channel.
3. **Authentication.** An HTTP endpoint is reachable by anyone who can hit the port, so it must be protected.
   The MCP spec expects **OAuth 2.1 bearer tokens**: the server acts as an OAuth resource server, validates
   the `Authorization: Bearer` header on every request, and advertises its authorization server via
   `/.well-known/oauth-protected-resource`. In Spring that means adding
   `spring-boot-starter-security` and `spring-boot-starter-oauth2-resource-server` and pointing them at an
   identity provider (Auth0, Okta, Keycloak, Entra ID, etc.). For an internal tool a static API key checked
   in a filter is a workable shortcut, but clients such as Claude Desktop only speak OAuth for remote servers.
4. **TLS.** Terminate HTTPS in front of the server (reverse proxy, load balancer or Spring's own
   `server.ssl.*` settings). Bearer tokens over plain HTTP are credentials in the clear.
5. **Origin and host checks.** Streamable HTTP servers must validate the `Origin` header to block DNS
   rebinding attacks and should bind to `127.0.0.1` when running locally.
6. **Hosting and operations.** A place to run it (container, VM, PaaS), health endpoints
   (`spring-boot-starter-actuator`), structured logging to stdout for the platform to collect, and
   rate limiting if the identity provider does not supply it.
7. **Multi-tenancy.** One process now serves many users. The CSV becomes shared state, so decide whether
   everyone sees the same leads or whether `leads.file` becomes per-tenant (for example, a database keyed by
   the token's subject).
8. **Client config changes.** Clients switch from `command`/`args` to a `url` entry plus an OAuth flow or
   header configuration.

None of the scoring code changes. `LeadTools`, `LeadService` and `IcpScorer` are transport-agnostic, so the
move is confined to the build file, `application.properties` and a new security configuration.

## The `find_leads` tool

All parameters are optional.

| Parameter      | Type           | Default | Meaning                                                              |
|----------------|----------------|---------|----------------------------------------------------------------------|
| `minScore`     | int (0-12)     | 6       | Minimum ICP score for a lead to be returned                          |
| `minHeadcount` | int            | none    | Keep only companies with at least this many employees                |
| `maxHeadcount` | int            | none    | Keep only companies with at most this many employees                 |
| `countries`    | list of string | none    | Full names or aliases (`US`, `UK`, `DE`, ...)                        |
| `industries`   | list of string | none    | Case-insensitive substring match, e.g. `software`, `bpo`             |
| `limit`        | int            | 25      | Maximum number of leads to return                                    |

Example prompts once connected:

- "Find leads with a score of at least 8."
- "Show me BPO leads in the US and UK with 200+ employees."
- "List the top 5 leads and explain why each one scored the way it did."

The response contains counts (`totalLeads`, `filteredOut`, `disqualified`, `belowThreshold`, `qualified`,
`returned`) and a `leads` array. Each lead carries its `score` and a human-readable `reasons` list.

### Scoring summary

Max score is 12.

- Hard gates (lead is **disqualified**): missing, malformed or free-mail email (gmail etc.); junior, intern,
  assistant or designer titles.
- Industry fit: +3, or +5 for BPO / contact center.
- Headcount 50-5000: +2, and another +1 at 200+.
- Decision-maker title in support, customer, operations, sales, revenue, CTO, engineering, IT or COO: +2.
- Remote-focused title: +1. Remote-friendly country: +1.
- Role mailbox such as `sales@`: -1.

Rules and weights live in `src/main/kotlin/com/krispwork/mcp/lead/icp/IcpRules.kt`.

## Using your own leads file

The bundled file is `src/main/resources/leads.csv` (50 sample leads). To score a different file, override the
`leads.file` property with any Spring resource URL:

```bash
java -jar build/libs/krispwork-mcp-0.0.1-SNAPSHOT.jar --leads.file=file:/absolute/path/to/leads.csv
```

In an MCP client config, add that flag to `args`. The CSV must have a header row with these nine columns
(any order, case-insensitive):

```
lead_id,first_name,last_name,title,company,industry,headcount,country,email
```

Blank cells are allowed. Rows with a blank `lead_id` are skipped.

## Logs and troubleshooting

Because stdout is the MCP channel, the server never prints to the console. Logs go to
`./logs/leads-mcp-<PID>.log`, relative to the working directory the client launched it from.

- **Client says the server failed to start or returned invalid JSON**: check that nothing writes to stdout.
  Then look at the newest file in `logs/`.
- **`java: command not found` in the client**: use the absolute path to the `java` binary in the config.
- **Wrong Java version**: the jar needs Java 21 or newer.
- **Tool returns zero leads**: lower `minScore`, or loosen the country/industry/headcount filters. The
  `filteredOut`, `disqualified` and `belowThreshold` counts in the response show where leads were dropped.

## Development

```bash
./gradlew build                 # compile + all tests
./gradlew test                  # all tests
./gradlew test --tests 'com.krispwork.mcp.lead.icp.IcpScorerTest'   # one class
```

Tests run without a Spring context and assert on specific rows of the bundled `leads.csv`, so changing that
file or the default rules will require updating expected values.

See `CLAUDE.md` for an architecture walkthrough.
