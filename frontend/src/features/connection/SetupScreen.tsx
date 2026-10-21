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
                    <p className='font-mono text-xs tracking-widest text-primary'>MCP VOICE CLIENT</p>
                    <p className="mt-6 text-sm font-medium">
                        DevOps Voice Companion
                    </p>

                    <h1
                        id="setup-title"
                        className="mt-3 text-3xl font-semibold tracking-tight"
                    >
                        Your delivery pipeline, in conversation.
                    </h1>

                    <p className="mt-4 text-sm leading-relaxed text-muted">
                        Talk to your CI/CD. Ask about builds, failures, and what broke.
                    </p>



            </section>
        </main>
    )
}

export default SetupScreen
