import type { AxiosResponse } from 'axios'
import { normalizeUsername } from '@/features/auth/model/login-input'
import type { LoginInput } from '@/features/auth/model/login-input'
import { ApiError } from '@/shared/api/api-error'
import { apiClient } from '@/shared/api/client'
import type { ApiResponse } from '@/shared/api/contracts'
import type { paths } from '@/shared/api/generated/openapi'

const LOGIN_PATH = '/api/v1/admin/auth/login'
const CURRENT_ADMIN_PATH = '/api/v1/admin/auth/me'
const LOGOUT_PATH = '/api/v1/admin/auth/logout'

type LoginResponse =
  paths[typeof LOGIN_PATH]['post']['responses'][200]['content']['application/json']
type CurrentAdminResponse =
  paths[typeof CURRENT_ADMIN_PATH]['get']['responses'][200]['content']['application/json']
type LogoutResponse =
  paths[typeof LOGOUT_PATH]['post']['responses'][200]['content']['application/json']

export type AdminIdentity = NonNullable<CurrentAdminResponse['data']>
export type LoginResult = NonNullable<LoginResponse['data']>

function requireData<T>(response: AxiosResponse<ApiResponse<T>>): T {
  if (response.data.data === null) {
    throw new ApiError({
      kind: 'invalid-response',
      message: '服务器响应缺少认证数据',
      status: response.status,
      data: response.data,
    })
  }

  return response.data.data
}

export async function login(input: LoginInput): Promise<LoginResult> {
  const body: LoginInput = {
    username: normalizeUsername(input.username),
    password: input.password,
  }
  const response = await apiClient.post<LoginResponse>(LOGIN_PATH, body)

  return requireData<LoginResult>(response)
}

export async function getCurrentAdmin(signal?: AbortSignal): Promise<AdminIdentity> {
  const response = await apiClient.get<CurrentAdminResponse>(CURRENT_ADMIN_PATH, { signal })

  return requireData<AdminIdentity>(response)
}

export async function logout(): Promise<void> {
  await apiClient.post<LogoutResponse>(LOGOUT_PATH)
}
