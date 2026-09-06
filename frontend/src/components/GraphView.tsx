import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import ForceGraph2D from 'react-force-graph-2d'
import type { ForceGraphMethods, LinkObject, NodeObject } from 'react-force-graph-2d'
import { connectAtoms, listAtoms, listAtomConnections, listConnections, updateConnectionStatus } from '../api'
import type { AtomResponse, ConnectionResponse, ConnectionStatus } from '../types'
import type { TranslationKey } from '../i18n'
import { AlertIcon, CheckIcon, CrosshairIcon, LinkIcon, NetworkIcon, RefreshIcon, SearchIcon, XIcon } from './Icons'
import { useSettings } from '../settings'
import { EmptyState } from './EmptyState'
import { SkeletonList } from './Skeleton'

const PAGE_SIZE = 100

interface GraphNode {
  id: string
  label: string
  atom: AtomResponse
  degree: number
}

interface GraphLink {
  source: string
  target: string
  connection: ConnectionResponse
}

type NodeDatum = NodeObject<GraphNode>
type LinkDatum = LinkObject<GraphNode, GraphLink>

type Drawer =
  | { kind: 'atom'; atom: AtomResponse }
  | { kind: 'connection'; connection: ConnectionResponse }
  | null

interface Palette {
  accent: string
  text: string
  textFaint: string
}

const STATUS_LABEL_KEY: Record<ConnectionResponse['status'], TranslationKey> = {
  PENDING: 'connections.status.pending',
  CONFIRMED: 'connections.status.confirmed',
  IGNORED: 'connections.status.ignored',
}

const STATUS_CLASS: Record<ConnectionResponse['status'], string> = {
  PENDING: 'pending',
  CONFIRMED: 'confirmed',
  IGNORED: 'ignored',
}

function readCssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = window.getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function readPalette(): Palette {
  return {
    accent: readCssVar('--accent', '#6366f1'),
    text: readCssVar('--text', '#1b2430'),
    textFaint: readCssVar('--text-faint', '#8a94a3'),
  }
}

function withAlpha(hex: string, alpha: number): string {
  const m = hex.replace('#', '')
  if (m.length !== 6) return hex
  const r = parseInt(m.slice(0, 2), 16)
  const g = parseInt(m.slice(2, 4), 16)
  const b = parseInt(m.slice(4, 6), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}

function truncate(text: string, max: number): string {
  const collapsed = text.replace(/\s+/g, ' ').trim()
  return collapsed.length > max ? `${collapsed.slice(0, max)}…` : collapsed
}

async function fetchAllAtoms(): Promise<AtomResponse[]> {
  const first = await listAtoms({ page: 0, size: PAGE_SIZE })
  const total = first.totalElements
  const all = [...first.content]
  const pages = Math.ceil(total / PAGE_SIZE)
  if (pages > 1) {
    const rest = await Promise.all(
      Array.from({ length: pages - 1 }, (_, i) => listAtoms({ page: i + 1, size: PAGE_SIZE })),
    )
    for (const r of rest) all.push(...r.content)
  }
  return all
}

async function fetchAllConnections(): Promise<ConnectionResponse[]> {
  const first = await listConnections({ page: 0, size: PAGE_SIZE })
  const total = first.totalElements
  const all = [...first.content]
  const pages = Math.ceil(total / PAGE_SIZE)
  if (pages > 1) {
    const rest = await Promise.all(
      Array.from({ length: pages - 1 }, (_, i) => listConnections({ page: i + 1, size: PAGE_SIZE })),
    )
    for (const r of rest) all.push(...r.content)
  }
  return all
}

export default function GraphView() {
  const { t, resolvedTheme, settings } = useSettings()

  const [atoms, setAtoms] = useState<AtomResponse[]>([])
  const [connections, setConnections] = useState<ConnectionResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [statusFilter, setStatusFilter] = useState<ConnectionStatus | ''>('')
  const [drawer, setDrawer] = useState<Drawer>(null)
  const [drawerConnections, setDrawerConnections] = useState<ConnectionResponse[]>([])
  const [drawerLoading, setDrawerLoading] = useState(false)
  const [searchQuery, setSearchQuery] = useState('')
  const [searchOpen, setSearchOpen] = useState(false)
  const [connectMode, setConnectMode] = useState(false)
  const [snack, setSnack] = useState<string | null>(null)

  const containerRef = useRef<HTMLDivElement>(null)
  const fgRef = useRef<ForceGraphMethods<NodeDatum, LinkDatum> | undefined>(undefined)
  const lastClickRef = useRef<{ id: string; ts: number } | null>(null)
  const autoFitRef = useRef(false)
  const selectedIdRef = useRef<string | null>(null)
  const hoveredIdRef = useRef<string | null>(null)
  const hoveredNeighborsRef = useRef<Set<string>>(new Set())
  const hoveredLinkIdRef = useRef<number | null>(null)
  const pendingLocateIdRef = useRef<string | null>(null)
  const zoomRef = useRef(1)
  const dragSourceRef = useRef<NodeDatum | null>(null)
  const dropTargetRef = useRef<NodeDatum | null>(null)
  const connectLineRef = useRef<SVGLineElement>(null)
  const snackTimer = useRef<number | null>(null)

  const [size, setSize] = useState({ width: 0, height: 0 })
  const [palette, setPalette] = useState<Palette>(readPalette)

  // Re-read theme/accent colors after the DOM data-* attributes are applied.
  useEffect(() => {
    setPalette(readPalette())
  }, [resolvedTheme, settings.accent])

  // Measure the canvas container (fires once on mount, then on resize).
  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    const observer = new ResizeObserver((entries) => {
      const rect = entries[0].contentRect
      setSize({ width: rect.width, height: rect.height })
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [])

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    autoFitRef.current = false
    try {
      const [allAtoms, allLinks] = await Promise.all([fetchAllAtoms(), fetchAllConnections()])
      setAtoms(allAtoms)
      setConnections(allLinks)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load, reloadKey])

  const graphData = useMemo<{
    nodes: GraphNode[]
    links: GraphLink[]
    adjacency: Map<string, Set<string>>
  }>(() => {
    const nodeById = new Map<string, GraphNode>()
    const nodes: GraphNode[] = atoms.map((atom) => {
      const node: GraphNode = {
        id: String(atom.id),
        label: truncate(atom.contentText, 24) || `#${atom.id}`,
        atom,
        degree: 0,
      }
      nodeById.set(node.id, node)
      return node
    })
    const visibleConnections = statusFilter ? connections.filter((c) => c.status === statusFilter) : connections
    const links: GraphLink[] = []
    const adjacency = new Map<string, Set<string>>()
    const addEdge = (a: string, b: string) => {
      if (!adjacency.has(a)) adjacency.set(a, new Set())
      if (!adjacency.has(b)) adjacency.set(b, new Set())
      adjacency.get(a)!.add(b)
      adjacency.get(b)!.add(a)
    }
    for (const c of visibleConnections) {
      const source = nodeById.get(String(c.sourceAtomId))
      const target = nodeById.get(String(c.targetAtomId))
      if (!source || !target) continue
      source.degree += 1
      target.degree += 1
      links.push({ source: source.id, target: target.id, connection: c })
      addEdge(source.id, target.id)
    }
    // When a status filter is active, drop atoms no longer on any visible edge.
    const visibleNodes = statusFilter ? nodes.filter((n) => n.degree > 0) : nodes
    return { nodes: visibleNodes, links, adjacency }
  }, [atoms, connections, statusFilter])

  const searchResults = useMemo(() => {
    const q = searchQuery.trim().toLowerCase()
    if (!q) return []
    return atoms.filter((a) => a.contentText.toLowerCase().includes(q)).slice(0, 10)
  }, [searchQuery, atoms])

  function openDrawer(atom: AtomResponse) {
    setDrawer({ kind: 'atom', atom })
    setDrawerConnections([])
    setDrawerLoading(true)
    listAtomConnections(atom.id, { page: 0, size: PAGE_SIZE })
      .then((data) => setDrawerConnections(data.content))
      .catch(() => setDrawerConnections([]))
      .finally(() => setDrawerLoading(false))
  }

  function openConnectionDrawer(connection: ConnectionResponse) {
    setDrawer({ kind: 'connection', connection })
  }

  function centerAndZoom(x: number, y: number) {
    fgRef.current?.centerAt(x, y, 500)
    fgRef.current?.zoom(2.5, 500)
  }

  function locateAtom(atom: AtomResponse) {
    const id = String(atom.id)
    selectedIdRef.current = id
    setSearchQuery('')
    setSearchOpen(false)
    setDrawer(null)
    const node = graphData.nodes.find((n) => n.id === id) as NodeDatum | undefined
    if (node && node.x != null && node.y != null) {
      centerAndZoom(node.x, node.y)
    } else {
      // Node is filtered out — clear the filter so it appears, then center after re-layout.
      pendingLocateIdRef.current = id
      setStatusFilter('')
    }
  }

  function locateAtomById(atomId: number) {
    const atom = atoms.find((a) => a.id === atomId)
    if (atom) locateAtom(atom)
  }

  function openAtomById(atomId: number) {
    const atom = atoms.find((a) => a.id === atomId)
    if (atom) openDrawer(atom)
  }

  function showSnack(message: string) {
    if (snackTimer.current) window.clearTimeout(snackTimer.current)
    setSnack(message)
    snackTimer.current = window.setTimeout(() => setSnack(null), 4000)
  }

  function handleConnectionStatus(id: number, status: ConnectionStatus) {
    updateConnectionStatus(id, status)
      .then((updated) => {
        setConnections((prev) => prev.map((c) => (c.id === id ? updated : c)))
        setDrawer({ kind: 'connection', connection: updated })
      })
      .catch((err: Error) => showSnack(err.message))
  }

  useEffect(() => {
    if (!drawer) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setDrawer(null)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [drawer])

  useEffect(() => {
    return () => {
      if (snackTimer.current) window.clearTimeout(snackTimer.current)
    }
  }, [])

  const nodeRadius = (node: NodeDatum) => Math.max(4, Math.min(node.degree, 12) * 1.4 + 4)

  function paintNode(node: NodeDatum, ctx: CanvasRenderingContext2D, globalScale: number) {
    const { x, y } = node
    if (x == null || y == null) return
    const r = nodeRadius(node) / globalScale
    const hoveredId = hoveredIdRef.current
    const isDimmed = hoveredId != null && node.id !== hoveredId && !hoveredNeighborsRef.current.has(node.id)

    ctx.globalAlpha = isDimmed ? 0.16 : 1

    ctx.beginPath()
    ctx.arc(x, y, r, 0, 2 * Math.PI)
    ctx.fillStyle = palette.accent
    ctx.fill()
    ctx.lineWidth = 1 / globalScale
    ctx.strokeStyle = 'rgba(0, 0, 0, 0.18)'
    ctx.stroke()

    // Hide labels when zoomed out to reduce clutter (always show the hovered node's).
    if (zoomRef.current >= 0.55 || node.id === hoveredId) {
      const fontSize = 11 / globalScale
      ctx.font = `500 ${fontSize}px system-ui, -apple-system, 'PingFang SC', 'Microsoft YaHei', sans-serif`
      ctx.textAlign = 'center'
      ctx.textBaseline = 'top'
      ctx.fillStyle = palette.text
      ctx.fillText(node.label, x, y + r + 3 / globalScale)
    }

    ctx.globalAlpha = 1

    if (node.id === selectedIdRef.current) {
      ctx.beginPath()
      ctx.arc(x, y, r + 3 / globalScale, 0, 2 * Math.PI)
      ctx.lineWidth = 2 / globalScale
      ctx.strokeStyle = palette.accent
      ctx.stroke()
    }
  }

  function paintNodePointer(node: NodeDatum, color: string, ctx: CanvasRenderingContext2D, globalScale: number) {
    const { x, y } = node
    if (x == null || y == null) return
    const r = nodeRadius(node) / globalScale
    ctx.fillStyle = color
    ctx.beginPath()
    ctx.arc(x, y, r, 0, 2 * Math.PI)
    ctx.fill()
  }

  function isLinkToHovered(link: LinkDatum): boolean {
    const hid = hoveredIdRef.current
    if (hid == null) return false
    return String(link.connection.sourceAtomId) === hid || String(link.connection.targetAtomId) === hid
  }

  const linkColor = (link: LinkDatum) => {
    const base = link.connection.origin === 'MANUAL' ? palette.accent : palette.textFaint
    if (hoveredLinkIdRef.current === link.connection.id) return base
    const hid = hoveredIdRef.current
    if (hid == null) return base
    return isLinkToHovered(link) ? base : withAlpha(base, 0.12)
  }

  const linkWidth = (link: LinkDatum) => {
    const base = link.connection.origin === 'MANUAL' ? 2 : 1.2
    if (hoveredLinkIdRef.current === link.connection.id) return base + 1.2
    const hid = hoveredIdRef.current
    if (hid == null) return base
    return isLinkToHovered(link) ? base + 0.8 : base * 0.4
  }

  const linkLineDash = (link: LinkDatum) => (link.connection.origin === 'MANUAL' ? null : [4, 3])

  function onNodeClick(node: NodeDatum) {
    const id = node.id
    selectedIdRef.current = id
    const now = Date.now()
    const prev = lastClickRef.current
    if (prev && prev.id === id && now - prev.ts < 350) {
      lastClickRef.current = null
      openDrawer(node.atom)
    } else {
      lastClickRef.current = { id, ts: now }
    }
  }

  function onNodeHover(node: NodeDatum | null) {
    hoveredIdRef.current = node ? node.id : null
    hoveredNeighborsRef.current = node ? (graphData.adjacency.get(node.id) ?? new Set()) : new Set()
  }

  function onZoom(transform: { k: number }) {
    zoomRef.current = transform.k
  }

  function onLinkHover(link: LinkDatum | null) {
    hoveredLinkIdRef.current = link ? link.connection.id : null
  }

  function onLinkClick(link: LinkDatum) {
    openConnectionDrawer(link.connection)
  }

  function nearestNodeTo(source: NodeDatum): NodeDatum | null {
    if (source.x == null || source.y == null) return null
    const threshold = 48 / zoomRef.current
    let best: NodeDatum | null = null
    let bestDist = threshold
    for (const n of graphData.nodes) {
      if (n.id === source.id) continue
      const d = n as NodeDatum
      if (d.x == null || d.y == null) continue
      const dx = d.x - source.x
      const dy = d.y - source.y
      const dist = Math.sqrt(dx * dx + dy * dy)
      if (dist < bestDist) {
        bestDist = dist
        best = d
      }
    }
    return best
  }

  function updateConnectLine(source: NodeDatum, target: NodeDatum | null) {
    const line = connectLineRef.current
    if (!line || !target) {
      if (line) line.style.display = 'none'
      return
    }
    const fg = fgRef.current
    if (!fg || source.x == null || source.y == null || target.x == null || target.y == null) {
      line.style.display = 'none'
      return
    }
    const s = fg.graph2ScreenCoords(source.x, source.y)
    const t = fg.graph2ScreenCoords(target.x, target.y)
    line.setAttribute('x1', String(s.x))
    line.setAttribute('y1', String(s.y))
    line.setAttribute('x2', String(t.x))
    line.setAttribute('y2', String(t.y))
    line.style.display = 'block'
  }

  function onNodeDrag(node: NodeDatum) {
    if (!connectMode) return
    dragSourceRef.current = node
    const target = nearestNodeTo(node)
    dropTargetRef.current = target
    updateConnectLine(node, target)
  }

  function onNodeDragEnd(node: NodeDatum) {
    if (!connectMode) return
    const source = dragSourceRef.current ?? node
    const target = dropTargetRef.current
    dragSourceRef.current = null
    dropTargetRef.current = null
    updateConnectLine(node, null)
    if (!target) return
    const sourceId = Number(source.id)
    const targetId = Number(target.id)
    if (!sourceId || !targetId || sourceId === targetId) return
    connectAtoms(sourceId, targetId)
      .then(() => {
        showSnack(t('connections.created'))
        setReloadKey((k) => k + 1)
      })
      .catch((err: Error) => showSnack(err.message || t('connections.createFailed')))
  }

  function onEngineStop() {
    if (pendingLocateIdRef.current) {
      const id = pendingLocateIdRef.current
      pendingLocateIdRef.current = null
      const node = graphData.nodes.find((n) => n.id === id) as NodeDatum | undefined
      if (node && node.x != null && node.y != null) {
        centerAndZoom(node.x, node.y)
      }
    } else if (!autoFitRef.current) {
      autoFitRef.current = true
      fgRef.current?.zoomToFit(400, 60)
    }
  }

  function handleFit() {
    fgRef.current?.zoomToFit(400, 60)
  }

  function handleRelayout() {
    fgRef.current?.d3ReheatSimulation()
  }

  return (
    <div className="panel graph-view">
      <div className="card toolbar graph-toolbar">
        <select
          className="graph-filter"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as ConnectionStatus | '')}
          aria-label={t('connections.filter.all')}
        >
          <option value="">{t('connections.filter.all')}</option>
          <option value="PENDING">{t('connections.status.pending')}</option>
          <option value="CONFIRMED">{t('connections.status.confirmed')}</option>
          <option value="IGNORED">{t('connections.status.ignored')}</option>
        </select>
        <div className="graph-search">
          <SearchIcon size={15} className="graph-search-icon" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onFocus={() => setSearchOpen(true)}
            onBlur={() => setSearchOpen(false)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && searchResults[0]) locateAtom(searchResults[0])
            }}
            placeholder={t('graph.search.placeholder')}
            aria-label={t('graph.search.placeholder')}
          />
          {searchOpen && searchQuery.trim() && (
            <div className="graph-search-results">
              {searchResults.length === 0 ? (
                <div className="graph-search-empty">{t('graph.search.empty')}</div>
              ) : (
                searchResults.map((a) => (
                  <button
                    key={a.id}
                    className="graph-search-item"
                    onMouseDown={(e) => {
                      e.preventDefault()
                      locateAtom(a)
                    }}
                  >
                    <span className="graph-search-label">{truncate(a.contentText, 30)}</span>
                    <span className="mono">#{a.id}</span>
                  </button>
                ))
              )}
            </div>
          )}
        </div>
        <span className="count">{t('graph.stats', { nodes: graphData.nodes.length, links: graphData.links.length })}</span>
        <div className="graph-legend">
          <span className="legend-item">
            <span className="legend-line solid" /> {t('graph.legend.manual')}
          </span>
          <span className="legend-item">
            <span className="legend-line dashed" /> {t('graph.legend.auto')}
          </span>
        </div>
        <div className="graph-actions">
          <button
            className={connectMode ? 'btn ghost active' : 'btn ghost'}
            onClick={() => setConnectMode((v) => !v)}
            aria-pressed={connectMode}
          >
            <LinkIcon size={16} />
            {t('graph.connectMode')}
          </button>
          <button className="btn ghost" onClick={handleFit}>
            <NetworkIcon size={16} />
            {t('graph.fit')}
          </button>
          <button className="btn ghost" onClick={handleRelayout}>
            <RefreshIcon size={16} />
            {t('graph.relayout')}
          </button>
          <button
            className="btn ghost square"
            onClick={() => setReloadKey((k) => k + 1)}
            title={t('common.refresh')}
            aria-label={t('common.refresh')}
          >
            <RefreshIcon size={16} />
          </button>
        </div>
      </div>

      {error && (
        <div className="error" role="alert">
          <AlertIcon size={16} />
          <span>{error}</span>
        </div>
      )}

      <div className="card graph-canvas-card">
        <div ref={containerRef} className="graph-canvas">
          {loading ? (
            <div className="graph-loading">
              <SkeletonList rows={5} />
            </div>
          ) : graphData.nodes.length === 0 ? (
            <EmptyState>
              <span>{statusFilter ? t('graph.noFiltered') : t('graph.empty')}</span>
            </EmptyState>
          ) : (
            <ForceGraph2D<GraphNode, GraphLink>
              ref={fgRef}
              graphData={graphData}
              width={size.width}
              height={size.height}
              backgroundColor="rgba(0, 0, 0, 0)"
              nodeId="id"
              nodeLabel={(n) => n.atom.contentText}
              nodeVal={(n) => Math.min(n.degree, 12) + 1}
              nodeCanvasObject={paintNode}
              nodePointerAreaPaint={paintNodePointer}
              linkColor={linkColor}
              linkWidth={linkWidth}
              linkLineDash={linkLineDash}
              linkDirectionalArrowLength={(l) => (l.connection.origin === 'MANUAL' ? 4 : 3)}
              linkDirectionalArrowRelPos={1}
              linkLabel={(l) =>
                `${l.connection.sourceText} → ${l.connection.targetText}（${(l.connection.similarity * 100).toFixed(1)}%）`
              }
              onNodeClick={onNodeClick}
              onNodeHover={onNodeHover}
              onLinkHover={onLinkHover}
              onLinkClick={onLinkClick}
              onNodeDrag={onNodeDrag}
              onNodeDragEnd={onNodeDragEnd}
              onBackgroundClick={() => {
                selectedIdRef.current = null
              }}
              onZoom={onZoom}
              onEngineStop={onEngineStop}
              cooldownTicks={150}
              d3VelocityDecay={0.25}
              autoPauseRedraw={false}
              minZoom={0.3}
              maxZoom={8}
            />
          )}
          <svg className="graph-connect-line" aria-hidden="true">
            <line ref={connectLineRef} x1="0" y1="0" x2="0" y2="0" />
          </svg>
          {!loading && graphData.nodes.length > 0 && graphData.links.length === 0 && (
            <div className="graph-no-links">{t('graph.noLinks')}</div>
          )}
        </div>
      </div>

      <div className="graph-hint">{connectMode ? t('graph.connectMode.hint') : t('graph.hint')}</div>

      {drawer && (
        <>
          <div className="graph-drawer-backdrop" onClick={() => setDrawer(null)} />
          {drawer.kind === 'atom' && (
          <aside
            className="graph-drawer"
            role="dialog"
            aria-label={t('graph.drawer.title', { id: drawer.atom.id })}
          >
            <header className="graph-drawer-head">
              <div className="graph-drawer-id mono">#{drawer.atom.id}</div>
              <button
                className="btn ghost square"
                onClick={() => setDrawer(null)}
                aria-label={t('settings.close')}
              >
                <XIcon size={18} />
              </button>
            </header>
            <div className="graph-drawer-body">
              <div className="graph-drawer-meta">
                <span className="badge type">{drawer.atom.contentType}</span>
                <span className="meta-item mono">v{drawer.atom.version}</span>
              </div>
              <div className="graph-drawer-content">{drawer.atom.contentText}</div>
              <div className="graph-drawer-times">
                <span className="meta-item">{new Date(drawer.atom.createdAt).toLocaleString()}</span>
                {drawer.atom.updatedAt !== drawer.atom.createdAt && (
                  <span className="meta-item">→ {new Date(drawer.atom.updatedAt).toLocaleString()}</span>
                )}
              </div>

              <section className="graph-drawer-section">
                <h3>{t('atoms.connections')}</h3>
                {drawerLoading ? (
                  <div className="empty">{t('atoms.loadingConnections')}</div>
                ) : drawerConnections.length === 0 ? (
                  <div className="empty">{t('atoms.noConnections')}</div>
                ) : (
                  drawerConnections.map((c) => (
                    <div key={c.id} className="conn">
                      <span className="badge sim">{(c.similarity * 100).toFixed(1)}%</span>
                      <span className="conn-path">
                        <strong>{c.sourceAtomId === drawer.atom.id ? c.targetText : c.sourceText}</strong>
                        <span className="arrow">↔</span>
                        <strong>{c.sourceAtomId === drawer.atom.id ? c.sourceText : c.targetText}</strong>
                      </span>
                      <span className={`badge ${STATUS_CLASS[c.status]}`}>{t(STATUS_LABEL_KEY[c.status])}</span>
                      {c.origin === 'MANUAL' && (
                        <span className="badge manual">{t('connections.origin.manual')}</span>
                      )}
                    </div>
                  ))
                )}
              </section>
            </div>
          </aside>
          )}
          {drawer.kind === 'connection' && (
            <aside
              className="graph-drawer"
              role="dialog"
              aria-label={t('graph.drawer.connection.title', { id: drawer.connection.id })}
            >
              <header className="graph-drawer-head">
                <div className="graph-drawer-id mono">
                  <LinkIcon size={14} /> #{drawer.connection.id}
                </div>
                <button
                  className="btn ghost square"
                  onClick={() => setDrawer(null)}
                  aria-label={t('settings.close')}
                >
                  <XIcon size={18} />
                </button>
              </header>
              <div className="graph-drawer-body">
                <div className="graph-drawer-meta">
                  <span className="badge sim">{(drawer.connection.similarity * 100).toFixed(1)}%</span>
                  <span className={`badge ${STATUS_CLASS[drawer.connection.status]}`}>
                    {t(STATUS_LABEL_KEY[drawer.connection.status])}
                  </span>
                  {drawer.connection.origin === 'MANUAL' && (
                    <span className="badge manual">{t('connections.origin.manual')}</span>
                  )}
                </div>

                <div className="graph-conn-endpoints">
                  <div className="graph-conn-endpoint">
                    <button
                      className="graph-conn-endpoint-main"
                      onClick={() => openAtomById(drawer.connection.sourceAtomId)}
                    >
                      <span className="graph-conn-role">{t('graph.drawer.source')}</span>
                      <span className="graph-conn-text">{drawer.connection.sourceText}</span>
                    </button>
                    <button
                      className="graph-conn-locate"
                      onClick={() => locateAtomById(drawer.connection.sourceAtomId)}
                      title={t('graph.locate')}
                      aria-label={t('graph.locate')}
                    >
                      <CrosshairIcon size={16} />
                    </button>
                  </div>
                  <span className="arrow">↓</span>
                  <div className="graph-conn-endpoint">
                    <button
                      className="graph-conn-endpoint-main"
                      onClick={() => openAtomById(drawer.connection.targetAtomId)}
                    >
                      <span className="graph-conn-role">{t('graph.drawer.target')}</span>
                      <span className="graph-conn-text">{drawer.connection.targetText}</span>
                    </button>
                    <button
                      className="graph-conn-locate"
                      onClick={() => locateAtomById(drawer.connection.targetAtomId)}
                      title={t('graph.locate')}
                      aria-label={t('graph.locate')}
                    >
                      <CrosshairIcon size={16} />
                    </button>
                  </div>
                </div>

                {drawer.connection.reason && (
                  <div className="graph-drawer-times">
                    <span className="meta-item">
                      {t('graph.drawer.reason')}：{drawer.connection.reason}
                    </span>
                  </div>
                )}

                <div className="graph-drawer-times">
                  <span className="meta-item">{new Date(drawer.connection.createdAt).toLocaleString()}</span>
                </div>

                {drawer.connection.status === 'PENDING' && (
                  <div className="conn-actions">
                    <button
                      className="btn primary"
                      onClick={() => handleConnectionStatus(drawer.connection.id, 'CONFIRMED')}
                    >
                      <CheckIcon size={16} />
                      {t('connections.confirm')}
                    </button>
                    <button
                      className="btn"
                      onClick={() => handleConnectionStatus(drawer.connection.id, 'IGNORED')}
                    >
                      <XIcon size={16} />
                      {t('connections.ignore')}
                    </button>
                  </div>
                )}
              </div>
            </aside>
          )}
        </>
      )}

      {snack && (
        <div className="snackbar" role="status">
          <span>{snack}</span>
          <button className="snackbar-close" onClick={() => setSnack(null)} aria-label={t('settings.close')}>
            <XIcon size={14} />
          </button>
        </div>
      )}
    </div>
  )
}



