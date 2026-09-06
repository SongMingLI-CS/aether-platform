import type { ReactNode } from 'react'

export function EmptyState({ children }: { children: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-illustration" aria-hidden="true">
        <svg viewBox="0 0 120 120" fill="none" stroke="currentColor" strokeWidth={1.4}>
          <circle cx="60" cy="60" r="7" fill="currentColor" stroke="none" />
          {[0, 60, 120].map((deg) => (
            <g key={deg} transform={`rotate(${deg} 60 60)`}>
              <ellipse cx="60" cy="60" rx="47" ry="18" />
              <circle cx="107" cy="60" r="4" fill="currentColor" stroke="none" />
            </g>
          ))}
        </svg>
      </div>
      <div className="empty-text">{children}</div>
    </div>
  )
}
