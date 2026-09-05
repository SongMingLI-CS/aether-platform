import type {
  AtomResponse,
  ConnectionResponse,
  ConnectionStatus,
  ContentType,
  PageResult,
  Result,
} from './types'

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

export function listAtoms(params: AtomQuery): Promise<PageResult<AtomResponse>> {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.keyword) qs.set('keyword', params.keyword)
  if (params.contentType) qs.set('contentType', params.contentType)
  return request<PageResult<AtomResponse>>(`${V1}/atoms?${qs.toString()}`)
}

export function createAtom(payload: { contentText: string; contentType: ContentType }): Promise<AtomResponse> {
  return request<AtomResponse>(`${V1}/atoms`, { method: 'POST', body: JSON.stringify(payload) })
}

export function deleteAtom(id: number): Promise<null> {
  return request<null>(`${V1}/atoms/${id}`, { method: 'DELETE' })
}

export function listConnections(params: ConnectionQuery): Promise<PageResult<ConnectionResponse>> {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  if (params.status) qs.set('status', params.status)
  return request<PageResult<ConnectionResponse>>(`${V1}/connections?${qs.toString()}`)
}

export function listAtomConnections(
  id: number,
  params: { page: number; size: number },
): Promise<PageResult<ConnectionResponse>> {
  const qs = new URLSearchParams({ page: String(params.page), size: String(params.size) })
  return request<PageResult<ConnectionResponse>>(`${V1}/atoms/${id}/connections?${qs.toString()}`)
}

export function updateConnectionStatus(
  id: number,
  status: ConnectionStatus,
): Promise<ConnectionResponse> {
  return request<ConnectionResponse>(`${V1}/connections/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}
