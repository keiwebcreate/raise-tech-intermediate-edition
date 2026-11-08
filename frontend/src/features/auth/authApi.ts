// 認証の API（docs/features/authentication.md 8章）
import { api } from '../../api/client.ts'
import type { UserResponse } from '../../api/types.ts'

export type SignupInput = {
  email: string
  password: string
  username: string
  displayName: string
}

export type LoginInput = {
  email: string
  password: string
}

export async function signup(input: SignupInput): Promise<UserResponse> {
  return (await api.post<UserResponse>('/api/auth/signup', input)).data
}

export async function login(input: LoginInput): Promise<UserResponse> {
  return (await api.post<UserResponse>('/api/auth/login', input)).data
}

export async function logout(): Promise<void> {
  await api.post('/api/auth/logout')
}

// ログインしている本人。ログインしていなければ ApiError（401）
export async function getMe(): Promise<UserResponse> {
  return (await api.get<UserResponse>('/api/auth/me')).data
}
