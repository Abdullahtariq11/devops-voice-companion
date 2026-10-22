import { useEffect, useRef, useState } from 'react'
import SetupScreen from './features/connection/SetupScreen'
import type { McpConnection } from './lib/mcpClient'

function App() {
  const [connection, setConnection] = useState<McpConnection | null>(null)
  const connectionRef = useRef<McpConnection | null>(null)

  useEffect(() => {
    return () => {
      if (connectionRef.current) {
        void connectionRef.current.client.close().catch(() => undefined)
      }
    }
  }, [])

  function handleConnected(result: McpConnection) {
    if (connectionRef.current && connectionRef.current !== result) {
      void connectionRef.current.client.close().catch(() => undefined)
    }

    connectionRef.current = result
    setConnection(result)
  }

  return <SetupScreen connection={connection} onConnected={handleConnected} />
}

export default App
