/** Xiaohongshu workspace Computer pane kinds. */
export type XhsComputerKind = 'topiclist' | 'note' | 'break' | null

export type XhsComputerKindLive = Exclude<XhsComputerKind, null>

export const XHS_SKILL_BY_KIND: Record<XhsComputerKindLive, string> = {
  topiclist: 'xhs-topiclist',
  note: 'xhs-note',
  break: 'xhs-break',
}

export function xhsKindFromSkillId(skillId?: string | null): XhsComputerKind {
  const id = (skillId || '').trim()
  if (id === XHS_SKILL_BY_KIND.topiclist) return 'topiclist'
  if (id === XHS_SKILL_BY_KIND.note) return 'note'
  if (id === XHS_SKILL_BY_KIND.break) return 'break'
  return null
}

export function xhsThinkingLabel(kind: XhsComputerKindLive): string {
  if (kind === 'note') return '正在生成笔记草稿…'
  if (kind === 'break') return '正在拆解爆文…'
  return '正在生成选题清单…'
}

export function xhsSuccessFallback(kind: XhsComputerKindLive): string {
  if (kind === 'note') return '已生成笔记草稿，右侧 Computer 可查看。'
  if (kind === 'break') return '已生成爆文拆解，右侧 Computer 可查看。'
  return '已生成选题清单，右侧 Computer 可查看。'
}

export function xhsEmptyFallback(kind: XhsComputerKindLive): string {
  if (kind === 'note') return '笔记生成已结束，但未收到可用草稿，请重试。'
  if (kind === 'break') return '拆解已结束，但未收到可用成果，请重试。'
  return '选题已结束，但未收到可用清单，请重试。'
}

export function xhsKindFromArtifactType(artifactType?: string | null): XhsComputerKind {
  const t = (artifactType || '').trim().toLowerCase()
  if (t === 'xhs_topiclist' || t === 'topiclist') return 'topiclist'
  if (t === 'xhs_break' || t === 'break') return 'break'
  if (t === 'xhs_note' || t === 'note') return 'note'
  return null
}
