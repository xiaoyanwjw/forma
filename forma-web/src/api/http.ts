const TOKEN_KEY = 'forma_jwt'

type AuthListener = () => void
const authListeners = new Set<AuthListener>()

function notifyAuthChange(): void {
  authListeners.forEach((listener) => listener())
}

/** Subscribe to setToken / clearToken; returns unsubscribe. */
export function onAuthChange(listener: AuthListener): () => void {
  authListeners.add(listener)
  return () => {
    authListeners.delete(listener)
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
  notifyAuthChange()
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
  notifyAuthChange()
}
