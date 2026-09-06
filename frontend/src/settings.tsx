import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import type { ReactNode } from 'react'
import { translate } from './i18n'
import type { Language, TranslationKey } from './i18n'

export type ThemeMode = 'system' | 'light' | 'dark'
export type AccentId =
  | 'indigo'
  | 'blue'
  | 'sky'
  | 'cyan'
  | 'teal'
  | 'emerald'
  | 'lime'
  | 'amber'
  | 'orange'
  | 'rose'
  | 'pink'
  | 'fuchsia'
  | 'violet'
  | 'slate'
export type Density = 'comfortable' | 'compact'

export interface Settings {
  themeMode: ThemeMode
  accent: AccentId
  density: Density
  reduceMotion: boolean
  language: Language
}

type UpdateFn = <K extends keyof Settings>(key: K, value: Settings[K]) => void
type TFunc = (key: TranslationKey, vars?: Record<string, string | number>) => string

interface SettingsContextValue {
  settings: Settings
  resolvedTheme: 'light' | 'dark'
  toggleTheme: () => void
  update: UpdateFn
  t: TFunc
}

const STORAGE_KEY = 'aether.settings.v1'

const DEFAULT_SETTINGS: Settings = {
  themeMode: 'system',
  accent: 'indigo',
  density: 'comfortable',
  reduceMotion: false,
  language: 'zh',
}

const SettingsContext = createContext<SettingsContextValue | null>(null)

function loadSettings(): Settings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return DEFAULT_SETTINGS
    return { ...DEFAULT_SETTINGS, ...(JSON.parse(raw) as Partial<Settings>) }
  } catch {
    return DEFAULT_SETTINGS
  }
}

function systemPrefersDark(): boolean {
  return (
    typeof window !== 'undefined' &&
    !!window.matchMedia?.('(prefers-color-scheme: dark)').matches
  )
}

export function SettingsProvider({ children }: { children: ReactNode }) {
  const [settings, setSettings] = useState<Settings>(loadSettings)
  const [systemDark, setSystemDark] = useState<boolean>(systemPrefersDark)

  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  // In Electron, hydrate preferences from the persisted file (settings.json) on startup.
  useEffect(() => {
    if (!window.aether?.settings) return
    window.aether.settings
      .get()
      .then((stored) => {
        if (stored && typeof stored === 'object') {
          setSettings((prev) => ({ ...DEFAULT_SETTINGS, ...prev, ...(stored as Partial<Settings>) }))
        }
      })
      .catch(() => {})
  }, [])

  const resolvedTheme: 'light' | 'dark' =
    settings.themeMode === 'system'
      ? systemDark
        ? 'dark'
        : 'light'
      : settings.themeMode

  useEffect(() => {
    const root = document.documentElement
    root.setAttribute('data-theme', resolvedTheme)
    root.setAttribute('data-accent', settings.accent)
    root.setAttribute('data-density', settings.density)
    root.setAttribute('lang', settings.language === 'en' ? 'en' : 'zh-CN')
    const material = window.aether?.material
    if (material && material !== 'none') root.setAttribute('data-material', material)
    else root.removeAttribute('data-material')
    if (settings.reduceMotion) root.setAttribute('data-reduce-motion', 'true')
    else root.removeAttribute('data-reduce-motion')
  }, [resolvedTheme, settings.accent, settings.density, settings.reduceMotion, settings.language])

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(settings))
    } catch {
      /* ignore quota / privacy errors */
    }
    // Mirror to a local file (Electron) so preferences survive independent of browser storage.
    window.aether?.settings?.set(settings)?.catch(() => {})
  }, [settings])

  const update: UpdateFn = useCallback((key, value) => {
    setSettings((prev) => ({ ...prev, [key]: value }))
  }, [])

  const toggleTheme = useCallback(() => {
    setSettings((prev) => ({
      ...prev,
      themeMode: resolvedTheme === 'dark' ? 'light' : 'dark',
    }))
  }, [resolvedTheme])

  const t: TFunc = useCallback(
    (key, vars) => translate(settings.language, key, vars),
    [settings.language],
  )

  const value = useMemo(
    () => ({ settings, resolvedTheme, toggleTheme, update, t }),
    [settings, resolvedTheme, toggleTheme, update, t],
  )

  return <SettingsContext.Provider value={value}>{children}</SettingsContext.Provider>
}

export function useSettings(): SettingsContextValue {
  const ctx = useContext(SettingsContext)
  if (!ctx) throw new Error('useSettings must be used within SettingsProvider')
  return ctx
}
