import { useCallback, useEffect, useRef, useState } from 'react'
import type { DragEvent as ReactDragEvent, FormEvent, MouseEvent } from 'react'
import { useVirtualizer } from '@tanstack/react-virtual'
import { connectAtoms, createAtom, deleteAtom, listAtomConnections, listAtoms } from '../api'
import type { AtomResponse, ConnectionResponse, ContentType } from '../types'
import {
  AlertIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  PlusIcon,
  RefreshIcon,
  SearchIcon,
  TrashIcon,
  XIcon,
} from './Icons'
import { useSettings } from '../settings'
import { SkeletonList } from './Skeleton'
import { EmptyState } from './EmptyState'

const PAGE_SIZE = 50

interface Snackbar {
  message: string
  action?: { label: string; run: () => void }
}

export default function AtomsView() {
  const { t } = useSettings()
  const [keyword, setKeyword] = useState('')
  const [debouncedKeyword, setDebouncedKeyword] = useState('')
  const [contentType, setContentType] = useState<ContentType | ''>('')
  const [atoms, setAtoms] = useState<AtomResponse[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [loadingMore, setLoadingMore] = useState(false)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const [newText, setNewText] = useState('')
  const [newType, setNewType] = useState<ContentType>('TEXT')
  const composerRef = useRef<HTMLTextAreaElement>(null)
  const searchRef = useRef<HTMLInputElement>(null)

  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [connections, setConnections] = useState<ConnectionResponse[]>([])
  const [connLoading, setConnLoading] = useState(false)

  const [dragId, setDragId] = useState<number | null>(null)
  const [dropTargetId, setDropTargetId] = useState<number | null>(null)

  const [snack, setSnack] = useState<Snackbar | null>(null)
  const snackTimer = useRef<number | null>(null)

  const parentRef = useRef<HTMLDivElement>(null)
  const rowVirtualizer = useVirtualizer({
    count: atoms.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 96,
    measureElement: (el) => el.getBoundingClientRect().height,
    overscan: 6,
  })

  // Debounce the search box so typing doesn't fire a request per keystroke.
  useEffect(() => {
    const id = window.setTimeout(() => setDebouncedKeyword(keyword), 250)
    return () => window.clearTimeout(id)
  }, [keyword])

  const load = useCallback(() => {
    setLoading(true)
    setError(null)
    listAtoms({ page: 0, size: PAGE_SIZE, keyword: debouncedKeyword, contentType })
      .then((data) => {
        setAtoms(data.content)
        setTotal(data.totalElements)
        setHasMore(data.content.length < data.totalElements)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoading(false))
  }, [debouncedKeyword, contentType, reloadKey])

  useEffect(() => {
    load()
  }, [load])

  const loadMore = useCallback(() => {
    if (loading || loadingMore || !hasMore) return
    setLoadingMore(true)
    const nextPage = Math.floor(atoms.length / PAGE_SIZE)
    listAtoms({ page: nextPage, size: PAGE_SIZE, keyword: debouncedKeyword, contentType })
      .then((data) => {
        const seen = new Set(atoms.map((a) => a.id))
        const merged = atoms.concat(data.content.filter((a) => !seen.has(a.id)))
        setAtoms(merged)
        setHasMore(merged.length < data.totalElements)
        setTotal(data.totalElements)
      })
      .catch((e: Error) => setError(e.message))
      .finally(() => setLoadingMore(false))
  }, [loading, loadingMore, hasMore, atoms, debouncedKeyword, contentType])

  // Infinite scroll: load the next page when the user nears the bottom.
  useEffect(() => {
    const el = parentRef.current
    if (!el) return
    const onScroll = () => {
      const items = rowVirtualizer.getVirtualItems()
      if (items.length && items[items.length - 1].index >= atoms.length - 6) {
        loadMore()
      }
    }
    el.addEventListener('scroll', onScroll, { passive: true })
    return () => el.removeEventListener('scroll', onScroll)
  }, [rowVirtualizer, atoms.length, loadMore])

  // Cross-component commands (command palette → focus composer / search).
  useEffect(() => {
    const onFocusComposer = () => composerRef.current?.focus()
    const onSearch = (e: Event) => {
      const detail = (e as CustomEvent).detail as { keyword?: string }
      if (detail?.keyword) setKeyword(detail.keyword)
      searchRef.current?.focus()
    }
    window.addEventListener('aether:focus-composer', onFocusComposer)
    window.addEventListener('aether:search', onSearch)
    return () => {
      window.removeEventListener('aether:focus-composer', onFocusComposer)
      window.removeEventListener('aether:search', onSearch)
    }
  }, [])

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
  }, [expandedId])

  useEffect(() => {
    return () => {
      if (snackTimer.current) window.clearTimeout(snackTimer.current)
    }
  }, [])

  function showSnack(next: Snackbar) {
    if (snackTimer.current) window.clearTimeout(snackTimer.current)
    setSnack(next)
    snackTimer.current = window.setTimeout(() => setSnack(null), 5000)
  }

  // Optimistic create: prepend a temp row, reconcile from the server on success.
  function handleCreate(e: FormEvent) {
    e.preventDefault()
    const text = newText.trim()
    if (!text) return
    const tempId = -Date.now()
    const temp: AtomResponse = {
      id: tempId,
      contentText: text,
      contentType: newType,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      version: 0,
    }
    setNewText('')
    setAtoms((prev) => [temp, ...prev])
    setTotal((v) => v + 1)
    createAtom({ contentText: text, contentType: newType })
      .then(() => setReloadKey((k) => k + 1))
      .catch((err: Error) => {
        setAtoms((prev) => prev.filter((a) => a.id !== tempId))
        setTotal((v) => Math.max(0, v - 1))
        setError(err.message)
      })
  }

  // Optimistic delete: remove immediately, offer undo.
  function handleDelete(atom: AtomResponse) {
    setAtoms((prev) => prev.filter((a) => a.id !== atom.id))
    setTotal((v) => Math.max(0, v - 1))
    deleteAtom(atom.id)
      .then(() => {
        showSnack({
          message: t('snackbar.deleted'),
          action: {
            label: t('snackbar.undo'),
            run: () => {
              createAtom({ contentText: atom.contentText, contentType: atom.contentType })
                .then(() => setReloadKey((k) => k + 1))
                .catch((err: Error) => setError(err.message))
            },
          },
        })
      })
      .catch((err: Error) => {
        setReloadKey((k) => k + 1)
        setError(err.message)
      })
  }

  function toggleExpand(id: number) {
    setExpandedId((cur) => (cur === id ? null : id))
  }

  function handleSpotlight(e: MouseEvent<HTMLDivElement>) {
    const el = e.currentTarget
    const rect = el.getBoundingClientRect()
    el.style.setProperty('--mx', `${e.clientX - rect.left}px`)
    el.style.setProperty('--my', `${e.clientY - rect.top}px`)
  }

  function handleDragStart(e: ReactDragEvent<HTMLDivElement>, atom: AtomResponse) {
    setDragId(atom.id)
    e.dataTransfer.effectAllowed = 'link'
    e.dataTransfer.setData('text/plain', String(atom.id))
  }

  function handleDragOver(e: ReactDragEvent<HTMLDivElement>, id: number) {
    if (dragId == null || dragId === id) return
    e.preventDefault()
    e.dataTransfer.dropEffect = 'link'
    setDropTargetId(id)
  }

  function handleDrop(e: ReactDragEvent<HTMLDivElement>, target: AtomResponse) {
    e.preventDefault()
    const sourceId = dragId ?? Number(e.dataTransfer.getData('text/plain') || 0)
    setDragId(null)
    setDropTargetId(null)
    if (!sourceId || sourceId === target.id) return
    connectAtoms(sourceId, target.id)
      .then(() => showSnack({ message: t('connections.created') }))
      .catch((err: Error) => showSnack({ message: err.message || t('connections.createFailed') }))
  }

  return (
    <div className="panel">
      <form className="card composer" onSubmit={handleCreate}>
        <div className="composer-head">
          <span className="card-title">{t('atoms.composer.title')}</span>
          <span className="composer-tip">{t('atoms.composer.tip')}</span>
        </div>
        <textarea
          ref={composerRef}
          placeholder={t('atoms.composer.placeholder')}
          value={newText}
          onChange={(e) => setNewText(e.target.value)}
        />
        <div className="form-actions">
          <select value={newType} onChange={(e) => setNewType(e.target.value as ContentType)}>
            <option value="TEXT">TEXT</option>
            <option value="MARKDOWN">MARKDOWN</option>
            <option value="IMAGE_URL">IMAGE_URL</option>
          </select>
          <button className="btn primary" type="submit">
            <PlusIcon size={16} />
            {t('atoms.composer.submit')}
          </button>
        </div>
      </form>

      <div className="card toolbar">
        <div className="search-field">
          <SearchIcon size={16} className="search-icon" />
          <input
            ref={searchRef}
            type="text"
            placeholder={t('atoms.search.placeholder')}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <select
          value={contentType}
          onChange={(e) => setContentType(e.target.value as ContentType | '')}
        >
          <option value="">{t('atoms.filter.all')}</option>
          <option value="TEXT">TEXT</option>
          <option value="MARKDOWN">MARKDOWN</option>
          <option value="IMAGE_URL">IMAGE_URL</option>
        </select>
        <button className="btn ghost" onClick={() => setReloadKey((k) => k + 1)}>
          <RefreshIcon size={16} />
          {t('common.refresh')}
        </button>
        <span className="count">{t('common.total', { total })}</span>
      </div>

      {error && (
        <div className="error" role="alert">
          <AlertIcon size={16} />
          <span>{error}</span>
        </div>
      )}

      <div className="card list virtual-card">
        {loading ? (
          <SkeletonList rows={6} />
        ) : atoms.length === 0 ? (
          <EmptyState>
            <span>{t('atoms.empty')}</span>
          </EmptyState>
        ) : (
          <div ref={parentRef} className="virtual-list">
            <div style={{ height: rowVirtualizer.getTotalSize(), position: 'relative', width: '100%' }}>
              {rowVirtualizer.getVirtualItems().map((vi) => {
                const atom = atoms[vi.index]
                return (
                  <div
                    key={atom.id}
                    data-index={vi.index}
                    ref={rowVirtualizer.measureElement}
                    style={{
                      position: 'absolute',
                      top: 0,
                      left: 0,
                      width: '100%',
                      transform: `translateY(${vi.start}px)`,
                    }}
                  >
                    <div
                      className={`atom-item${dragId === atom.id ? ' dragging' : ''}${dropTargetId === atom.id ? ' drop-target' : ''}`}
                      draggable
                      onDragStart={(e) => handleDragStart(e, atom)}
                      onDragOver={(e) => handleDragOver(e, atom.id)}
                      onDragLeave={() => setDropTargetId((cur) => (cur === atom.id ? null : cur))}
                      onDrop={(e) => handleDrop(e, atom)}
                      onDragEnd={() => setDragId(null)}
                      onMouseMove={handleSpotlight}
                    >
                      <div className="atom-main">
                        <button
                          type="button"
                          className="atom-body"
                          onClick={() => toggleExpand(atom.id)}
                          title={expandedId === atom.id ? t('atoms.collapseConnections') : t('atoms.viewConnections')}
                        >
                          <div className="atom-content">{atom.contentText}</div>
                          <div className="meta">
                            <span className="badge type">{atom.contentType}</span>
                            <span className="meta-item mono">#{atom.id}</span>
                            <span className="meta-item mono">v{atom.version}</span>
                            <span className="meta-item">{new Date(atom.updatedAt).toLocaleString()}</span>
                          </div>
                        </button>
                        <div className="atom-actions">
                          <button className="btn ghost" onClick={() => toggleExpand(atom.id)}>
                            {expandedId === atom.id ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
                            {expandedId === atom.id ? t('atoms.collapse') : t('atoms.connections')}
                          </button>
                          <button
                            className="btn danger square"
                            onClick={() => handleDelete(atom)}
                            title={t('common.delete')}
                            aria-label={t('common.delete')}
                          >
                            <TrashIcon size={16} />
                          </button>
                        </div>
                      </div>
                      {expandedId === atom.id && (
                        <div className="conn-list">
                          {connLoading ? (
                            <div className="empty">{t('atoms.loadingConnections')}</div>
                          ) : connections.length === 0 ? (
                            <div className="empty">{t('atoms.noConnections')}</div>
                          ) : (
                            connections.map((c) => (
                              <div key={c.id} className="conn">
                                <span className="badge sim">{(c.similarity * 100).toFixed(1)}%</span>
                                <span className="conn-path">
                                  <span>{c.sourceAtomId === atom.id ? c.targetText : c.sourceText}</span>
                                  <span className="arrow">→</span>
                                  <span>{c.sourceAtomId === atom.id ? c.sourceText : c.targetText}</span>
                                </span>
                              </div>
                            ))
                          )}
                        </div>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
            {loadingMore && <div className="load-more">{t('common.loading')}</div>}
          </div>
        )}
      </div>

      {snack && (
        <div className="snackbar" role="status">
          <span>{snack.message}</span>
          {snack.action && (
            <button className="snackbar-action" onClick={snack.action.run}>
              {snack.action.label}
            </button>
          )}
          <button className="snackbar-close" onClick={() => setSnack(null)} aria-label={t('window.close')}>
            <XIcon size={14} />
          </button>
        </div>
      )}
    </div>
  )
}
