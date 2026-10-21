# DevOps Voice Companion frontend

React, TypeScript, Vite, and Tailwind CSS. The starter contains an empty app and a minimal dark theme.

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

## Styling

Tailwind uses the `@tailwindcss/vite` plugin and is imported in `src/index.css`.
Use utility classes directly in components; no separate Tailwind or PostCSS configuration is needed.

Theme utilities include `bg-page`, `bg-surface`, `text-primary`, and `text-thinking`.
The `font-sans` stack prefers Inter when available and otherwise uses the system font.
