# FailWhisperer

Talk to your delivery pipeline. Ask about GitHub Actions builds, inspect failed runs,
and hear an evidence-based explanation of what broke.

An independent web voice client and self-hosted MCP server for the Amazon Developer
Hackathon. This demonstrates an Alexa+-style experience; it is **not an official Alexa
integration** and does not require an Echo or Alexa+ preview access.

## How it works

```text
Browser speech or typed question
  → Spring Boot AI routing API → Amazon Bedrock Nova Lite selects an approved tool
  → browser MCP client → Streamable HTTP MCP server → GitHub Actions
  → failure summaries use Bedrock with bounded GitHub evidence
  → visible response and optional browser speech playback
```

Actual tool calls use MCP, not a simulated timer. For latest-failure explanations,
the client resolves the latest failed run before calling `summarize_failure`.
Follow-up questions carry bounded conversation context and the previous run ID.
The AI cannot deploy, rerun builds, or modify repositories. A failed assertion does
not establish a deeper root cause; summaries distinguish observations from uncertainty.

You can also ask general CI/CD questions such as “What is a pipeline?” or “What is a
flaky test?” Educational explanations are generated separately, without repository
status or conversation history, and are not presented as findings about your code.
Unrelated requests receive a pipeline-only scope message. Deploying, rerunning builds,
and editing code are unsupported. Internal model planning is not a user-facing answer.
Standalone thanks receive a brief acknowledgment without rerunning tools. Other questions
are scope-checked without conversation history before any repository tool is selected.

## Stack

- `server/`: Java 25, Spring Boot, Spring AI MCP, AWS SDK for Bedrock.
- `frontend/`: React, TypeScript, Vite, Tailwind, official MCP TypeScript client.
- GitHub Actions for CI; Railway backend; Vercel frontend.

## Run locally

Start the backend from `server/` with `GITHUB_TOKEN` already set in your shell:

```bash
AWS_PROFILE=your-profile AWS_REGION=us-east-2 ./mvnw spring-boot:run
```

In another terminal:

```bash
cd frontend
npm ci
npm run dev
```

Connect to `http://localhost:8080/mcp` from `http://localhost:5173`. Local HTTP is
accepted only in a frontend development build. Leave the server auth token blank
unless `APP_AUTH_TOKEN` is configured. Enter an accessible `owner/repository`, then
ask by microphone or text. The separate Check build status button works without AI
routing and remains useful as a fallback.

Try: “Is the build green?”, “What failed recently?”, “Why did the latest build fail?”,
then “Show the logs for that failure.” Missing or inaccessible logs remain a stated limitation.

### Deeper failure investigation

Ask **“Investigate that failure in depth”** or **“Investigate the latest failure”**.
The read-only `investigate_failure` tool combines a longer log excerpt with up to
three changed source/test/workflow files and patches pinned to the run's head SHA.
It returns observations, a possible cause (not confirmed), one next diagnostic check,
and explicit coverage limits. It can recognize an intentionally simulated workflow
failure when the provided evidence shows one; it does not invent missing test code.

This first version inspects one failed job from the first 100 latest-attempt jobs and
the first 30 files of a **single head commit**, not the full PR or unchanged source.
Fork-head source is skipped. Source/patch excerpts can be truncated or unavailable.
The GitHub token needs **Actions: read** and **Contents: read** on the selected repository.
Selected source excerpts, patches and logs are sent to Bedrock; only connect repositories
you are authorized to analyze. Filename filtering is not a secret-redaction guarantee.
No files are edited, builds rerun, or fixes applied. Investigation may take longer than
a normal summary, so wait for it to finish before asking another question.

## Connecting to a protected server

The website can be public while its MCP backend requires a private **server bearer token**.
If you are using someone else's server, request access from its operator or self-host
your own backend. Your GitHub token and AWS credentials are not substitutes for this token.

For your own deployment:

1. Generate a token locally, before recording or screen sharing:
   ```bash
   openssl rand -hex 32
   ```
   This prints a random value; it does **not** configure Railway automatically.
2. Copy that value into the backend's Railway variable `APP_AUTH_TOKEN`, save, and redeploy.
3. Open the frontend, enter the server's `/mcp` URL, and paste the **same value** into
   the masked **Auth token** field. Connect and select **Start talking**.

Do not put this token in Vercel environment variables, `VITE_*` variables, source code,
Git commits, screenshots, or public documentation. Vercel only needs the public server
URL in `VITE_MCP_SERVER_URL`. Keep the token privately so you can reconnect later.

### Preparing a recorded demo

Connect and confirm tool discovery **before starting the recording**, then begin the
demo on the voice screen. You do not need to type or reveal a token during the demo.
The app saves only the server URL; the token remains in browser memory and is not
displayed on the voice screen. Refreshing or closing the page requires reconnecting
with the same token. A new token is only needed if you choose to rotate or replace it;
update Railway and reconnect clients when doing so.

## Deploy and verify

Follow [the Railway + Vercel deployment checklist](docs/deployment.md). Keep GitHub
and AWS credentials on the backend. Never put secrets in `VITE_*` environment variables.
The app saves only the server URL locally; the server bearer token stays in memory.

```bash
cd server
RUN_BEDROCK_TESTS=false ./mvnw test
cd ../frontend
npm run build
```

The default tests mock AWS and GitHub; live Bedrock testing is opt-in and billable.
See [implementation details](docs/implementation.md) and [frontend usage](frontend/README.md).

## Privacy and limitations

Browser speech recognition may use an online provider and is not supported everywhere.
Typed questions remain available. Questions, bounded recent conversation, and GitHub
failure evidence may be processed by Bedrock. Speech playback depends on installed
browser/device voices; Google UK English Female at 0.90× is preferred when available.

The bearer token is a shared demo access control, not multi-user authentication or
per-user GitHub authorization. API concurrency is bounded per server instance; configure
AWS budgets/monitoring and stronger rate limiting before opening access broadly.
