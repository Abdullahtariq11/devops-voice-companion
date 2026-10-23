# DevOps Voice Companion frontend

FailWhisperer uses React, TypeScript, Vite, and Tailwind CSS to connect to a DevOps MCP server.

Run these commands from `frontend`:

```bash
npm install
npm run dev
```

Build and lint:

```bash
npm run build
npm run lint
```

## Voice build status

For a protected backend, paste its private server bearer token into the masked **Auth token**
field on the Connect screen. Obtain it from the server operator; do not enter your GitHub
token or AWS credentials. It is not a Vercel environment variable and is kept in memory only.
After a refresh or page close, reconnect with the same token. See the root README's
[connection and demo instructions](../README.md#connecting-to-a-protected-server).

1. Connect to the running MCP server and select **Start talking**.
2. Enter a repository in `owner/repository` format.
3. Select the microphone (or press Space outside form controls) and allow microphone access.
4. Ask **How's the build?**, **What failed recently?**, or **Why did the latest build fail?**.

The recognized question goes to the backend Bedrock router, which selects an approved MCP tool,
a general CI/CD explanation, or a controlled clarification/scope message.
The browser executes the tool using the entered repository. The answer appears
in Conversation and is read aloud when speech synthesis is available. Tap the microphone again
to cancel listening or interrupt playback. Typed questions use the same AI flow, and suggested
prompts are clickable. Follow-ups such as **explain that failure** use bounded conversation context.
For latest-failure explanations the browser resolves the most recent failed run first.
Raw logs are shown rather than spoken; ask for a summary to hear an explanation.

General questions such as **What is a CI/CD pipeline?** and **What is a flaky test?**
use a separate educational answer path without repository status or conversation history.
Unrelated questions are declined with a pipeline-only scope message. Model-internal
planning text is not used as a conversational reply. A passing latest build is not
evidence that there are no historical failures; ask **Any failures?** to check failed runs.

**Check build status** remains available without a microphone or the routing API. Its response has a **Read aloud**
button for optional playback. Leaving the voice screen cancels recognition, playback, and pending requests.

Open **Voice & playback** to choose an available voice and adjust speaking speed (0.75–1.25×).
The default is **Google UK English Female** at **0.90×**, falling back to the browser default
when that voice is unavailable.
Select **Preview voice** to audition it without calling MCP. These settings also apply to spoken answers
and **Read aloud**. English voices are listed when available; otherwise the browser's available voices
are shown. Voice quality depends on the browser and installed voices; no external TTS service is added.

Recognition requires browser support, permission, and a secure context (HTTPS or localhost).
Some browsers use an online recognition service; audio is not guaranteed to stay on your device.
See the [browser speech recognition documentation](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition).

## Deployment

Ask “Investigate that failure in depth” to use `investigate_failure` after a summary,
or “Investigate the latest failure” to resolve the latest failed run automatically.
The backend must expose the new tool; reconnect after redeploying to discover five tools.
The report separates observations, possible cause, next check and coverage limits.
It inspects bounded changed-file evidence, not the entire repository, and never edits code.
Backend GitHub credentials need Contents read as well as Actions read; no new frontend secret is needed.

Use Vercel with Root Directory `frontend`, Vite preset, `npm ci`, `npm run build`, and output `dist`.
Set the public `VITE_MCP_SERVER_URL` to the Railway HTTPS `/mcp` endpoint; `.env.example` shows
the expected format. Enter the private server bearer token in the Connect form, never a `VITE_*`
variable. Only the server URL is saved locally. See [the deployment checklist](../docs/deployment.md)
for Railway origins, API access protection, health checks, and end-to-end verification.

## Styling

Tailwind uses the `@tailwindcss/vite` plugin and is imported in `src/index.css`.
Use utility classes directly in components; no separate Tailwind or PostCSS configuration is needed.

Theme utilities include `bg-page`, `bg-surface`, `text-primary`, and `text-thinking`.
The `font-sans` stack prefers Inter when available and otherwise uses the system font.
