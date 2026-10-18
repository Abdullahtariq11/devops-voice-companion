# Implementation summary

## Available MCP tools

| Tool | Inputs | Current behavior |
| --- | --- | --- |
| `get_build_status` | `owner`, `repo` | Latest run status, branch, and relative time, with friendly messages for common GitHub errors. |
| `get_failed_runs` | `owner`, `repo`, optional `limit` | Recent failed runs with IDs, branches, and relative times. Defaults to five and accepts one to five results. |
| `get_failure_logs` | `owner`, `repo`, `runId` | Failed or timed-out jobs, failed steps, and bounded log excerpts for a run's latest attempt. |
| `summarize_failure` | `owner`, `repo`, `runId` | Collects failure evidence and asks Bedrock Nova Lite for a short explanation. Validation errors, GitHub errors, and empty results bypass the model. |

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
- Failure details include up to five jobs, with the final 1500 decoded log characters
  per job, including a marker when earlier content is omitted. Errors earlier in a long
  log may still be outside the excerpt.
- The downloader checks log size with HEAD, requests the final 64 KiB for larger logs,
  and reads at most 64 KiB plus one overflow byte. Incomplete or oversized responses
  are rejected. Large logs require a host that supplies a content length and honors
  byte ranges; otherwise job and step metadata remain available without log text.
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

Run the server from `server` using an AWS profile with Bedrock invocation access:

```bash
AWS_PROFILE=abdullah-dev AWS_REGION=us-east-2 ./mvnw spring-boot:run
```

Use your own profile name if it differs. AWS credentials stay in the local AWS
configuration, not in application source. The summarizer uses `amazon.nova-lite-v1:0`.

Connect MCP Inspector using Streamable HTTP and `http://localhost:8080/mcp`.
Call `get_failed_runs`, then pass one returned run ID as `runId` to `get_failure_logs`.
Restart the app and reconnect Inspector after changing tool code.
Call `summarize_failure` with the same inputs to generate a model response. Missing
logs still allow a summary of the failed-job metadata, with instructions to explain
uncertainty. Model output length and wording are guided by the prompt, not strictly enforced.

Run the tests from `server`:

```bash
./mvnw test
```

Tests cover application startup, log downloads without forwarding authorization,
log truncation, rejection of non-HTTPS download URLs, missing logs, preservation of
failed-step details, filtering out successful jobs, and the combined output cap.
HTTP behavior is tested with mock responses rather than live GitHub requests.
Summary orchestration tests also mock Bedrock, checking evidence forwarding, bypass
behavior, missing logs, SDK failures, and empty model responses.

The separate live Bedrock smoke test is disabled by default. Enable it explicitly:

```bash
AWS_PROFILE=abdullah-dev AWS_REGION=us-east-2 RUN_BEDROCK_TESTS=true \
  ./mvnw -Dtest=BedrockSummarizerTests test
```

This makes a billable request with sample failure details; it does not verify live
GitHub log downloads or the full MCP-to-Bedrock flow.

## Still to add

- End-to-end verification of `summarize_failure` through MCP with live GitHub evidence.
- Alexa+ integration and end-to-end voice testing.
- More targeted excerpts for failures outside the final log segment.
