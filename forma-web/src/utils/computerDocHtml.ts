import DOMPurify from 'dompurify'
import { marked } from 'marked'
import type { ComputerDocFormat } from '@/types/business/computerView'

const PURIFY = {
  USE_PROFILES: { html: true },
  ADD_TAGS: ['button'],
  ADD_ATTR: [
    'data-forma-action',
    'data-forma-skill-id',
    'data-forma-prompt',
    'data-forma-media-object-id',
    'data-forma-media-role',
    // legacy Adam protocol (migrated below; kept so purify does not strip if missed)
    'data-adam-action',
    'data-adam-skill-id',
    'data-adam-prompt',
    'data-adam-media-object-id',
    'data-adam-media-role',
    'type',
    'open',
    'aria-label',
  ],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
}

/** Map historical Adam view markup → Forma protocol before sanitize/render. */
export function migrateAdamProtocolHtml(html: string): string {
  return html
    .replace(/data-adam-/g, 'data-forma-')
    .replace(/(^|[\s"'=])adam-/g, '$1forma-')
}

export function sanitizeComputerHtml(dirty: string): string {
  return DOMPurify.sanitize(migrateAdamProtocolHtml(dirty), PURIFY)
}

export function toPreviewHtml(format: ComputerDocFormat, content: string): string {
  const raw =
    format === 'markdown'
      ? String(marked.parse(content, { async: false })).trim()
      : content
  return sanitizeComputerHtml(raw)
}
