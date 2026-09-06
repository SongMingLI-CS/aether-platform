import { useEffect, useState } from 'react'
import { subscribe, type SyncSnapshot } from '../sync'
import { useSettings } from '../settings'

/**
 * Local-first sync indicator: whether the local mirror is up to date, how many
 * writes are still queued, and whether we are currently offline.
 */
export default function SyncStatus() {
  const { t } = useSettings()
  const [snap, setSnap] = useState<SyncSnapshot>({ status: 'idle', pending: 0, lastSyncAt: null })

  useEffect(() => subscribe(setSnap), [])

  let label: string
  let dotClass: string
  if (snap.status === 'offline') {
    label = snap.pending > 0 ? t('sync.offlinePending', { n: snap.pending }) : t('sync.offline')
    dotClass = 'dot bad'
  } else if (snap.status === 'syncing') {
    label = t('sync.syncing')
    dotClass = 'dot pending'
  } else if (snap.status === 'error') {
    label = t('sync.error')
    dotClass = 'dot bad'
  } else if (snap.pending > 0) {
    label = t('sync.pending', { n: snap.pending })
    dotClass = 'dot pending'
  } else {
    label = t('sync.synced')
    dotClass = 'dot ok'
  }

  return (
    <div className="status" aria-live="polite" title={label}>
      <span className={dotClass} />
      <span>{label}</span>
    </div>
  )
}
