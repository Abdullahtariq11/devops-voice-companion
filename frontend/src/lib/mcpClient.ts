import {
  Client,
  StreamableHTTPClientTransport,
} from '@modelcontextprotocol/client'

export type McpConnection = Awaited<ReturnType<typeof connectToMcpServer>>

export async function connectToMcpServer(
  serverUrl: string,
  authToken = '',
) {
  const client = new Client({
    name: 'failwhisperer',
    version: '0.1.0',
  })

  const headers = new Headers()

  if (authToken.trim()) {
    headers.set('Authorization', `Bearer ${authToken.trim()}`)
  }

  const transport = new StreamableHTTPClientTransport(
    new URL(serverUrl.trim()),
    {
      requestInit: {
        headers,
        credentials: 'omit',
        redirect: 'error',
      },
    },
  )

  try {
    await client.connect(transport)

    const { tools } = await client.listTools()

    return {
      client, tools, serverUrl: new URL(serverUrl.trim()).href,
      getAssistantHeaders: () => new Headers(headers),
    }
  } catch (error) {
    await client.close().catch(() => undefined)
    throw error
  }
}
