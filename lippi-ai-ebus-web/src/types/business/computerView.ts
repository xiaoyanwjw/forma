export type ComputerDocFormat = 'markdown' | 'html'

export interface ComputerDocView {
  version: 2
  title: string
  format: ComputerDocFormat
  content: string
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

export function parseComputerDocView(raw: unknown): ComputerDocView | null {
  if (!isRecord(raw)) return null
  if (raw.version !== 2) return null
  if (typeof raw.title !== 'string' || !raw.title.trim()) return null
  if (raw.format !== 'markdown' && raw.format !== 'html') return null
  if (typeof raw.content !== 'string' || !raw.content.trim()) return null
  return {
    version: 2,
    title: raw.title.trim(),
    format: raw.format,
    content: raw.content,
  }
}
