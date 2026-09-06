import { useEffect, useMemo, useRef, useState } from 'react'
import type { ReactNode, KeyboardEvent as ReactKeyboardEvent } from 'react'
import { searchAtoms } from '../api'
import type { AtomResponse } from '../types'
import { useSettings } from '../settings'
import { isMacPlatform } from '../platform'
import { GearIcon, LinkIcon, MoonIcon, PlusIcon, SparklesIcon, SunIcon, XIcon } from './Icons'

type Tab = 'atoms' | 'connections'

interface Command {
  id: string
  label: string
  hint?: string
  icon: ReactNode
  run: () => void
}

interface Item {
  type: 'command' | 'atom'
  command?: Command
  atom?: AtomResponse
}

interface Props {
  onClose: () => void
  onOpenSettings: () => void
  onOpenShortcuts: () => void
  onSwitchTab: (tab: Tab) => void
  onNewAtom: () => void
}

export default function CommandPalette({
  onClose,
  onOpenSettings,
  onOpenShortcuts,
  onSwitchTab,
  onNewAtom,
}: Props) {
  const { t, resolvedTheme, toggleTheme } = useSettings()
  const isMac = isMacPlatform()
  const [query, setQuery] = useState('')
  const [atoms, setAtoms] = useState<AtomResponse[]>([])
  const [searching, setSearching] = useState(false)
  const [active, setActive] = useState(0)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  // Debounced hybrid semantic search (unless in "command mode" prefixed with `>`).
  useEffect(() => {
    const q = query.trim()
    if (!q || q.startsWith('>')) {
      setAtoms([])
      setSearching(false)
      return
    }
    setSearching(true)
    const id = window.setTimeout(() => {
      searchAtoms(q, 8)
        .then((hits) => setAtoms(hits.map((h) => h.atom)))
        .catch(() => setAtoms([]))
        .finally(() => setSearching(false))
    }, 160)
    return () => window.clearTimeout(id)
  }, [query])

  const commands: Command[] = useMemo(
    () => [
      {
        id: 'theme',
        label: resolvedTheme === 'dark' ? t('app.theme.toLight') : t('app.theme.toDark'),
        hint: isMac ? '⇧⌘D' : 'Ctrl+Shift+D',
        icon: resolvedTheme === 'dark' ? <SunIcon size={16} /> : <MoonIcon size={16} />,
        run: () => {
          toggleTheme()
          onClose()
        },
      },
      {
        id: 'new-atom',
        label: t('atoms.composer.title'),
        icon: <PlusIcon size={16} />,
        run: () => {
          onNewAtom()
          onClose()
        },
      },
      {
        id: 'settings',
        label: t('app.settings'),
        hint: isMac ? '⌘,' : 'Ctrl+,',
        icon: <GearIcon size={16} />,
        run: () => {
          onOpenSettings()
          onClose()
        },
      },
      {
        id: 'shortcuts',
        label: t('settings.shortcuts.panel'),
        hint: isMac ? '⌘/' : 'Ctrl+/',
        icon: <SparklesIcon size={16} />,
        run: () => {
          onOpenShortcuts()
          onClose()
        },
      },
      {
        id: 'go-atoms',
        label: t('app.tab.atoms'),
        icon: <SparklesIcon size={16} />,
        run: () => {
          onSwitchTab('atoms')
          onClose()
        },
      },
      {
        id: 'go-connections',
        label: t('app.tab.connections'),
        icon: <LinkIcon size={16} />,
        run: () => {
          onSwitchTab('connections')
          onClose()
        },
      },
    ],
    [t, resolvedTheme, isMac, toggleTheme, onClose, onNewAtom, onOpenSettings, onOpenShortcuts, onSwitchTab],
  )

  const items: Item[] = useMemo(() => {
    const q = query.trim()
    const commandMode = q.startsWith('>')
    const needle = (commandMode ? q.slice(1) : q).trim().toLowerCase()
    const matched = commands.filter((c) => !needle || c.label.toLowerCase().includes(needle))
    const list: Item[] = matched.map((command) => ({ type: 'command', command }))
    if (!commandMode) {
      for (const atom of atoms) list.push({ type: 'atom', atom })
    }
    return list
  }, [query, commands, atoms])

  useEffect(() => {
    setActive(0)
  }, [items.length])

  function run(item: Item) {
    if (item.type === 'command' && item.command) {
      item.command.run()
    } else if (item.type === 'atom' && item.atom) {
      onSwitchTab('atoms')
      const keyword = item.atom.contentText.slice(0, 40)
      window.setTimeout(
        () => window.dispatchEvent(new CustomEvent('aether:search', { detail: { keyword } })),
        0,
      )
      onClose()
    }
  }

  function onKeyDown(e: ReactKeyboardEvent<HTMLInputElement>) {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((a) => Math.min(a + 1, Math.max(0, items.length - 1)))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const item = items[active]
      if (item) run(item)
    }
  }

  return (
    <div className="overlay palette-overlay" onClick={onClose}>
      <div
        className="palette"
        role="dialog"
        aria-modal="true"
        aria-label={t('app.commandPalette')}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="palette-input">
          <SparklesIcon size={18} className="palette-prefix" />
          <input
            ref={inputRef}
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder={t('palette.placeholder')}
          />
          <button className="palette-close" onClick={onClose} aria-label={t('settings.close')}>
            <XIcon size={16} />
          </button>
        </div>
        <div className="palette-list">
          {items.length === 0 ? (
            <div className="palette-empty">{searching ? t('common.loading') : t('palette.noResults')}</div>
          ) : (
            items.map((item, i) => {
              const label = item.type === 'command' ? item.command!.label : item.atom!.contentText
              const icon = item.type === 'command' ? item.command!.icon : <SparklesIcon size={16} />
              const hint = item.type === 'command' ? item.command!.hint : `#${item.atom!.id}`
              const key = item.type === 'command' ? item.command!.id : `atom-${item.atom!.id}`
              return (
                <button
                  key={key}
                  className={i === active ? 'palette-item active' : 'palette-item'}
                  onMouseEnter={() => setActive(i)}
                  onClick={() => run(item)}
                >
                  <span className="palette-icon">{icon}</span>
                  <span className="palette-label">{label}</span>
                  {hint && <span className="palette-hint">{hint}</span>}
                </button>
              )
            })
          )}
        </div>
        <div className="palette-footer">
          <span>
            <kbd>↑</kbd>
            <kbd>↓</kbd> {t('palette.navigate')}
          </span>
          <span>
            <kbd>↵</kbd> {t('palette.select')}
          </span>
          <span>
            <kbd>esc</kbd> {t('palette.close')}
          </span>
        </div>
      </div>
    </div>
  )
}
