import { useCallback, useEffect, useState } from 'react'
import { listConnections, updateConnectionStatus } from '../api'
import type { ConnectionResponse, ConnectionStatus } from '../types'
import type { TranslationKey } from '../i18n'
import {
  AlertIcon,
  CheckIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
  RefreshIcon,
  XIcon,
} from './Icons'
import { useSettings } from '../settings'
import { SkeletonList } from './Skeleton'
import { EmptyState } from './EmptyState'

const PAGE_SIZE = 20

const STATUS_LABEL_KEY: Record<ConnectionStatus, TranslationKey> = {
  PENDING: 'connections.status.pending',
  CONFIRMED: 'connections.status.confirmed',
  IGNORED: 'connections.status.ignored',
}

const STATUS_CLASS: Record<ConnectionStatus, string> = {
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  IGNORED: 'ignored',
}

export default function ConnectionsView() {
  const { t } = useSettings()
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
    const prev = connections.find((c) => c.id === id)
    if (!prev || prev.status === next) return
    // Optimistic: when a status filter is active and the new status no longer
    // matches it, remove the row; otherwise just flip its status. Roll back by
    // reloading from the server on failure.
    const shouldHide = status !== '' && status !== next
    setConnections((list) =>
      shouldHide
        ? list.filter((c) => c.id !== id)
        : list.map((c) => (c.id === id ? { ...c, status: next } : c)),
    )
    if (shouldHide) setTotal((v) => Math.max(0, v - 1))
    updateConnectionStatus(id, next).catch((e: Error) => {
      setReloadKey((k) => k + 1)
      setError(e.message)
    })
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
          <option value="">{t('connections.filter.all')}</option>
          <option value="PENDING">{t('connections.status.pending')}</option>
          <option value="CONFIRMED">{t('connections.status.confirmed')}</option>
          <option value="IGNORED">{t('connections.status.ignored')}</option>
        </select>
        <button className="btn ghost" onClick={() => setReloadKey((k) => k + 1)}>
          <RefreshIcon size={16} />
          {t('common.refresh')}
        </button>
      </div>

      {error && (
        <div className="error" role="alert">
          <AlertIcon size={16} />
          <span>{error}</span>
        </div>
      )}

      <div className="card list">
        {loading ? (
          <SkeletonList rows={4} />
        ) : connections.length === 0 ? (
          <EmptyState>
            <span>{t('connections.empty')}</span>
          </EmptyState>
        ) : (
          connections.map((c) => (
            <div key={c.id} className="atom-item">
              <div className="conn">
                <span className="badge sim">{(c.similarity * 100).toFixed(1)}%</span>
                <span className="conn-path">
                  <strong>{c.sourceText}</strong>
                  <span className="arrow">→</span>
                  <strong>{c.targetText}</strong>
                </span>
                <span className={`badge ${STATUS_CLASS[c.status]}`}>{t(STATUS_LABEL_KEY[c.status])}</span>
                {c.origin === 'MANUAL' && (
                  <span className="badge manual">{t('connections.origin.manual')}</span>
                )}
              </div>
              <div className="meta">
                <span className="meta-item">{c.reason}</span>
                <span className="meta-item">{new Date(c.createdAt).toLocaleString()}</span>
              </div>
              {c.status === 'PENDING' && (
                <div className="conn-actions">
                  <button className="btn primary" onClick={() => handleStatus(c.id, 'CONFIRMED')}>
                    <CheckIcon size={16} />
                    {t('connections.confirm')}
                  </button>
                  <button className="btn" onClick={() => handleStatus(c.id, 'IGNORED')}>
                    <XIcon size={16} />
                    {t('connections.ignore')}
                  </button>
                </div>
              )}
            </div>
          ))
        )}
      </div>

      <div className="pagination">
        <button className="btn" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
          <ChevronLeftIcon size={16} />
          {t('common.prev')}
        </button>
        <span>{t('common.pagination', { page: page + 1, pages: totalPages, total })}</span>
        <button className="btn" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>
          {t('common.next')}
          <ChevronRightIcon size={16} />
        </button>
      </div>
    </div>
  )
}
