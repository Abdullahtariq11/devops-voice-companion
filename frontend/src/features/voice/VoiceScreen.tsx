import Logo from '../../components/Logo'
import type { McpConnection } from '../../lib/mcpClient'

type VoiceScreenProps = {
  connection: McpConnection
  onBack: () => void
}

export default function VoiceScreen({ connection, onBack }: VoiceScreenProps) {
  const hostname = new URL(connection.serverUrl).host

  return (
    <div className="flex min-h-svh flex-col lg:h-svh lg:overflow-hidden">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-border px-6 py-4 lg:px-10">
        <div className="flex items-center gap-3">
          <Logo />
          <div>
            <h1 className="text-lg font-semibold tracking-tight">
              Fail<span className="text-primary">Whisperer</span>
            </h1>
            <p className="mt-1 font-mono text-[10px] tracking-widest text-muted">
              MCP VOICE CLIENT
            </p>
          </div>
        </div>

        <div className="flex min-w-0 flex-wrap items-center gap-3">
          <div className="flex min-w-0 items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-3 py-2 text-xs">
            <span aria-hidden="true" className="size-1.5 shrink-0 rounded-full bg-primary" />
            <span className="text-primary-light">Connected</span>
            <span title={hostname} className="max-w-36 truncate font-mono text-muted sm:max-w-56">
              {hostname}
            </span>
          </div>
          <button
            type="button"
            onClick={onBack}
            className="min-h-11 rounded-full border border-border px-4 text-xs text-muted transition-colors duration-200 hover:border-border-strong hover:bg-surface-hover hover:text-foreground active:bg-raised focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary motion-reduce:transition-none"
          >
            Connection settings
          </button>
        </div>
      </header>

      <main className="mx-auto grid w-full max-w-7xl flex-1 gap-6 p-6 lg:min-h-0 lg:grid-cols-[0.85fr_1.15fr] lg:px-10 lg:py-8">
        <section aria-labelledby="voice-title" className="relative flex min-h-96 flex-col overflow-hidden rounded-2xl border border-border bg-surface/40 p-6 lg:min-h-0">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0 opacity-30 [background-image:linear-gradient(var(--color-border)_1px,transparent_1px),linear-gradient(90deg,var(--color-border)_1px,transparent_1px)] [background-size:32px_32px]"
          />
          <div className="relative">
            <h2 id="voice-title" className="font-mono text-xs tracking-widest text-muted">
              VOICE SESSION
            </h2>
            <div className="mt-4 flex flex-wrap gap-2 font-mono text-[11px]">
              <span className="rounded-full border border-primary/20 bg-primary/5 px-3 py-1 text-primary-light">
                MCP READY
              </span>
              <span className="rounded-full border border-border bg-inset px-3 py-1 text-steel">
                {connection.tools.length} tools available
              </span>
            </div>
          </div>

          <div className="relative flex flex-1 flex-col items-center justify-center py-12 text-center">
            <div className="flex size-40 items-center justify-center rounded-full border border-dashed border-border-strong">
              <button
                type="button"
                disabled
                aria-label="Microphone unavailable: voice controls are not implemented yet"
                className="flex size-24 cursor-not-allowed items-center justify-center rounded-full border border-border-strong bg-raised text-muted shadow-xl"
              >
                <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" className="size-8">
                  <rect x="9" y="3" width="6" height="12" rx="3" />
                  <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8" />
                </svg>
              </button>
            </div>
            <h3 className="mt-7 text-lg font-medium">Ready for your first question</h3>
            <p className="mt-2 max-w-64 text-sm leading-relaxed text-muted">
              Your MCP session is ready. Voice controls are coming next.
            </p>
          </div>

          <div className="relative flex items-center justify-center gap-2 font-mono text-[10px] tracking-wider text-muted">
            <span aria-hidden="true" className="h-1 w-8 rounded-full bg-primary" />
            IDLE
          </div>
        </section>

        <section aria-labelledby="conversation-title" className="flex min-h-96 flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-xl lg:min-h-0">
          <div className="flex items-center justify-between border-b border-border px-6 py-5">
            <h2 id="conversation-title" className="text-sm font-medium">Conversation</h2>
            <span className="font-mono text-xs text-muted">Today</span>
          </div>
          <div className="flex flex-1 flex-col items-center justify-center px-6 py-12 text-center lg:min-h-0 lg:overflow-y-auto">
            <Logo />
            <h3 className="mt-6 text-xl font-medium tracking-tight">What would you like to know?</h3>
            <p className="mt-3 max-w-72 text-sm leading-relaxed text-muted">
              Build status, failed runs, and useful explanations—all in one conversation.
            </p>
            <p className="mt-6 max-w-72 text-xs leading-relaxed text-steel">
              Your conversation will appear here once we connect the voice controls.
            </p>
          </div>
        </section>
      </main>

      <footer className="shrink-0 px-6 pb-5 text-center text-xs text-muted">
        Connected via MCP · Voice controls coming next
      </footer>
    </div>
  )
}
