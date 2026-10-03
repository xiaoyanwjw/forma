/** 质量差等成果反馈 */
export interface Feedback {
  id: string
  artifactId: string
  tag: string
  commentText?: string | null
  createdAt: string
}

export interface SubmitFeedbackRequest {
  artifactId: string
  tag: string
  commentText?: string
}

export const FEEDBACK_TAG_POOR_QUALITY = '质量差'
export const FEEDBACK_TAG_GOOD_QUALITY = '质量好'
