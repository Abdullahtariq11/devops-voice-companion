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

1. Connect to the running MCP server and select **Start talking**.
2. Enter a repository in `owner/repository` format.
3. Select the microphone (or press Space outside form controls) and allow microphone access.
4. Ask **How's the build?** or **Check the latest build**.

The recognized question calls `get_build_status` using the entered repository. The answer appears
in Conversation and is read aloud when speech synthesis is available. Tap the microphone again
to cancel listening or interrupt playback. Other voice intents are not implemented yet.

**Check build status** remains available without a microphone. Its response has a **Read aloud**
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

## Styling

Tailwind uses the `@tailwindcss/vite` plugin and is imported in `src/index.css`.
Use utility classes directly in components; no separate Tailwind or PostCSS configuration is needed.

Theme utilities include `bg-page`, `bg-surface`, `text-primary`, and `text-thinking`.
The `font-sans` stack prefers Inter when available and otherwise uses the system font.
