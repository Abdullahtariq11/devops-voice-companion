# Deployment: Railway backend + Vercel frontend

## 1. Protect and update the Railway backend

Keep the existing service rooted at `server/` and its Dockerfile. Do not commit
credentials. Configure these Railway service variables before deploying the new code:

| Variable | Value |
| --- | --- |
| `APP_ENV` | `production` |
| `APP_AUTH_TOKEN` | A long random private bearer token that you generate and retain securely |
| `FRONTEND_ORIGINS` | Your exact Vercel production origin; optionally append `,http://localhost:5173` for local testing |
| `AWS_REGION` | `us-east-2` |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | Your authorized AWS credentials, or use a supported workload identity |
| `GITHUB_TOKEN` | Token permitted to read Actions for the demo repositories |

Use an IAM identity, not AWS root credentials. Production startup refuses an empty
`APP_AUTH_TOKEN`. The same bearer token protects both `/mcp` and `/api/assistant/route`.
Health checks at `/actuator/health` remain unauthenticated. Set Railway's healthcheck
path to `/actuator/health` and leave its platform-provided `PORT` unchanged.

Generate `APP_AUTH_TOKEN` locally with `openssl rand -hex 32`. The command only prints
the value: copy it into Railway yourself, then use the same value in the frontend's
masked **Auth token** field. Never put it in Vercel or a public `VITE_*` variable.
Retain it privately for reconnecting; visitors need authorized access from the operator
or their own self-hosted backend. Connect before recording a demo and start the video
on the voice screen. Refreshing the page clears the in-memory token, so reconnect if
you refresh; generating a different token is not required unless you are rotating it.

`FRONTEND_ORIGINS` is a comma-separated list of exact origins with no paths or trailing
slashes. Do not use `*` or a wildcard covering arbitrary Vercel previews. Add a specific
preview URL explicitly only when needed. CORS is not a substitute for authentication.

Deploy from the branch containing these changes after committing/pushing them yourself.
The existing Railway URL remains your MCP endpoint plus `/mcp`.

## 2. Create the Vercel frontend project

Import the same GitHub repository into Vercel and configure:

- Root Directory: `frontend`
- Framework Preset: Vite
- Install Command: `npm ci`
- Build Command: `npm run build`
- Output Directory: `dist`
- Public environment variable: `VITE_MCP_SERVER_URL` = your Railway HTTPS URL + `/mcp`

`frontend/vercel.json` supplies build defaults and basic browser-security headers.
No API proxy, server function, or AWS credential is needed on Vercel. Do not put
`APP_AUTH_TOKEN`, AWS keys, or GitHub tokens into `VITE_*` variables: those values are
bundled into publicly downloadable JavaScript. Enter the server bearer token in the
masked Connect form when using the app instead.

After Vercel assigns a production domain, update Railway's `FRONTEND_ORIGINS` to that
exact HTTPS origin and redeploy/restart the backend. A domain without a registered
origin will correctly be rejected by CORS. If the frontend URL was saved previously,
disconnect and change the server URL when testing another deployment.

## 3. End-to-end smoke test

1. Verify Railway `/actuator/health` returns `UP`.
2. Open the Vercel production page and connect to Railway `/mcp` with the server bearer token.
3. Confirm four tools are discovered; select Start talking.
4. Enter a repository accessible to the configured GitHub token.
5. Type “How’s the build?” and verify real tool activity and a GitHub response.
6. Type “Why did the latest build fail?” and verify `get_failed_runs` followed by
   `summarize_failure`, or an honest no-failures/access-error response.
7. Follow up with “Explain that failure” and check that the known run is reused.
8. Test microphone permission, the preferred voice, read-aloud interruption, the typed
   fallback, help navigation, and a mobile-width screen.
9. Verify an incorrect server token is rejected. Check CORS from the actual deployed origin.

AI model availability, IAM permissions, and live GitHub evidence require deployment
verification; mocked tests alone do not prove them. Invocations consume AWS resources.
Create billing alerts, keep access restricted, and rotate a demo bearer token if exposed.

## Submission checklist

- Show real MCP discovery and tool execution in the video, not fake progress.
- Explain the independent simulated Alexa+-style web experience accurately.
- Demonstrate an evidence-backed failure explanation and its limits.
- Include setup/deployment instructions, SDK/product feedback, and required repository access.
- Check current hackathon requirements, repository licensing/access, and video visibility before submission.
