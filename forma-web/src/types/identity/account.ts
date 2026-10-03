/** 账户资料（GET|PATCH /api/v1/account/profile）。邮箱只读；username 可改。 */
export interface AccountProfile {
  userId: string
  username: string
  email: string
}

export interface UpdateAccountProfileRequest {
  username: string
}

/** PUT /api/v1/account/password */
export interface ChangeAccountPasswordRequest {
  oldPassword: string
  newPassword: string
}
