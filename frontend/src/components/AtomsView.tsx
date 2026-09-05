import { useCallback, useEffect, useState } from 'react'
import { createAtom, deleteAtom, listAtomConnections, listAtoms } from '../api'
import type { AtomResponse, ConnectionResponse, ContentType } from '../types'

const PAGE_SIZE = 20

export default function AtomsView() {
  const [keyword, setKeyword] = useState('')
  const [contentType, setContentType] = useState<ContentType | ''>('')
  const [page, setPage] = useState(0)
  const [atoms, setAtoms] = useState<AtomResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const [newText, setNewText] = useState('')
  const [newType, setNewType] = useState<ContentType>('TEXT')

  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [connections, setConnections] = useState<ConnectionResponse[]>([])
  const [connLoading, setConnLoading] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    listAtoms({ page, size: PAGE_SIZE, keyword, contentType })
      .then((data) => {
        setAtoms(data.content)
        setTotal(data.totalElements)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [page, keyword, contentType, reloadKey])

  useEffect(() => {
    load()
  }, [load])

  useEffect(() => {
    if (expandedId == null) {
      setConnections([])
      return
    }
    setConnLoading(true)
    listAtomConnections(expandedId, { page: 0, size: 10 })
      .then((data) => setConnections(data.content))
      .catch(() => setConnections([]))
      .finally(() => setConnLoading(false))
  }, [expandedId, reloadKey])

  function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (!newText.trim()) return
    createAtom({ contentText: newText.trim(), contentType: newType })
      .then(() => {
        setNewText('')
        setReloadKey((k) => k + 1)
      })
      .catch((err: Error) => setError(err.message))
  }

  function handleDelete(id: number) {
    if (!window.confirm('删除该知识原子？（逻辑删除）')) return
    deleteAtom(id)
      .then(() => setReloadKey((k) => k + 1))
      .catch((err: Error) => setError(err.message))
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  return (
    <div className="panel">
      <form className="card form" onSubmit={handleCreate}>
        <textarea
          placeholder="输入新的知识原子（纯文本 / Markdown）…"
          value={newText}
          onChange={(e) => setNewText(e.target.value)}
        />
        <select value={newType} onChange={(e) => setNewType(e.target.value as ContentType)}>
          <option value="TEXT">TEXT</option>
          <option value="MARKDOWN">MARKDOWN</option>
          <option value="IMAGE_URL">IMAGE_URL</option>
        </select>
        <button className="btn primary" type="submit">
          创建原子
        </button>
      </form>

      <div className="card toolbar">
        <input
          type="text"
          placeholder="搜索关键词…"
          value={keyword}
          onChange={(e) => {
            setKeyword(e.target.value)
            setPage(0)
          }}
        />
        <select
          value={contentType}
          onChange={(e) => {
            setContentType(e.target.value as ContentType | '')
            setPage(0)
          }}
        >
          <option value="">全部类型</option>
          <option value="TEXT">TEXT</option>
          <option value="MARKDOWN">MARKDOWN</option>
          <option value="IMAGE_URL">IMAGE_URL</option>
        </select>
        <button className="btn" onClick={() => setReloadKey((k) => k + 1)}>
          刷新
        </button>
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card">
        {loading ? (
          <div className="empty">加载中…</div>
        ) : atoms.length === 0 ? (
          <div className="empty">暂无知识原子，先在上方创建一条吧。</div>
        ) : (
          atoms.map((atom) => (
            <div key={atom.id} className="atom-item">
              <div
                style={{ cursor: 'pointer' }}
                onClick={() => setExpandedId(expandedId === atom.id ? null : atom.id)}
              >
                <div>{atom.contentText}</div>
                <div className="meta">
                  <span className="badge type">{atom.contentType}</span>
                  <span>#{atom.id}</span>
                  <span>v{atom.version}</span>
                  <span>{new Date(atom.updatedAt).toLocaleString()}</span>
                </div>
              </div>
              <div className="toolbar" style={{ marginTop: 6 }}>
                <button
                  className="btn"
                  onClick={() => setExpandedId(expandedId === atom.id ? null : atom.id)}
                >
                  {expandedId === atom.id ? '收起连接' : '查看连接'}
                </button>
                <button className="btn danger" onClick={() => handleDelete(atom.id)}>
                  删除
                </button>
              </div>
              {expandedId === atom.id && (
                <div className="conn-list">
                  {connLoading ? (
                    <div className="empty">加载连接…</div>
                  ) : connections.length === 0 ? (
                    <div className="empty">暂无相关连接。</div>
                  ) : (
                    connections.map((c) => (
                      <div key={c.id} className="conn">
                        <span className="badge sim">{(c.similarity * 100).toFixed(1)}%</span>
                        <span>
                          {c.sourceAtomId === atom.id ? c.targetText : c.sourceText}
                          <span className="arrow"> → </span>
                          {c.sourceAtomId === atom.id ? c.sourceText : c.targetText}
                        </span>
                      </div>
                    ))
                  )}
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

