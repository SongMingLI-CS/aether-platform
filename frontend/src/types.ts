export type ContentType = 'TEXT' | 'MARKDOWN' | 'IMAGE_URL'

export type ConnectionStatus = 'PENDING' | 'CONFIRMED' | 'IGNORED'

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  totalElements: number
  totalPages: number
  page: number
  size: number
  content: T[]
}

export interface AtomResponse {
  id: number
  contentText: string
  contentType: ContentType
  createdAt: string
  updatedAt: string
  version: number
}

export interface ConnectionResponse {
  id: number
  sourceAtomId: number
  sourceText: string
  targetAtomId: number
  targetText: string
  similarity: number
  status: ConnectionStatus
  reason: string
  createdAt: string
}
