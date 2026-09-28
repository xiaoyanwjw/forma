import { request } from '@/api/client'
import type { Feedback, SubmitFeedbackRequest } from '@/types/business/feedback'

export function submitFeedback(body: SubmitFeedbackRequest) {
  return request<Feedback>('/api/v1/feedbacks', {
    method: 'POST',
    body: JSON.stringify({
      artifactId: body.artifactId,
      tag: body.tag,
      commentText: body.commentText?.trim() || undefined,
    }),
  })
}

export function getFeedback(id: string) {
  return request<Feedback>(`/api/v1/feedbacks/${encodeURIComponent(id)}`)
}
