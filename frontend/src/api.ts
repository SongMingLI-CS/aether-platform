import type {
  AtomResponse,
  ConnectionResponse,
  ConnectionStatus,
  ContentType,
  PageResult,
  Result,
  SearchHitResponse,
} from './types'
import {
  createTempId,
  enqueueMutation,
  getAllAtoms,
  getAllConnections,
  putAtomLocal,
  putConnectionLocal,
  removeAtomLocal,
  updateConnectionLocal,
} from './localdb'

const API_BASE = (typeof window !== 'undefined' && window.aether?.backendUrl) || ''
const V1 = `${API_BASE}/api/v1`

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  const body = (await res.json()) as Result<T>
  if (!res.ok || body.code !== 0) {
    throw new Error(body.message || `HTTP ${res.status}`)
  }
  return body.data
}

function atomQs(params: AtomQuery): string {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.keyword) qs.set('keyword', params.keyword)
  if (params.contentType) qs.set('contentType', params.contentType)
  return qs.toString()
}

function connectionQs(params: ConnectionQuery): string {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status) qs.set('status', params.status)
  return qs.toString()
}

function isOfflineError(e: unknown): boolean {
  return (typeof navigator !== 'undefined' && !navigator.onLine) || e instanceof TypeError
}

// ---- Raw (always hit the network; throw on failure) ----

export function listAtomsRaw(params: AtomQuery): Promise<PageResult<AtomResponse>> {
  return request<PageResult<AtomResponse>>(`${V1}/atoms?${atomQs(params)}`)
}

export function listConnectionsRaw(params: ConnectionQuery): Promise<PageResult<ConnectionResponse>> {
  return request<PageResult<ConnectionResponse>>(`${V1}/connections?${connectionQs(params)}`)
}

export function deleteAtomRaw(id: number): Promise<null> {
  return request<null>(`${V1}/atoms/${id}`, { method: 'DELETE' })
}

export function updateConnectionStatusRaw(
  id: number,
  status: ConnectionStatus,
): Promise<ConnectionResponse> {
  return request<ConnectionResponse>(`${V1}/connections/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function createAtomRaw(payload: { contentText: string; contentType: ContentType }): Promise<AtomResponse> {
  return request<AtomResponse>(`${V1}/atoms`, { method: 'POST', body: JSON.stringify(payload) })
}

export function connectAtomsRaw(sourceAtomId: number, targetAtomId: number): Promise<ConnectionResponse> {
  return request<ConnectionResponse>(`${V1}/connections`, {
    method: 'POST',
    body: JSON.stringify({ sourceAtomId, targetAtomId }),
  })
}

export async function fetchAllAtoms(): Promise<AtomResponse[]> {
  const pageSize = 100
  const first = await listAtomsRaw({ page: 0, size: pageSize })
  const all = [...first.content]
  const pages = Math.ceil(first.totalElements / pageSize)
  if (pages > 1) {
    const rest = await Promise.all(
      Array.from({ length: pages - 1 }, (_, i) => listAtomsRaw({ page: i + 1, size: pageSize })),
    )
    for (const r of rest) all.push(...r.content)
  }
  return all
}

export async function fetchAllConnections(): Promise<ConnectionResponse[]> {
  const pageSize = 100
  const first = await listConnectionsRaw({ page: 0, size: pageSize })
  const all = [...first.content]
  const pages = Math.ceil(first.totalElements / pageSize)
  if (pages > 1) {
    const rest = await Promise.all(
      Array.from({ length: pages - 1 }, (_, i) => listConnectionsRaw({ page: i + 1, size: pageSize })),
    )
    for (const r of rest) all.push(...r.content)
  }
  return all
}

export interface AtomQuery {
  page: number
  size: number
  keyword?: string
  contentType?: ContentType | ''
}

export interface ConnectionQuery {
  page: number
  size: number
  status?: ConnectionStatus | ''
}

function paginateAtoms(cached: AtomResponse[], params: AtomQuery): PageResult<AtomResponse> {
  let list = cached
  if (params.contentType) list = list.filter((a) => a.contentType === params.contentType)
  if (params.keyword) {
    const keyword = params.keyword.toLowerCase()
    list = list.filter((a) => a.contentText.toLowerCase().includes(keyword))
  }
  const start = params.page * params.size
  return {
    totalElements: list.length,
    totalPages: Math.max(1, Math.ceil(list.length / params.size)),
    page: params.page,
    size: params.size,
    content: list.slice(start, start + params.size),
  }
}

function paginateConnections(cached: ConnectionResponse[], params: ConnectionQuery): PageResult<ConnectionResponse> {
  let list = cached
  if (params.status) list = list.filter((c) => c.status === params.status)
  const start = params.page * params.size
  return {
    totalElements: list.length,
    totalPages: Math.max(1, Math.ceil(list.length / params.size)),
    page: params.page,
    size: params.size,
    content: list.slice(start, start + params.size),
  }
}

export async function listAtoms(params: AtomQuery): Promise<PageResult<AtomResponse>> {
  try {
    return await listAtomsRaw(params)
  } catch (e) {
    if (!isOfflineError(e)) throw e
    const cached = await getAllAtoms()
    if (cached.length === 0) throw e
    return paginateAtoms(cached, params)
  }
}

export function searchAtoms(query: string, limit = 20): Promise<SearchHitResponse[]> {
  const qs = new URLSearchParams({ query, limit: String(limit) })
  return request<SearchHitResponse[]>(`${V1}/atoms/search?${qs.toString()}`)
}

export async function createAtom(payload: { contentText: string; contentType: ContentType }): Promise<AtomResponse> {
  try {
    const created = await createAtomRaw(payload)
    await putAtomLocal(created)
    return created
  } catch (e) {
    if (!isOfflineError(e)) throw e
    const tempId = createTempId()
    const now = new Date().toISOString()
    const tempAtom: AtomResponse = {
      id: tempId,
      contentText: payload.contentText,
      contentType: payload.contentType,
      createdAt: now,
      updatedAt: now,
      version: 0,
    }
    await putAtomLocal(tempAtom)
    await enqueueMutation({
      kind: 'CREATE_ATOM',
      payload: { tempId, contentText: payload.contentText, contentType: payload.contentType },
      createdAt: Date.now(),
    })
    return tempAtom
  }
}

export async function deleteAtom(id: number): Promise<null> {
  try {
    return await deleteAtomRaw(id)
  } catch (e) {
    if (!isOfflineError(e)) throw e
    await removeAtomLocal(id)
    await enqueueMutation({ kind: 'DELETE_ATOM', payload: { id }, createdAt: Date.now() })
    return null
  }
}

export async function listConnections(params: ConnectionQuery): Promise<PageResult<ConnectionResponse>> {
  try {
    return await listConnectionsRaw(params)
  } catch (e) {
    if (!isOfflineError(e)) throw e
    const cached = await getAllConnections()
    if (cached.length === 0) throw e
    return paginateConnections(cached, params)
  }
}

export function listAtomConnections(
  id: number,
  params: { page: number; size: number },
): Promise<PageResult<ConnectionResponse>> {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  return request<PageResult<ConnectionResponse>>(`${V1}/atoms/${id}/connections?${qs.toString()}`)
}

export async function updateConnectionStatus(
  id: number,
  status: ConnectionStatus,
): Promise<ConnectionResponse> {
  try {
    return await updateConnectionStatusRaw(id, status)
  } catch (e) {
    if (!isOfflineError(e)) throw e
    await updateConnectionLocal(id, { status })
    await enqueueMutation({ kind: 'UPDATE_CONNECTION_STATUS', payload: { id, status }, createdAt: Date.now() })
    const cached = await getAllConnections()
    const found = cached.find((c) => c.id === id)
    if (found) return { ...found, status }
    throw e
  }
}

// NOTE: the backend currently only auto-discovers connections (no manual
// "create connection" endpoint). This is the target API once it is added.
export async function connectAtoms(sourceAtomId: number, targetAtomId: number): Promise<ConnectionResponse> {
  try {
    const created = await connectAtomsRaw(sourceAtomId, targetAtomId)
    await putConnectionLocal(created)
    return created
  } catch (e) {
    if (!isOfflineError(e)) throw e
    const tempConnectionId = createTempId()
    const atoms = await getAllAtoms()
    const source = atoms.find((a) => a.id === sourceAtomId)
    const target = atoms.find((a) => a.id === targetAtomId)
    const tempConnection: ConnectionResponse = {
      id: tempConnectionId,
      sourceAtomId,
      targetAtomId,
      sourceText: source?.contentText ?? '',
      targetText: target?.contentText ?? '',
      similarity: 1,
      status: 'CONFIRMED',
      origin: 'MANUAL',
      reason: '',
      createdAt: new Date().toISOString(),
    }
    await putConnectionLocal(tempConnection)
    await enqueueMutation({
      kind: 'CONNECT_ATOMS',
      payload: { tempConnectionId, sourceAtomId, targetAtomId },
      createdAt: Date.now(),
    })
    return tempConnection
  }
}
