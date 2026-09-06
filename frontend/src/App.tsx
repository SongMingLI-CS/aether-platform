import { useEffect, useState } from 'react'
import { flushSync } from 'react-dom'
import './App.css'
import AtomsView from './components/AtomsView'
import ConnectionsView from './components/ConnectionsView'
import GraphView from './components/GraphView'
import SettingsPanel from './components/SettingsPanel'
import ShortcutsPanel from './components/ShortcutsPanel'
import CommandPalette from './components/CommandPalette'
import SyncStatus from './components/SyncStatus'
import WindowControls from './components/WindowControls'
import { GearIcon, MoonIcon, SparklesIcon, SunIcon } from './components/Icons'
import { useSettings } from './settings'
import { isMacPlatform } from './platform'
import { startSync } from './sync'

type BackendState = 'loading' | 'online' | 'offline'
type Tab = 'atoms' | 'connections' | 'graph'

export default function App() {
  const [state, setState] = useState<BackendState>('loading')
  const [ping, setPing] = useState<string | null>(null)
  const [tab, setTab] = useState<Tab>('atoms')
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [shortcutsOpen, setShortcutsOpen] = useState(false)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const { resolvedTheme, toggleTheme, t } = useSettings()
  const isMac = isMacPlatform()
  const isDesktop = typeof window !== 'undefined' && !!window.aether
  const shortcutSettings = isMac ? '⌘,' : 'Ctrl+,'
  const shortcutTheme = isMac ? '⇧⌘D' : 'Ctrl+Shift+D'

  useEffect(() => {
    fetch('/api/ping')
      .then((res) => res.json())
      .then((data: { code: number; data?: { status?: string } }) => {
        setPing(data.data?.status ?? '')
        setState(data.code === 0 ? 'online' : 'offline')
      })
      .catch(() => setState('offline'))
  }, [])

  useEffect(() => {
    startSync()
  }, [])

  useEffect(() => {
    function onKeyDown(e: KeyboardEvent) {
      const el = e.target as HTMLElement | null
      if (
        el &&
        (el.tagName === 'INPUT' ||
          el.tagName === 'TEXTAREA' ||
          el.tagName === 'SELECT' ||
          el.isContentEditable)
      ) {
        return
      }
      const mod = isMac ? e.metaKey : e.ctrlKey
      if (!mod) return
      const key = e.key.toLowerCase()
      if (key === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      } else if (key === ',') {
        e.preventDefault()
        setSettingsOpen((v) => !v)
      } else if (key === 'd' && e.shiftKey) {
        e.preventDefault()
        toggleTheme()
      } else if (key === '/' || key === '?') {
        e.preventDefault()
        setShortcutsOpen((v) => !v)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [isMac, toggleTheme])

  useEffect(() => {
    if (!window.aether?.onMenu) return
    return window.aether.onMenu((action) => {
      if (action === 'open-settings') setSettingsOpen(true)
      else if (action === 'toggle-theme') toggleTheme()
      else if (action === 'open-shortcuts') setShortcutsOpen(true)
      else if (action === 'open-palette') setPaletteOpen(true)
    })
  }, [toggleTheme])

  const statusText =
    state === 'loading'
      ? t('app.status.checking')
      : state === 'online'
        ? t('app.status.online', { status: ping ?? '' })
        : t('app.status.offline')

  const themeToggleLabel = resolvedTheme === 'dark' ? t('app.theme.toLight') : t('app.theme.toDark')

  function switchTab(next: Tab) {
    const doc = document as unknown as {
      startViewTransition?: (cb: () => void) => unknown
    }
    if (typeof doc.startViewTransition === 'function') {
      doc.startViewTransition(() => flushSync(() => setTab(next)))
    } else {
      setTab(next)
    }
  }

  return (
    <>
      <header className="topbar">
        <div className={isMac ? 'topbar-inner mac' : 'topbar-inner'}>
          <div className="brand">
            <div className="logo">
              <SparklesIcon size={20} />
            </div>
            <div className="brand-text">
              <h1>Aether Platform</h1>
              <p className="subtitle">{t('app.subtitle')}</p>
            </div>
          </div>

          <div className="header-actions">
            <div className="status" aria-live="polite">
              <span
                className={
                  state === 'online'
                    ? 'dot ok'
                    : state === 'loading'
                      ? 'dot pending'
                      : 'dot bad'
                }
              />
              <span>{statusText}</span>
            </div>
            <SyncStatus />
            <button
              className="icon-btn"
              onClick={toggleTheme}
              title={`${themeToggleLabel} (${shortcutTheme})`}
              aria-label={themeToggleLabel}
            >
              {resolvedTheme === 'dark' ? <SunIcon /> : <MoonIcon />}
            </button>
            <button
              className="icon-btn"
              onClick={() => setSettingsOpen(true)}
              title={`${t('app.settings')} (${shortcutSettings})`}
              aria-label={t('app.settings')}
            >
              <GearIcon />
            </button>
            {isDesktop && !isMac && <WindowControls />}
          </div>
        </div>
      </header>

      <main className={tab === 'graph' ? 'app app--graph' : 'app'}>
        <nav className="tabs" role="tablist">
          <button
            role="tab"
            aria-selected={tab === 'atoms'}
            className={tab === 'atoms' ? 'tab active' : 'tab'}
            onClick={() => switchTab('atoms')}
          >
            {t('app.tab.atoms')}
          </button>
          <button
            role="tab"
            aria-selected={tab === 'connections'}
            className={tab === 'connections' ? 'tab active' : 'tab'}
            onClick={() => switchTab('connections')}
          >
            {t('app.tab.connections')}
          </button>
          <button
            role="tab"
            aria-selected={tab === 'graph'}
            className={tab === 'graph' ? 'tab active' : 'tab'}
            onClick={() => switchTab('graph')}
          >
            {t('app.tab.graph')}
          </button>
        </nav>

        {tab === 'atoms' ? <AtomsView /> : tab === 'connections' ? <ConnectionsView /> : <GraphView />}
      </main>

      {settingsOpen && <SettingsPanel onClose={() => setSettingsOpen(false)} />}
      {shortcutsOpen && <ShortcutsPanel onClose={() => setShortcutsOpen(false)} />}
      {paletteOpen && (
        <CommandPalette
          onClose={() => setPaletteOpen(false)}
          onOpenSettings={() => setSettingsOpen(true)}
          onOpenShortcuts={() => setShortcutsOpen(true)}
          onSwitchTab={switchTab}
          onNewAtom={() => {
            switchTab('atoms')
            window.setTimeout(() => window.dispatchEvent(new CustomEvent('aether:focus-composer')), 0)
          }}
        />
      )}
    </>
  )
}

