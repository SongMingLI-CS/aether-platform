import type { AtomResponse, ConnectionResponse, ConnectionStatus, ContentType } from './types'

const DB_NAME = 'aether-local'
const DB_VERSION = 1

const STORE_ATOMS = 'atoms'
const STORE_CONNECTIONS = 'connections'
const STORE_OUTBOX = 'outbox'
const STORE_META = 'meta'

/**
 * A mutation queued for background replay to the backend while offline.
 * DELETE_ATOM / UPDATE_CONNECTION_STATUS carry known ids, so they are safe to
 * replay offline (no temporary-id reconciliation required).
 */
export type LocalMutation =
  | { id?: number; kind: 'CREATE_ATOM'; payload: { tempId: number; contentText: string; contentType: ContentType }; createdAt: number }
  | { id?: number; kind: 'DELETE_ATOM'; payload: { id: number }; createdAt: number }
  | { id?: number; kind: 'CONNECT_ATOMS'; payload: { tempConnectionId: number; sourceAtomId: number; targetAtomId: number }; createdAt: number }
  | { id?: number; kind: 'UPDATE_CONNECTION_STATUS'; payload: { id: number; status: ConnectionStatus }; createdAt: number }

let tempSeq = 0
export function createTempId(): number {
  return -(Date.now() + ++tempSeq)
}

let dbPromise: Promise<IDBDatabase> | null = null

function openDb(): Promise<IDBDatabase> {
  if (dbPromise) return dbPromise
  dbPromise = new Promise<IDBDatabase>((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION)
    req.onupgradeneeded = () => {
      const db = req.result
      if (!db.objectStoreNames.contains(STORE_ATOMS)) db.createObjectStore(STORE_ATOMS, { keyPath: 'id' })
      if (!db.objectStoreNames.contains(STORE_CONNECTIONS)) db.createObjectStore(STORE_CONNECTIONS, { keyPath: 'id' })
      if (!db.objectStoreNames.contains(STORE_OUTBOX)) db.createObjectStore(STORE_OUTBOX, { keyPath: 'id', autoIncrement: true })
      if (!db.objectStoreNames.contains(STORE_META)) db.createObjectStore(STORE_META, { keyPath: 'key' })
    }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
  return dbPromise
}

function requestOf<T>(req: IDBRequest<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

// ---- Atom mirror ----

export async function replaceAtoms(atoms: AtomResponse[]): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_ATOMS, 'readwrite')
    const store = tx.objectStore(STORE_ATOMS)
    store.clear()
    for (const atom of atoms) store.put(atom)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function getAllAtoms(): Promise<AtomResponse[]> {
  const db = await openDb()
  const tx = db.transaction(STORE_ATOMS, 'readonly')
  return requestOf(tx.objectStore(STORE_ATOMS).getAll() as IDBRequest<AtomResponse[]>)
}

export async function removeAtomLocal(id: number): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_ATOMS, 'readwrite')
  await requestOf(tx.objectStore(STORE_ATOMS).delete(id))
}

export async function putAtomLocal(atom: AtomResponse): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_ATOMS, 'readwrite')
  await requestOf(tx.objectStore(STORE_ATOMS).put(atom))
}

// ---- Connection mirror ----

export async function replaceConnections(connections: ConnectionResponse[]): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_CONNECTIONS, 'readwrite')
    const store = tx.objectStore(STORE_CONNECTIONS)
    store.clear()
    for (const c of connections) store.put(c)
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function getAllConnections(): Promise<ConnectionResponse[]> {
  const db = await openDb()
  const tx = db.transaction(STORE_CONNECTIONS, 'readonly')
  return requestOf(tx.objectStore(STORE_CONNECTIONS).getAll() as IDBRequest<ConnectionResponse[]>)
}

export async function updateConnectionLocal(id: number, patch: Partial<ConnectionResponse>): Promise<void> {
  const db = await openDb()
  await new Promise<void>((resolve, reject) => {
    const tx = db.transaction(STORE_CONNECTIONS, 'readwrite')
    const store = tx.objectStore(STORE_CONNECTIONS)
    const getReq = store.get(id)
    getReq.onsuccess = () => {
      const existing = getReq.result as ConnectionResponse | undefined
      if (existing) store.put({ ...existing, ...patch })
    }
    tx.oncomplete = () => resolve()
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}

export async function putConnectionLocal(connection: ConnectionResponse): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_CONNECTIONS, 'readwrite')
  await requestOf(tx.objectStore(STORE_CONNECTIONS).put(connection))
}

export async function removeConnectionLocal(id: number): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_CONNECTIONS, 'readwrite')
  await requestOf(tx.objectStore(STORE_CONNECTIONS).delete(id))
}

// ---- Outbox (mutation queue) ----

export async function enqueueMutation(mutation: LocalMutation): Promise<number> {
  const db = await openDb()
  const tx = db.transaction(STORE_OUTBOX, 'readwrite')
  return (await requestOf(tx.objectStore(STORE_OUTBOX).add(mutation))) as number
}

export async function listMutations(): Promise<LocalMutation[]> {
  const db = await openDb()
  const tx = db.transaction(STORE_OUTBOX, 'readonly')
  return requestOf(tx.objectStore(STORE_OUTBOX).getAll() as IDBRequest<LocalMutation[]>)
}

export async function removeMutation(id: number): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_OUTBOX, 'readwrite')
  await requestOf(tx.objectStore(STORE_OUTBOX).delete(id))
}

// ---- Meta ----

export async function getMeta(key: string): Promise<string | null> {
  const db = await openDb()
  const tx = db.transaction(STORE_META, 'readonly')
  const row = await requestOf(tx.objectStore(STORE_META).get(key) as IDBRequest<{ key: string; value: string } | undefined>)
  return row ? row.value : null
}

export async function setMeta(key: string, value: string): Promise<void> {
  const db = await openDb()
  const tx = db.transaction(STORE_META, 'readwrite')
  await requestOf(tx.objectStore(STORE_META).put({ key, value }))
}
