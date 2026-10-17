# Implementation summary

## Available MCP tools

| Tool | Inputs | Current behavior |
| --- | --- | --- |
| `get_build_status` | `owner`, `repo` | Latest run status, branch, and relative time, with friendly messages for common GitHub errors. |
| `get_failed_runs` | `owner`, `repo`, optional `limit` | Recent failed runs with IDs, branches, and relative times. Defaults to five and accepts one to five results. |
| `get_failure_logs` | `owner`, `repo`, `runId` | Failed or timed-out jobs, failed steps, and bounded log excerpts for a run's latest attempt. |

The Spring AI BOM manages dependency versions. The MCP WebMVC starter registers the
annotated `BuildTools` methods and exposes them over Streamable HTTP at `/mcp`.

## GitHub data flow

1. `GitHubClient` requests runs from the GitHub Actions REST API.
2. Jackson maps snake_case run responses into `GitHubWorkflowRun` and
   `GitHubRunsResponse`; `toGitHubRun()` produces the run model used by the tools.
3. Run IDs identify the jobs to fetch. `getJobs()` reads all pages for the latest attempt.
4. `GitHubJob`, `GitHubStep`, and `GitHubJobsResponse` hold job and step results.
5. For failed or timed-out jobs, the client requests GitHub's temporary log URL and
   downloads an excerpt with a separate client that has no GitHub authorization header.

`GITHUB_TOKEN` is optional for public repositories and is read from the environment.
Private repositories require a token with suitable access. The API client disables
automatic redirects, and log downloads require an HTTPS URL. HTTP clients use a
three-second connection timeout and a five-second read timeout per request.

## Output limits and error handling

- Completed build timing uses `updatedAt` as an approximation of completion time.
  Active build timing uses `createdAt` and explicitly says when the run was created.
- Failure details include up to five jobs, with the first 1500 decoded log characters
  per job. These excerpts can miss failures later in long logs.
- Log truncation markers count toward the excerpt limit. Combined failure output,
  including job metadata, is capped at 10000 characters.
- If a job log is unavailable, the tool retains its job and failed-step metadata.
  Older runs may have expired logs.
- Build status has specific messages for missing repositories, authentication,
  permissions, rate limits, GitHub server errors, and connection failures.
- Failed-run and failure-detail tools return broader friendly error messages.
  Input limits are checked before making requests.
- Failure-detail output is analysis material, not text intended to be spoken verbatim.

## Local verification

Run the server from `server`:

```bash
./mvnw spring-boot:run
```

Connect MCP Inspector using Streamable HTTP and `http://localhost:8080/mcp`.
Call `get_failed_runs`, then pass one returned run ID as `runId` to `get_failure_logs`.
Restart the app and reconnect Inspector after changing tool code.

Run the tests from `server`:

```bash
./mvnw test
```

Tests cover application startup, log downloads without forwarding authorization,
log truncation, rejection of non-HTTPS download URLs, missing logs, preservation of
failed-step details, filtering out successful jobs, and the combined output cap.
HTTP behavior is tested with mock responses rather than live GitHub requests.

## Still to add

- Amazon Bedrock Nova Lite integration and the `summarize_failure` tool.
- Alexa+ integration and end-to-end voice testing.
- More targeted excerpts so errors later in long logs are available for summarization.
