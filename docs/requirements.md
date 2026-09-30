# DevOps Voice Companion — MVP Requirements

## 1. Goal

DevOps Voice Companion keeps developers informed about their CI builds through Alexa+. Ask about build status and hear a plain-language summary of what passed, what failed, and why. The vision: never miss a broken build — the companion tells you when something fails, without being asked.

## 2. Voice flows

### Flow 1 — Build status (MVP)

- User: "Alexa, ask DevOps Companion how the build is doing"
- Alexa: "The latest build on devops-voice-companion passed 12 minutes ago."

### Flow 2 — Failure summary (MVP)

- User: "Alexa, ask DevOps Companion why did the build fail"
- Alexa: "The build failed in the test step. Three tests failed in PaymentServiceTest, all from a null pointer when the payment gateway times out."

### Flow 3 — What's new (stretch)

- User: "Alexa, ask DevOps Companion anything new"
- Alexa: "Two builds failed since you last checked yesterday evening."

Voice design rules:

- Responses under ~30 seconds spoken (~75 words)
- No code, stack traces, or URLs in spoken output
- Relative times ("12 minutes ago"); repo named in each answer

## 3. Tools

| Tool | Inputs | Output | Data source |
| ---- | ------ | ------ | ----------- |
| `get_build_status` | `owner`, `repo` | Latest run: status, branch, commit message, timing, run URL | GitHub `GET /repos/{owner}/{repo}/actions/runs?per_page=1` |
| `get_failed_runs` | `owner`, `repo`, `limit` (default 5) | Recent failures: run id, branch, commit, time, failed jobs | GitHub list runs, filtered by `conclusion=failure` |
| `get_failure_logs` | `owner`, `repo`, `run_id` | Failed jobs + log excerpts, capped at ~10k chars | GitHub list jobs + download logs |
| `summarize_failure` | `owner`, `repo`, `run_id` | 2–3 sentence plain-language summary | Bedrock Nova Lite, fed by `get_failure_logs` internally |

Notes:

- Repo is a tool parameter on every call, not env config — "how's the build on X?" works for any repo.
- Log cap (~10k chars) protects Bedrock cost and voice latency; raw logs can be megabytes.
- Error handling: repo not found, no runs yet, run still in progress, GitHub rate limit — all return friendly messages, never raw exceptions.

## 4. Non-functional requirements

- **Public HTTPS**: Railway URL; Alexa+ must reach it over the internet. No auth on the MCP endpoint for MVP.
- **Secrets via env vars**: GitHub PAT, AWS credentials. Never in code or the repo.
- **Latency**: status flows under 5s end to end; summaries under 10s (voice dies after ~10s of silence). Nova chosen over Claude for speed.
- **Cost**: stay inside the $150 AWS credits. Nova Lite + free GitHub API = pennies per demo.
- **Uptime**: deploy on every push to main; must be live for the demo video and judging.

## 5. Out of scope

- CI systems beyond GitHub Actions (Jenkins, GitLab CI, CircleCI, Azure Pipelines)
- Proactive push notifications (MCP is request/response; true push needs separate Alexa APIs)
- Web dashboard
- Multi-user auth

## 6. Stretch goals (only if time)

- **"Anything new?"**: server tracks last-reported state per repo (`owner`/`repo` as key); answers with failures since last check.

## 7. Decisions log

| Decision | Choice | Date |
| -------- | ------ | ---- |
| Bedrock model | Nova Lite (latency + cost) | 2026-09-30 |
| Repo targeting | Tool parameter, not env config | 2026-09-30 |
| Demo target | Dogfood: this repo's own Actions runs | 2026-09-30 |
| Notifications | Stretch only ("anything new?" flavor) | 2026-09-30 |
