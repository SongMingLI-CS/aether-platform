import { useEffect, useState } from 'react'
import './App.css'
import AtomsView from './components/AtomsView'
import ConnectionsView from './components/ConnectionsView'

type BackendState = 'loading' | 'online' | 'offline'
type Tab = 'atoms' | 'connections'

export default function App() {
  const [state, setState] = useState<BackendState>('loading')
  const [ping, setPing] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('atoms')

  useEffect(() => {
    fetch('/api/ping')
      .then((res) => res.json())
      .then((data: { code: number; data?: { status?: string } }) => {
        setPing(data.data?.status ?? '')
        setState(data.code === 0 ? 'online' : 'offline')
      })
      .catch(() => setState('offline'))
  }, [])

  return (
    <main className="app">
      <header className="top">
        <div>
          <h1>Aether Platform</h1>
          <p className="subtitle">本地优先 · 知识原子 · 主动连接发现</p>
        </div>
        <div className="status" aria-live="polite">
          <span className={state === 'online' ? 'dot ok' : state === 'loading' ? 'dot pending' : 'dot bad'} />
          <span>
            {state === 'loading' && '检测中…'}
            {state === 'online' && `后端在线：${ping}`}
            {state === 'offline' && '后端不可达（请确认 8080 端口服务已启动）'}
          </span>
        </div>
      </header>

      <nav className="tabs">
        <button className={tab === 'atoms' ? 'active' : ''} onClick={() => setTab('atoms')}>
          知识原子
        </button>
        <button className={tab === 'connections' ? 'active' : ''} onClick={() => setTab('connections')}>
          连接发现
        </button>
      </nav>

      {tab === 'atoms' ? <AtomsView /> : <ConnectionsView />}
    </main>
  )
}

