import { useEffect, useRef, useState } from 'react'
import SetupScreen from './features/connection/SetupScreen'
import VoiceScreen from './features/voice/VoiceScreen'
import type { McpConnection } from './lib/mcpClient'

function App() {
  const [connection, setConnection] = useState<McpConnection | null>(null)
  const connectionRef = useRef<McpConnection | null>(null)
  const [screen, setScreen] = useState<'setup' | 'voice'>('setup')

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

  if (screen === 'voice' && connection) {
    return (
      <VoiceScreen
        connection={connection}
        onBack={() => setScreen('setup')}
      />
    )
  }

  return (
    <SetupScreen
      connection={connection}
      onConnected={handleConnected}
      onStart={() => setScreen('voice')}
    />
  )
}

export default App
