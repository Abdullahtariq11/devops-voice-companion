# Implementation summary

## Available MCP tools

| Tool | Inputs | Current behavior |
| --- | --- | --- |
| `get_build_status` | `owner`, `repo` | Latest run status, branch, and relative time, with friendly messages for common GitHub errors. |
| `get_failed_runs` | `owner`, `repo`, optional `limit` | Recent failed runs with IDs, branches, and relative times. Defaults to five and accepts one to five results. |
| `get_failure_logs` | `owner`, `repo`, `runId` | Failed or timed-out jobs, failed steps, and bounded log excerpts for a run's latest attempt. |
| `summarize_failure` | `owner`, `repo`, `runId` | Collects failure evidence and asks Bedrock Nova Lite for a short explanation. Validation errors, GitHub errors, and empty results bypass the model. |
| `investigate_failure` | `owner`, `repo`, `runId` | Read-only investigation combining longer logs, a single head-commit diff, and up to three changed source excerpts; separates observations, uncertain cause, next check, and limitations. |

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

## AI tool routing (backend service)

`BedrockToolRouter.route(AssistantRequest)` first checks the current message independently
of history and the selected repository. Common standalone thanks/farewells return a fixed
acknowledgment, and standalone affection returns the scope message, without calling AWS.
Other messages pass a forced structured `classify_current_message` decision with only the
current question: pipeline request, general CI/CD question, social, out of scope, or ambiguous.
Social/off-topic/ambiguous decisions stop before repository routing. Invalid scope responses
fail closed. This prevents history alone from turning a thank-you into a repeated investigation;
model-based scope classification still needs live evaluation.

For a pipeline request it sends the transcript and selected repository to Nova Lite using
Bedrock Converse tool calling. All five approved read-only tools
are exposed, alongside internal `clarify_request` and `explain_pipeline` decisions that
are never dispatched to MCP. The result is an `AssistantResponse` containing either one validated tool
call or a controlled clarification/general explanation with no calls. The router does not execute MCP
tools, retrieve GitHub data, or report a build result itself.

Messages are limited to 2000 characters, with up to six 4000-character history entries.
Repository names are validated before calling AWS. Tool responses must name an approved
tool and supply `owner` and `repo` for the selected repository, with only approved
optional `limit` or `runId` fields. Unknown tools, extra arguments, cross-repository calls,
multiple calls, and truncated or empty responses are rejected. Tool choice is explicitly
`any`, requiring structured decisions instead of Nova's default free-text planning.
Raw routing text is never forwarded, even if the provider unexpectedly returns a text-only
turn: a fixed clarification is returned instead. This prevents internal planning or
unverified build claims from becoming a displayed/spoken routing response.
Each AWS request has a 30-second overall timeout and a 15-second attempt timeout.

General CI/CD questions use an isolated Bedrock call after scope checking with only the current question,
not the repository or conversational history. It must return a structured user-facing
explanation or classify the question as requiring live data/out of scope. Explanations
are bounded to 2000 characters and rejected if they contain recognized internal planning
markup. Clarification and off-topic responses are fixed application text, not arbitrary
model prose. The frontend routing timeout is 100 seconds: normally scope plus routing or
scope plus explanation, with up to three bounded calls if routing selects an explanation.
These educational answers are model-generated general guidance, not verified repository findings.

`POST /api/assistant/route` exposes this plan to the browser. Both typed and spoken
questions use it; repository tool selection is AI-driven rather than phrase-matched.
The standalone social-response fast path does not select repository tools. The browser validates the plan again
and executes it on the existing MCP session. When a failure tool omits `runId`, the
browser calls `get_failed_runs` with limit 1, extracts the first returned ID, and only then
executes the requested tool. No failed runs or a GitHub error returns directly without
inventing an ID. This is bounded dependency resolution, not an unlimited autonomous agent.
Follow-ups carry previous conversational text and a known run ID. Model-proposed IDs must
appear in the current question or supplied context. Raw logs are not read aloud verbatim.

The API admits at most four simultaneous model requests per server instance. Provider
errors are sanitized. CORS accepts explicit `FRONTEND_ORIGINS` for MCP and the routing API.
`APP_AUTH_TOKEN` protects both endpoints when set; `APP_ENV=production` requires a token.
See [deployment](deployment.md) for the required production configuration. The controller
does not implement multi-user authentication or distributed rate limiting. Semantic
classification is still model-based and should be verified with representative live questions.

Run its mocked tests without making live AWS calls:

```bash
cd server
./mvnw -Dtest=BedrockToolRouterTests test
```

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

## Bounded investigation

`investigate_failure` reads only the first page of 100 latest-attempt jobs and selects
one failed/timed-out job. Its log tail is capped at 6000 characters. The run's head SHA
and head repository are checked before source access; fork-head source is skipped.
The first 30 changed files of the single head commit are considered, ranking filenames
mentioned in the log ahead of others, then reading up to three eligible source/test
or `.github/workflows` files at that immutable SHA. This is not a full PR diff or a
search through unchanged files. Removed files retain a patch without source.

Each patch/source excerpt is at most 2000 characters; raw source over 16 KiB is omitted.
JSON requests read at most 1 MiB plus one overflow byte; no source pagination, repository
clone, arbitrary model-selected URL, or write operation is performed. Common credential
filenames and unsafe paths are excluded, but this does not guarantee secret redaction.
Only analyze repositories authorized for sending selected code and logs to Bedrock.

The analyzer makes one structured Nova Lite call, with a 25-second total SDK timeout,
700 output-token limit, and 30000-character evidence ceiling. Non-structured text is
discarded; required report fields and internal-markup checks reject unsafe output.
Repository text is treated as untrusted evidence, not instructions. Coverage limits are
also appended by the server, independently of the model. Missing source access retains
log/job evidence; empty jobs and invalid requests bypass analysis. The browser permits
100 seconds for this MCP call. Routing is a separate bounded model call. Semantic
accuracy and prompt-injection resistance still require live evaluation; hypotheses
are not proof of causation.

The GitHub integration uses [workflow-run metadata](https://docs.github.com/en/rest/actions/workflow-runs?apiVersion=2022-11-28),
[commit files](https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28),
and [repository contents at a ref](https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28).
Fine-grained tokens need Actions read for workflow evidence and Contents read for code.

Regression tests cover immutable SHA access, fork refusal, filename priority, access
failures, source/patch/response limits, forced structured analysis, reasoning rejection,
no-result bypass, and discovery/invocation through the actual HTTP MCP transport.

## Still to add

- End-to-end verification of `summarize_failure` through MCP with live GitHub evidence.
- Alexa+ integration and end-to-end voice testing.
- More targeted excerpts for failures outside the final log segment.
- Full PR/unchanged-file investigation beyond the bounded single-head-commit analysis.
