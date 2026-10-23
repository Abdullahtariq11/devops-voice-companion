import type { McpConnection } from './mcpClient'

export type AssistantTurn = { role: 'user' | 'assistant'; text: string }
export type ToolActivity = { name: string; state: 'running' | 'complete' | 'error' }
type ToolCall = { name: string; arguments: Record<string, unknown> }
type Decision = { message: string; toolCalls: ToolCall[] }
export type AssistantAnswer = { text: string; runId: number | null; rawLogs: boolean }

const approvedTools = new Set(['get_build_status', 'get_failed_runs', 'get_failure_logs', 'summarize_failure'])

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function validId(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value > 0
}

export function firstFailedRunId(text: string): number | null {
  const match = /\bRun\s+(\d+):/i.exec(text)
  const runId = match ? Number(match[1]) : null
  return validId(runId) ? runId : null
}

function parseDecision(value: unknown, repository: string): Decision {
  if (!isObject(value) || typeof value.message !== 'string' || value.message.length > 4000 ||
      !Array.isArray(value.toolCalls) || value.toolCalls.length > 1) {
    throw new Error('The AI returned an invalid response. Please try again.')
  }
  const [owner, repo] = repository.split('/')
  const calls: ToolCall[] = value.toolCalls.map((call: unknown) => {
    if (!isObject(call) || typeof call.name !== 'string' || !approvedTools.has(call.name) || !isObject(call.arguments)) {
      throw new Error('The AI requested an unsupported tool.')
    }
    const args = call.arguments
    const keys = call.name === 'get_failed_runs' ? ['owner', 'repo', 'limit'] :
      ['get_failure_logs', 'summarize_failure'].includes(call.name) ? ['owner', 'repo', 'runId'] : ['owner', 'repo']
    if (Object.keys(args).some((key) => !keys.includes(key)) ||
        typeof args.owner !== 'string' || args.owner.toLowerCase() !== owner.toLowerCase() ||
        typeof args.repo !== 'string' || args.repo.toLowerCase() !== repo.toLowerCase() ||
        ('runId' in args && !validId(args.runId)) ||
        ('limit' in args && (!validId(args.limit) || args.limit > 5))) {
      throw new Error('The AI returned invalid repository or tool arguments.')
    }
    return { name: call.name, arguments: { ...args, owner, repo } }
  })
  if (!calls.length && !value.message.trim()) throw new Error('The AI returned an empty response.')
  return { message: value.message, toolCalls: calls }
}

async function chooseTool(connection: McpConnection, repository: string, question: string,
  history: AssistantTurn[], runId: number | null, signal: AbortSignal) {
  const headers = connection.getAssistantHeaders()
  headers.set('Content-Type', 'application/json')
  headers.set('Accept', 'application/json')
  const response = await fetch(new URL('/api/assistant/route', connection.serverUrl), {
    method: 'POST', headers, credentials: 'omit', redirect: 'error',
    signal: AbortSignal.any([signal, AbortSignal.timeout(40000)]),
    body: JSON.stringify({ message: question, repository, runId,
      history: history.slice(-6).map((turn) => ({ ...turn, text: turn.text.slice(0, 4000) })) }),
  })
  if (!response.ok) {
    if (response.status === 401) throw new Error('Server authentication failed. Reconnect with the MCP server bearer token.')
    if (response.status === 404) throw new Error('This server does not have AI routing yet. Redeploy the backend, or use Check build status.')
    if (response.status === 429) throw new Error('The assistant is busy. Please try again shortly.')
    throw new Error('AI routing is unavailable. Check backend AWS access or try again shortly.')
  }
  return parseDecision(await response.json(), repository)
}

async function invoke(connection: McpConnection, call: ToolCall, signal: AbortSignal,
  onActivity: (activity: ToolActivity) => void) {
  if (!connection.tools.some((tool) => tool.name === call.name)) {
    throw new Error(`The connected MCP server does not provide ${call.name}.`)
  }
  onActivity({ name: call.name, state: 'running' })
  try {
    const result = await connection.client.callTool(call, { signal, timeout: 60000 })
    const text = result.content.filter((item) => item.type === 'text').map((item) => item.text).join('\n').trim()
    if (result.isError || !text) throw new Error(text || 'The tool returned no readable response.')
    onActivity({ name: call.name, state: 'complete' })
    return text
  } catch (error) {
    if (!signal.aborted) onActivity({ name: call.name, state: 'error' })
    throw error
  }
}

export async function askAssistant(connection: McpConnection, repository: string, question: string,
  history: AssistantTurn[], contextRunId: number | null, signal: AbortSignal,
  onActivity: (activity: ToolActivity) => void, directStatus = false): Promise<AssistantAnswer> {
  const [owner, repo] = repository.split('/')
  const decision = directStatus
    ? { message: '', toolCalls: [{ name: 'get_build_status', arguments: { owner, repo } }] }
    : await chooseTool(connection, repository, question, history, contextRunId, signal)
  signal.throwIfAborted()
  if (!decision.toolCalls.length) return { text: decision.message, runId: contextRunId, rawLogs: false }

  const call = decision.toolCalls[0]
  let runId = validId(call.arguments.runId) ? call.arguments.runId : null
  if (['get_failure_logs', 'summarize_failure'].includes(call.name) && runId === null) {
    const failures = await invoke(connection, { name: 'get_failed_runs', arguments: { owner, repo, limit: 1 } }, signal, onActivity)
    runId = firstFailedRunId(failures)
    if (runId === null) return { text: failures, runId: null, rawLogs: false }
    call.arguments.runId = runId
  }
  const text = await invoke(connection, call, signal, onActivity)
  if (call.name === 'get_failed_runs') runId = firstFailedRunId(text)
  return { text, runId: call.name === 'get_failed_runs' ? runId : runId ?? contextRunId, rawLogs: call.name === 'get_failure_logs' }
}
