import { useEffect, useRef, useState } from 'react'
import Logo from '../../components/Logo'
import type { McpConnection } from '../../lib/mcpClient'
import { useBrowserSpeech } from './useBrowserSpeech'
import { isBuildStatusQuestion } from './voiceCommands'
import VoiceSettings from './VoiceSettings'

type VoiceScreenProps = {
  connection: McpConnection
  onBack: () => void
}

export default function VoiceScreen({ connection, onBack }: VoiceScreenProps) {
  const [repository, setRepository] = useState('')
  const [repositoryError, setRepositoryError] = useState('')
  const [isChecking, setIsChecking] = useState(false)
  const [requestError, setRequestError] = useState('')
  const [response, setResponse] = useState<{ text: string; receivedAt: string } | null>(null)
  const [checkedRepository, setCheckedRepository] = useState('')
  const [spokenQuestion, setSpokenQuestion] = useState('')
  const [heardTranscript, setHeardTranscript] = useState('')
  const [commandError, setCommandError] = useState('')
  const requestRef = useRef<AbortController | null>(null)
  const hostname = new URL(connection.serverUrl).host
  const canCheckBuild = connection.tools.some((tool) => tool.name === 'get_build_status')
  const speech = useBrowserSpeech((transcript) => {
    setHeardTranscript(transcript)

    if (!isBuildStatusQuestion(transcript)) {
      setCommandError('For now, ask “How’s the build?” or “Check the latest build.” Other voice commands are coming next.')
      return
    }

    void handleCheckBuild(transcript)
  })
  const voiceState = isChecking ? 'Thinking' : speech.isListening ? 'Listening' : speech.isSpeaking ? 'Speaking' : 'Idle'

  useEffect(() => {
    return () => requestRef.current?.abort()
  }, [])

  function validateRepository() {
    const repositoryName = repository.trim()
    const match = /^([a-zA-Z0-9][a-zA-Z0-9-]{0,38})\/([a-zA-Z0-9._-]{1,100})$/.exec(repositoryName)

    if (!match || match[2] === '.' || match[2] === '..') {
      setRepositoryError('Enter a repository in owner/repository format, not a URL.')
      return null
    }

    setRepositoryError('')
    return { owner: match[1], repo: match[2], name: repositoryName }
  }

  async function handleCheckBuild(question = '') {
    if (requestRef.current || !canCheckBuild) return

    const target = validateRepository()
    if (!target) return

    speech.stopSpeaking()
    setCommandError('')
    setRequestError('')
    setResponse(null)
    setCheckedRepository(target.name)
    setSpokenQuestion(question)
    setIsChecking(true)

    const controller = new AbortController()
    requestRef.current = controller

    try {
      const result = await connection.client.callTool(
        {
          name: 'get_build_status',
          arguments: { owner: target.owner, repo: target.repo },
        },
        { signal: controller.signal },
      )

      if (controller.signal.aborted) return

      const text = result.content
        .filter((content) => content.type === 'text')
        .map((content) => content.text)
        .join('\n')
        .trim()

      if (result.isError || !text) {
        setRequestError(text || 'The server returned no readable build status. Please try again.')
      } else {
        setResponse({ text, receivedAt: new Date().toISOString() })
        if (question) speech.speak(text)
      }
    } catch {
      if (!controller.signal.aborted) {
        setRequestError('Could not retrieve build status. Check your connection and try again.')
      }
    } finally {
      if (!controller.signal.aborted) {
        setIsChecking(false)
      }
      if (requestRef.current === controller) {
        requestRef.current = null
      }
    }
  }

  function handleVoiceControl() {
    if (isChecking) return
    if (speech.isSpeaking) {
      speech.stopSpeaking()
      return
    }
    if (speech.isListening) {
      speech.stopListening()
      return
    }
    if (!canCheckBuild || !validateRepository()) return

    setCommandError('')
    setHeardTranscript('')
    speech.startListening()
  }

  useEffect(() => {
    function handleSpace(event: KeyboardEvent) {
      if (event.code !== 'Space' || event.repeat || event.isComposing || event.altKey || event.ctrlKey || event.metaKey) return
      if (event.target instanceof Element && event.target.closest('input, textarea, select, button, a, [contenteditable]:not([contenteditable="false"])')) return
      event.preventDefault()
      handleVoiceControl()
    }

    window.addEventListener('keydown', handleSpace)
    return () => window.removeEventListener('keydown', handleSpace)
  })

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
        <section aria-labelledby="voice-title" className="relative flex min-h-96 flex-col overflow-y-auto rounded-2xl border border-border bg-surface/40 p-6 lg:min-h-0">
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

          <form
            className="relative mt-6"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              void handleCheckBuild()
            }}
          >
            <label htmlFor="repository" className="block text-sm font-medium">
              GitHub repository
            </label>
            <input
              id="repository"
              type="text"
              value={repository}
              disabled={isChecking || speech.isListening}
              onChange={(event) => setRepository(event.target.value)}
              aria-invalid={Boolean(repositoryError)}
              aria-describedby={repositoryError ? 'repository-error' : undefined}
              placeholder="owner/repository"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              className="mt-2 w-full rounded-xl border border-border bg-inset px-4 py-3 font-mono text-sm placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
            />
            {repositoryError && (
              <p id="repository-error" role="alert" className="mt-2 text-sm text-failure">
                {repositoryError}
              </p>
            )}
            <button
              type="submit"
              disabled={!repository.trim() || isChecking || speech.isListening || !canCheckBuild}
              className="mt-3 w-full rounded-full bg-primary px-4 py-3 text-sm font-semibold text-page transition-colors duration-200 hover:bg-primary-light active:bg-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
            >
              {isChecking ? 'Checking build status…' : 'Check build status'}
            </button>
            {!canCheckBuild && (
              <p className="mt-2 text-sm text-muted">This server does not offer get_build_status.</p>
            )}
          </form>

          {speech.supportsSynthesis && (
            <VoiceSettings
              voices={speech.voices}
              selectedVoiceURI={speech.selectedVoiceURI}
              rate={speech.rate}
              disabled={isChecking || speech.isListening}
              isSpeaking={speech.isSpeaking}
              onVoiceChange={speech.changeVoice}
              onRateChange={speech.changeRate}
              onPreview={() => speech.speak("Hi, I'm FailWhisperer. Let's find out how your latest build is doing.")}
              onStop={speech.stopSpeaking}
            />
          )}

          <div className="relative flex flex-1 flex-col items-center justify-center py-4 text-center">
            <div className="relative flex size-36 shrink-0 items-center justify-center rounded-full border border-dashed border-border-strong">
              {speech.isListening && (
                <span aria-hidden="true" className="pointer-events-none absolute inset-3 rounded-full border border-primary/40 motion-safe:animate-ping" />
              )}
              <button
                type="button"
                onClick={handleVoiceControl}
                disabled={isChecking || (!speech.isSpeaking && (!speech.supportsRecognition || !canCheckBuild))}
                aria-label={speech.isSpeaking ? 'Stop speaking' : speech.isListening ? 'Stop listening' : 'Start listening'}
                aria-pressed={speech.isListening || speech.isSpeaking}
                className={`relative flex size-24 items-center justify-center rounded-full border shadow-xl transition-colors duration-200 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-50 motion-reduce:transition-none ${isChecking ? 'border-thinking/30 bg-thinking/10 text-thinking' : speech.isListening || speech.isSpeaking ? 'border-primary/40 bg-primary/10 text-primary hover:bg-primary/20 active:bg-primary/30' : 'border-border-strong bg-raised text-muted hover:border-primary/40 hover:text-primary active:bg-surface-hover'}`}
              >
                {speech.isSpeaking ? (
                  <span aria-hidden="true" className="flex h-10 items-center gap-1">
                    {[16, 28, 36, 24, 12].map((height, index) => (
                      <span key={index} className="w-1 rounded-full bg-primary motion-safe:animate-pulse" style={{ height, animationDelay: `${index * 120}ms` }} />
                    ))}
                  </span>
                ) : (
                <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" className="size-8">
                  <rect x="9" y="3" width="6" height="12" rx="3" />
                  <path d="M5 10v2a7 7 0 0 0 14 0v-2M12 19v3M8 22h8" />
                </svg>
                )}
              </button>
            </div>
            <h3 className="mt-5 text-lg font-medium">
              {isChecking ? 'Checking build status…' : speech.isListening ? 'Listening…' : speech.isSpeaking ? 'Speaking — tap to interrupt' : speech.supportsRecognition ? 'Tap to talk' : 'Use Check build status'}
            </h3>
            <p className="mt-2 max-w-64 text-sm leading-relaxed text-muted">
              {speech.isListening ? 'Ask “How’s the build?” Tap again to cancel.' : speech.isSpeaking ? 'Reading the answer from your MCP server.' : speech.supportsRecognition ? 'Ask “How’s the build?” · or press Space' : 'Speech recognition is unavailable in this browser. The button above still works.'}
            </p>
            {heardTranscript && (
              <p role="status" className="mt-3 max-w-72 text-xs text-steel">Heard: “{heardTranscript}”</p>
            )}
            {(speech.speechError || commandError) && (
              <p role="alert" className="mt-3 max-w-72 text-sm text-failure">{commandError || speech.speechError}</p>
            )}
          </div>

          <div role="status" aria-label={`Voice state: ${voiceState}`} className="relative flex flex-wrap items-center justify-center gap-3 font-mono text-[10px] tracking-wider">
            {['Idle', 'Listening', 'Thinking', 'Speaking'].map((state) => (
              <span key={state} className={`flex flex-col items-center gap-2 ${state === voiceState ? state === 'Thinking' ? 'text-thinking' : 'text-primary' : 'text-muted'}`}>
                <span aria-hidden="true" className={`h-1 rounded-full ${state === voiceState ? state === 'Thinking' ? 'w-8 bg-thinking' : 'w-8 bg-primary' : 'w-3 bg-border-strong'}`} />
                {state.toUpperCase()}
              </span>
            ))}
          </div>
        </section>

        <section aria-labelledby="conversation-title" className="flex min-h-96 flex-col overflow-hidden rounded-2xl border border-border bg-surface shadow-xl lg:min-h-0">
          <div className="flex items-center justify-between border-b border-border px-6 py-5">
            <h2 id="conversation-title" className="text-sm font-medium">Conversation</h2>
            <span className="font-mono text-xs text-muted">Today</span>
          </div>
          {checkedRepository ? (
            <div className="flex-1 space-y-6 overflow-y-auto p-6 lg:min-h-0">
              <div className="ml-auto max-w-[90%] rounded-2xl border border-border bg-raised px-4 py-3">
                <p className="text-sm">{spokenQuestion || 'Check the latest build for'}</p>
                <p className="mt-1 break-all font-mono text-sm text-primary-light">{checkedRepository}</p>
              </div>
              <div className="rounded-2xl border border-border bg-inset p-5" aria-busy={isChecking}>
                <p className={`font-mono text-xs ${isChecking ? 'text-thinking' : 'text-steel'}`}>
                  get_build_status · {isChecking ? 'running' : requestError ? 'error' : 'complete'}
                </p>
                {isChecking && (
                  <p role="status" className="mt-3 text-sm text-muted">Checking GitHub Actions…</p>
                )}
                {requestError && (
                  <p role="alert" className="mt-3 whitespace-pre-wrap break-words text-sm text-failure">{requestError}</p>
                )}
                {response && (
                  <div role="status">
                    <p className="mt-3 whitespace-pre-wrap break-words text-sm leading-relaxed">{response.text}</p>
                    <time dateTime={response.receivedAt} className="mt-4 block font-mono text-xs text-muted tabular-nums">
                      {new Date(response.receivedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </time>
                    {speech.supportsSynthesis && (
                      <button
                        type="button"
                        disabled={speech.isListening || isChecking}
                        onClick={() => speech.isSpeaking ? speech.stopSpeaking() : speech.speak(response.text)}
                        className="mt-4 min-h-11 rounded-full border border-border px-4 text-xs text-primary-light transition-colors duration-200 hover:bg-surface-hover active:bg-raised focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40 motion-reduce:transition-none"
                      >
                        {speech.isSpeaking ? 'Stop reading' : 'Read aloud'}
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>
          ) : (
          <div className="flex flex-1 flex-col items-center justify-center px-6 py-12 text-center lg:min-h-0 lg:overflow-y-auto">
            <Logo />
            <h3 className="mt-6 text-xl font-medium tracking-tight">What would you like to know?</h3>
            <p className="mt-3 max-w-72 text-sm leading-relaxed text-muted">
              Build status, failed runs, and useful explanations—all in one conversation.
            </p>
            <p className="mt-6 max-w-72 text-xs leading-relaxed text-steel">
              Enter a repository, then ask “How’s the build?” or select Check build status.
            </p>
          </div>
          )}
        </section>
      </main>

      <footer className="shrink-0 px-6 pb-5 text-center text-xs text-muted">
        Connected via MCP · Speech recognition may use your browser’s online service
      </footer>
    </div>
  )
}
