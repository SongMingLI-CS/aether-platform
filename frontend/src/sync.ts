import {
  connectAtomsRaw,
  createAtomRaw,
  deleteAtomRaw,
  fetchAllAtoms,
  fetchAllConnections,
  updateConnectionStatusRaw,
} from './api'
import { getMeta, listMutations, removeMutation, replaceAtoms, replaceConnections, setMeta } from './localdb'

export type SyncStatus = 'idle' | 'syncing' | 'offline' | 'error'

export interface SyncSnapshot {
  status: SyncStatus
  pending: number
  lastSyncAt: number | null
}

type Listener = (snapshot: SyncSnapshot) => void

let started = false
let status: SyncStatus = 'idle'
let pending = 0
let lastSyncAt: number | null = null
const listeners = new Set<Listener>()

function emit() {
  const snapshot: SyncSnapshot = { status, pending, lastSyncAt }
  listeners.forEach((listener) => listener(snapshot))
}

function isOnline(): boolean {
  return typeof navigator === 'undefined' || navigator.onLine
}

async function refreshPending() {
  pending = (await listMutations()).length
}

/**
 * Pull the full atom/connection mirror from the backend into the local store.
 * This is what makes reads offline-capable: once pulled, every read can be
 * served from IndexedDB without the network.
 */
export async function pull(): Promise<void> {
  if (!isOnline()) {
    status = 'offline'
    emit()
    return
  }
  status = 'syncing'
  emit()
  try {
    const [atoms, connections] = await Promise.all([fetchAllAtoms(), fetchAllConnections()])
    await replaceAtoms(atoms)
    await replaceConnections(connections)
    lastSyncAt = Date.now()
    await setMeta('lastSyncAt', String(lastSyncAt))
    status = 'idle'
  } catch {
    status = 'error'
  }
  emit()
}

async function loadIdMap(): Promise<Map<number, number>> {
  const raw = await getMeta('tempIdMap')
  if (!raw) return new Map()
  try {
    return new Map<number, number>(JSON.parse(raw) as [number, number][])
  } catch {
    return new Map()
  }
}

async function saveIdMap(map: Map<number, number>): Promise<void> {
  await setMeta('tempIdMap', JSON.stringify(Array.from(map.entries())))
}

/**
 * Replay queued mutations to the backend in order, reconciling temporary
 * negative ids (offline-created atoms/connections) with their server ids.
 * A persistent tempId -> realId map survives partial flushes; after a full
 * replay the local mirror is refreshed from the server.
 */
export async function flush(): Promise<void> {
  if (!isOnline()) return
  const mutations = await listMutations()
  if (mutations.length === 0) return
  status = 'syncing'
  emit()

  const idMap = await loadIdMap()
  let processed = 0

  for (const mutation of mutations) {
    try {
      if (mutation.kind === 'CREATE_ATOM') {
        const { tempId, contentText, contentType } = mutation.payload
        const created = await createAtomRaw({ contentText, contentType })
        idMap.set(tempId, created.id)
      } else if (mutation.kind === 'CONNECT_ATOMS') {
        const { sourceAtomId, targetAtomId } = mutation.payload
        await connectAtomsRaw(idMap.get(sourceAtomId) ?? sourceAtomId, idMap.get(targetAtomId) ?? targetAtomId)
      } else if (mutation.kind === 'DELETE_ATOM') {
        await deleteAtomRaw(idMap.get(mutation.payload.id) ?? mutation.payload.id)
      } else if (mutation.kind === 'UPDATE_CONNECTION_STATUS') {
        await updateConnectionStatusRaw(idMap.get(mutation.payload.id) ?? mutation.payload.id, mutation.payload.status)
      }
      await removeMutation(mutation.id as number)
      processed++
    } catch {
      break
    }
  }

  await saveIdMap(processed === mutations.length ? new Map() : idMap)
  await refreshPending()

  if (processed > 0) {
    // Refresh the mirror from the (now updated) server for a consistent view.
    await pull()
    return
  }
  status = isOnline() ? 'idle' : 'offline'
  emit()
}

async function syncNow() {
  await pull()
  await flush()
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  listener({ status, pending, lastSyncAt })
  return () => {
    listeners.delete(listener)
  }
}

export function startSync(): void {
  if (started) return
  started = true

  if (!isOnline()) {
    status = 'offline'
    emit()
  }

  window.addEventListener('online', () => void syncNow())
  window.addEventListener('offline', () => {
    status = 'offline'
    emit()
  })

  void refreshPending().then(emit)
  void syncNow()

  // Background poll: keep the pending count fresh and catch up while online.
  window.setInterval(() => {
    void refreshPending().then(emit)
    if (isOnline()) void flush()
  }, 5000)
}
