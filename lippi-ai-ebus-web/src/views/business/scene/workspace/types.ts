export type SceneWorkspaceSpec = {
  sceneCode: string
  breadcrumb: string
  artifactTypes: string[]
  paneBySkillId: Record<string, string>
  paneByArtifactType: Record<string, string>
}
