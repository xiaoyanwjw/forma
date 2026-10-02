import { computed, ref, watch, type Ref, type ComputedRef } from 'vue'
import { ApiError } from '@/api/client'
import { getFeedbackByArtifact, submitFeedback } from '@/api/business/feedback/feedback'
import { FEEDBACK_TAG_GOOD_QUALITY, FEEDBACK_TAG_POOR_QUALITY } from '@/types/business/feedback'

export function useWorkspaceFeedback(opts: {
  getArtifactId: () => string
  sessionBusy: Ref<boolean> | ComputedRef<boolean>
}) {
  const feedbackNote = ref('')
  const feedbackBusy = ref(false)
  const feedbackHint = ref('')
  const feedbackTag = ref<string | null>(null)
  const dislikeDrawerOpen = ref(false)
  let feedbackOpSeq = 0
  const localFeedbackSubmitSeq = new Map<string, number>()

  const canSubmitFeedback = computed(
    () => Boolean(opts.getArtifactId()) && !opts.sessionBusy.value && !feedbackBusy.value,
  )

  function markLocalFeedbackSubmit(artifactId: string) {
    const seq = ++feedbackOpSeq
    localFeedbackSubmitSeq.set(artifactId, seq)
  }

  function applyFeedbackRestore(seq: number, artifactId: string, tag: string | null) {
    if (opts.getArtifactId() !== artifactId) return
    const localSeq = localFeedbackSubmitSeq.get(artifactId) ?? 0
    if (localSeq > seq) return
    feedbackTag.value = tag
  }

  async function submitLikeFeedback() {
    const artifactRef = opts.getArtifactId()
    if (!artifactRef || !canSubmitFeedback.value) return
    feedbackBusy.value = true
    feedbackHint.value = ''
    try {
      await submitFeedback({
        artifactId: artifactRef,
        tag: FEEDBACK_TAG_GOOD_QUALITY,
      })
      markLocalFeedbackSubmit(artifactRef)
      feedbackTag.value = FEEDBACK_TAG_GOOD_QUALITY
      feedbackHint.value = '已记录「质量好」反馈，不影响积分。'
    } catch (e) {
      feedbackHint.value = e instanceof ApiError ? e.message : '反馈提交失败'
    } finally {
      feedbackBusy.value = false
    }
  }

  function openDislikeDrawer() {
    if (!opts.getArtifactId() || opts.sessionBusy.value) return
    dislikeDrawerOpen.value = true
  }

  function closeDislikeDrawer() {
    dislikeDrawerOpen.value = false
  }

  async function submitPoorQualityFeedback() {
    const artifactRef = opts.getArtifactId()
    if (!artifactRef || !canSubmitFeedback.value) return
    feedbackBusy.value = true
    feedbackHint.value = ''
    try {
      await submitFeedback({
        artifactId: artifactRef,
        tag: FEEDBACK_TAG_POOR_QUALITY,
        commentText: feedbackNote.value.trim() || undefined,
      })
      markLocalFeedbackSubmit(artifactRef)
      feedbackTag.value = FEEDBACK_TAG_POOR_QUALITY
      feedbackNote.value = ''
      dislikeDrawerOpen.value = false
      feedbackHint.value = '已记录「质量差」反馈，不影响积分。'
    } catch (e) {
      feedbackHint.value = e instanceof ApiError ? e.message : '反馈提交失败'
    } finally {
      feedbackBusy.value = false
    }
  }

  function resetFeedbackUi() {
    feedbackNote.value = ''
    feedbackHint.value = ''
    feedbackTag.value = null
    dislikeDrawerOpen.value = false
    feedbackBusy.value = false
    feedbackOpSeq += 1
    localFeedbackSubmitSeq.clear()
  }

  watch(
    () => opts.getArtifactId(),
    async (artifactId) => {
      if (!artifactId) {
        feedbackOpSeq += 1
        feedbackTag.value = null
        return
      }
      const seq = ++feedbackOpSeq
      try {
        const existing = await getFeedbackByArtifact(artifactId)
        applyFeedbackRestore(seq, artifactId, existing?.tag?.trim() || null)
      } catch {
        applyFeedbackRestore(seq, artifactId, null)
      }
    },
  )

  return {
    feedbackNote,
    feedbackBusy,
    feedbackHint,
    feedbackTag,
    dislikeDrawerOpen,
    canSubmitFeedback,
    submitLikeFeedback,
    openDislikeDrawer,
    closeDislikeDrawer,
    submitPoorQualityFeedback,
    resetFeedbackUi,
    FEEDBACK_TAG_GOOD_QUALITY,
    FEEDBACK_TAG_POOR_QUALITY,
  }
}
