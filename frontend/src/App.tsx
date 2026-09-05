import { useEffect, useState } from 'react'
import './App.css'

interface PingResponse {
  code: number
  message: string
  data: { status: string }
  timestamp: string
}

type BackendState = 'loading' | 'online' | 'offline'

export default function App() {
  const [state, setState] = useState<BackendState>('loading')
  const [ping, setPing] = useState<PingResponse | null>(null)

  useEffect(() => {
    fetch('/api/ping')
      .then((res) => res.json())
      .then((data: PingResponse) => {
        setPing(data)
        setState(data.code === 0 ? 'online' : 'offline')
      })
      .catch(() => setState('offline'))
  }, [])

  return (
    <main className="container">
      <h1>Aether Platform</h1>
      <p className="subtitle">本地优先 · 知识原子 · 主动连接发现</p>

      <section className="status" aria-live="polite">
        <span
          className={
            state === 'online' ? 'dot ok' : state === 'loading' ? 'dot pending' : 'dot bad'
          }
        />
        <span>
          {state === 'loading' && '正在检测后端…'}
          {state === 'online' && `后端在线：${ping?.data.status ?? ''}`}
          {state === 'offline' && '后端不可达（请确认 8080 端口的 aether-backend 已启动）'}
        </span>
      </section>

      {ping && <pre className="response">{JSON.stringify(ping, null, 2)}</pre>}

      <footer className="hint">
        当前为前端脚手架。后端知识原子 API（/api/v1/atoms）将在阶段 1 开放，
        届时本页面将提供知识原子的管理视图。
      </footer>
    </main>
  )
}
