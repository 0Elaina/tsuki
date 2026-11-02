import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { ApiError, normalizeApiError } from '@/shared/api/api-error'
import { isApiResponse } from '@/shared/api/contracts'
import { env } from '@/shared/config/env'

const DEFAULT_REQUEST_TIMEOUT_MS = 15_000
const SAFE_HTTP_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

// 仅用于返回标准 JSON 包裹的管理 API。
export const apiClient = axios.create({
  baseURL: env.apiBaseUrl,
  withCredentials: true,
  timeout: DEFAULT_REQUEST_TIMEOUT_MS,
  transitional: { clarifyTimeoutError: true },
})

apiClient.interceptors.request.use((config) => {
  const method = (config.method ?? 'GET').toUpperCase()

  if (!SAFE_HTTP_METHODS.has(method)) {
    config.headers.set('X-CSRF-Protection', '1')
  }

  return config
})

apiClient.interceptors.response.use(
  (response: AxiosResponse<unknown>) => {
    const body = isApiResponse(response.data) ? response.data : undefined

    if (!body || body.code !== 'SUCCESS') {
      throw new ApiError({
        kind: 'invalid-response',
        message: '服务器响应格式不正确',
        status: response.status,
        code: body?.code,
        data: response.data,
      })
    }

    return response
  },
  (error: unknown) => {
    throw normalizeApiError(error)
  },
)
