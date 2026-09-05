import { useCallback, useEffect, useState } from 'react'
import { listConnections, updateConnectionStatus } from '../api'
import type { ConnectionResponse, ConnectionStatus } from '../types'

const PAGE_SIZE = 20

export default function ConnectionsView() {
  const [status, setStatus] = useState<ConnectionStatus | ''>('')
  const [page, setPage] = useState(0)
  const [connections, setConnections] = useState<ConnectionResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    listConnections({ page, size: PAGE_SIZE, status })
      .then((data) => {
        setConnections(data.content)
        setTotal(data.totalElements)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [page, status, reloadKey])

  useEffect(() => {
    load()
  }, [load])

  function handleStatus(id: number, next: ConnectionStatus) {
    updateConnectionStatus(id, next)
      .then(() => setReloadKey((k) => k + 1))
      .catch((e: Error) => setError(e.message))
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="panel">
      <div className="card toolbar">
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value as ConnectionStatus | '')
            setPage(0)
          }}
        >
          <option value="">全部状态</option>
          <option value="PENDING">PENDING（待确认）</option>
          <option value="CONFIRMED">CONFIRMED</option>
          <option value="IGNORED">IGNORED</option>
        </select>
        <button className="btn" onClick={() => setReloadKey((k) => k + 1)}>
          刷新
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card">
        {loading ? (
          <div className="empty">加载中…</div>
        ) : connections.length === 0 ? (
          <div className="empty">暂无连接。创建语义相近的知识原子后，系统会自动发现并显示在这里。</div>
        ) : (
          connections.map((c) => (
            <div key={c.id} className="atom-item">
              <div className="conn">
                <span className="badge sim">{(c.similarity * 100).toFixed(1)}%</span>
                <span>
                  <strong>{c.sourceText}</strong>
                  <span className="arrow"> → </span>
                  <strong>{c.targetText}</strong>
                </span>
                <span className="badge">{c.status}</span>
              </div>
              <div className="meta">
                <span>{c.reason}</span>
                <span>{new Date(c.createdAt).toLocaleString()}</span>
              </div>
              {c.status === 'PENDING' && (
                <div className="toolbar" style={{ marginTop: 6 }}>
                  <button className="btn primary" onClick={() => handleStatus(c.id, 'CONFIRMED')}>
                    确认
                  </button>
                  <button className="btn" onClick={() => handleStatus(c.id, 'IGNORED')}>
                    忽略
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      <div className="pagination">
        <button className="btn" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          上一页
        </button>
        <span>
          第 {page + 1} / {totalPages} 页 · 共 {total} 条
        </span>
        <button className="btn" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
          下一页
        </button>
      </div>
    </div>
  )
}
