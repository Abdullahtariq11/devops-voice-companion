import Logo from '../../components/Logo'

function SetupScreen() {
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

            <form className='mt-8 space-y-5'>
                <div className="space-y-2">
                    <label htmlFor="server-url" className="block text-sm font-medium">
                        Server URL
                    </label>
                    <input
                        id="server-url"
                        name="serverUrl"
                        type="url"
                        required
                        placeholder="https://your-mcp-server.railway.app/mcp"
                        className="w-full rounded-xl border border-border bg-inset px-4 py-3 text-sm placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    />
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
                        autoComplete="off"
                        placeholder="Bearer token"
                        className="w-full rounded-xl border border-border bg-inset px-4 py-3 text-sm placeholder:text-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
                    />
                </div>

                <button
                    type="submit"
                    disabled
                    className="w-full rounded-full bg-primary px-4 py-3 text-sm font-semibold text-page transition-colors duration-200 hover:bg-primary-light focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-40"
                >
                    Connect to server
                </button>
            </form>
            </section>
        </main>
    )
}

export default SetupScreen
