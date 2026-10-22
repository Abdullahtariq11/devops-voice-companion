import { useEffect, useRef, useState } from 'react'
import Logo from '../../components/Logo'
import { connectToMcpServer } from '../../lib/mcpClient'

type McpConnection = Awaited<ReturnType<typeof connectToMcpServer>>

function SetupScreen() {
    const [serverUrl, setServerUrl] = useState('');
    const [authToken, setAuthToken] = useState('')
    const [error, setError] = useState('');
    const [isConnecting, setIsConnecting] = useState(false)
    const [connection, setConnection] = useState<McpConnection | null>(null)
    const connectionRef = useRef<McpConnection | null>(null)
    const isMounted = useRef(false)

    useEffect(() => {
        isMounted.current = true

        return () => {
            isMounted.current = false
            if (connectionRef.current) {
                void connectionRef.current.client.close().catch(() => undefined)
            }
        }
    }, [])

    function validateServerUrl() {
        try {
            const url = new URL(serverUrl.trim())

            const isLocalDevelopment =
                import.meta.env.DEV &&
                url.protocol === 'http:' &&
                ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)

            if (
                (url.protocol !== 'https:' && !isLocalDevelopment) ||
                url.username ||
                url.password
            ) {
                throw new Error('Invalid server URL')
            }

            setError('')
            return true
        } catch {
            setError("Couldn't reach that server — check the URL and try again")
            return false
        }
    }

    async function handleConnect() {
        if (isConnecting || connection || !validateServerUrl()) return

        setIsConnecting(true)

        try {
            const result = await connectToMcpServer(serverUrl, authToken)

            if (!isMounted.current) {
                await result.client.close().catch(() => undefined)
                return
            }

            connectionRef.current = result
            setConnection(result)
        } catch {
            if (isMounted.current) {
                setError("Couldn't connect to that MCP server — check the URL and try again")
            }
        } finally {
            if (isMounted.current) {
                setIsConnecting(false)
            }
        }
    }

    return (
        <main className='flex min-h-svh items-center justify-center p-6'>
            <section aria-labelledby="setup-title"
                className="relative w-full max-w-120 overflow-hidden rounded-2xl border border-border bg-surface p-8 shadow-2xl"
            >
                <div
                    aria-hidden="true"
                    className='absolute inset-x-16 top-0 h-px bg-primary'
                />
                <p className='text-center font-mono text-xs tracking-widest text-primary'>MCP VOICE CLIENT</p>
                <div className="mt-6 flex flex-col items-center gap-4">
                    <Logo />
                    <p className="text-xl font-semibold tracking-tight text-foreground">
                        Fail<span className="text-primary">Whisperer</span>
                    </p>
                </div>

                <h1
                    id="setup-title"
                    className="mt-3 text-3xl font-semibold tracking-tight"
                >
                    Your delivery pipeline, in conversation.
                </h1>

                <p className="mt-4 text-sm leading-relaxed text-muted">
                    Talk to your CI/CD. Ask about builds, failures, and what broke.
                </p>

                <form
                    className='mt-8 space-y-5'
                    noValidate
                    onSubmit={(event) => {
                        event.preventDefault()
                        void handleConnect()
                    }}
                >
                    <div className="space-y-2">
                        <label htmlFor="server-url" className="block text-sm font-medium">
                            Server URL
                        </label>
                        <input
                            id="server-url"
                            name="serverUrl"
                            type="url"
                            value={serverUrl}
                            disabled={isConnecting || Boolean(connection)}
                            onChange={(event) => setServerUrl(event.target.value)}
                            aria-invalid={Boolean(error)}
                            aria-describedby={error ? 'connection-error' : undefined}
                            required
                            placeholder="https://your-mcp-server.railway.app/mcp"
                            className="w-full rounded-xl border border-border bg-inset px-4 py-3 text-sm placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        />
                        {error && (
                            <p id="connection-error" role="alert" className="text-sm text-failure">
                                {error}
                            </p>
                        )}
                    </div>
                    <div className="space-y-2">
                        <label htmlFor="auth-token" className="block text-sm font-medium">
                            Auth token
                            <span className="ml-2 font-normal text-muted">Optional</span>
                        </label>
                        <input
                            id="auth-token"
                            name="authToken"
                            type="password"
                            value={authToken}
                            disabled={isConnecting || Boolean(connection)}
                            onChange={(event) => setAuthToken(event.target.value)}
                            autoComplete="off"
                            placeholder="Bearer token"
                            className="w-full rounded-xl border border-border bg-inset px-4 py-3 text-sm placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={!serverUrl.trim() || isConnecting || Boolean(connection)}
                        className="w-full rounded-full bg-primary px-4 py-3 text-sm font-semibold text-page transition-colors duration-200 hover:bg-primary-light focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40"
                    >
                        {isConnecting
                            ? 'Discovering tools…'
                            : connection
                                ? 'Server connected'
                                : 'Connect to server'}
                    </button>
                    <p role="status" className="text-sm text-primary">
                        {isConnecting
                            ? 'Discovering tools…'
                            : connection && `${connection.tools.length} tools found and ready to use`}
                    </p>
                </form>
            </section>
        </main>
    )
}

export default SetupScreen
