import Logo from '../../components/Logo'

const examples = [
  ['How’s the build?', 'get_build_status'],
  ['What failed recently?', 'get_failed_runs'],
  ['Show the failure logs', 'get_failure_logs'],
  ['Why did the latest build fail?', 'summarize_failure'],
]

export default function GuideScreen({ onBack }: { onBack: () => void }) {
  return (
    <main className="mx-auto min-h-svh max-w-3xl px-6 py-8 text-sm leading-relaxed">
      <header className="mb-12 flex items-center justify-between gap-4">
        <div className="flex items-center gap-3"><Logo /><span className="font-semibold">Fail<span className="text-primary">Whisperer</span></span></div>
        <button type="button" onClick={onBack} className="min-h-11 rounded-full border border-border px-4 hover:bg-surface-hover focus-visible:outline-2 focus-visible:outline-primary">Back to voice</button>
      </header>
      <p className="font-mono text-xs tracking-widest text-primary">MCP VOICE GUIDE</p>
      <h1 className="mt-4 text-3xl font-semibold tracking-tight">Talk to your delivery pipeline.</h1>
      <p className="mt-4 text-muted">Connect your server and ask about builds, failures, and what broke.</p>
      <div className="mt-10 space-y-10">
        <section><h2 className="text-lg font-medium">01 — What this is</h2>
          <p className="mt-3 text-muted">FailWhisperer is an independent voice client demonstrating an Alexa+-style experience—not an official Alexa integration. Amazon Bedrock chooses approved read-only tools, and the browser executes them over MCP.</p></section>
        <section><h2 className="text-lg font-medium">02 — How to connect</h2>
          <ol className="mt-3 list-decimal space-y-2 pl-5 text-muted">
            <li>Enter your FailWhisperer server’s public HTTPS MCP endpoint.</li>
            <li>Enter the server bearer token if protected. This is not your GitHub token or AWS key.</li>
            <li>Connect, discover the tools, and select Start talking. Enter a GitHub owner/repository.</li>
            <li>Tap the microphone and allow access, or type a question in Conversation. Space controls the mic outside form fields.</li>
          </ol></section>
        <section><h2 className="text-lg font-medium">03 — What you can ask</h2>
          <div className="mt-4 overflow-x-auto rounded-xl border border-border"><table className="w-full text-left">
            <caption className="sr-only">Example questions and the MCP tools they select</caption>
            <thead className="bg-surface"><tr><th className="p-3">Question</th><th className="p-3">Tool</th></tr></thead>
            <tbody>{examples.map(([question, tool]) => <tr key={tool} className="border-t border-border"><td className="p-3">{question}</td><td className="p-3 font-mono text-xs text-primary-light">{tool}</td></tr>)}</tbody>
          </table></div>
          <p className="mt-3 text-muted">For a latest-failure explanation, the app first resolves the most recent failed run. Follow up with “explain that failure.” Change the repository to reset conversation context. Raw logs are displayed, not spoken verbatim.</p>
          <p className="mt-3 text-muted">You can also ask general CI/CD questions such as “What is a pipeline?” or “What is a flaky test?” These explanations do not establish facts about your repository. Unrelated questions are declined; deploying, rerunning builds, and editing code are not supported.</p></section>
        <section><h2 className="text-lg font-medium">04 — Requirements and privacy</h2>
          <p className="mt-3 text-muted">The server needs Streamable HTTP MCP, these GitHub tools, the FailWhisperer AI routing API, and CORS permission for this website. Remote connections require HTTPS; local HTTP is allowed only during development.</p>
          <p className="mt-3 text-muted">Your browser may send microphone audio to its speech recognition provider. Questions and bounded conversation context go to the backend and Bedrock; GitHub data and failure logs may also be processed by Bedrock. Credentials stay in memory in the browser; only the server URL is saved locally. AWS and GitHub credentials belong on the backend.</p>
          <p className="mt-3 text-muted">Voice support and available voices vary by browser. The typed-question and build-status buttons remain available. Findings are limited to the available evidence; a test mismatch does not establish a deeper root cause.</p></section>
      </div>
    </main>
  )
}
