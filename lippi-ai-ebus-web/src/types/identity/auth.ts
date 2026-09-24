export interface ApiResponse<T> {
  code: number
  success: boolean
  message: string
  data: T
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
  /** 须为 true；后端校验，不落库 */
  agreedToAiDisclaimer: boolean
}

export interface LoginRequest {
  account: string
  password: string
}

export interface RegisterResult {
  userId: string
  username: string
  email: string
}

export interface LoginResult {
  token: string
  userId: string
  username: string
  email: string
}

export interface Me {
  userId: string
  username: string
  email: string
}
