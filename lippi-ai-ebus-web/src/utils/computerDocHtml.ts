import DOMPurify from 'dompurify'
import { marked } from 'marked'
import type { ComputerDocFormat } from '@/types/business/computerView'

const PURIFY = {
  USE_PROFILES: { html: true },
  ADD_TAGS: ['button'],
  ADD_ATTR: [
    'data-adam-action',
    'data-adam-skill-id',
    'data-adam-prompt',
    'data-adam-media-object-id',
    'data-adam-media-role',
    'type',
  ],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
}

export function sanitizeComputerHtml(dirty: string): string {
  return DOMPurify.sanitize(dirty, PURIFY)
}

export function toPreviewHtml(format: ComputerDocFormat, content: string): string {
  const raw =
    format === 'markdown'
      ? String(marked.parse(content, { async: false })).trim()
      : content
  return sanitizeComputerHtml(raw)
}
