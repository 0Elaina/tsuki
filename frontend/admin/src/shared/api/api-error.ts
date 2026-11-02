import { AxiosError, isAxiosError, isCancel } from 'axios'
import { isApiResponse, isValidationErrorData } from '@/shared/api/contracts'
import type { ValidationIssue } from '@/shared/api/contracts'

export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'invalid-response'

interface ApiErrorOptions {
  kind: ApiErrorKind
  message: string
  status?: number
  code?: string
  data?: unknown
  cause?: unknown
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind
  readonly status: number | undefined
  readonly code: string | undefined
  readonly data: unknown

  constructor(options: ApiErrorOptions) {
    super(options.message, { cause: options.cause })
    this.name = 'ApiError'
    this.kind = options.kind
    this.status = options.status
    this.code = options.code
    this.data = options.data
  }

  get validationIssues(): readonly ValidationIssue[] {
    return this.code === 'INVALID_REQUEST' && isValidationErrorData(this.data)
      ? this.data.errors
      : []
  }
}

export function normalizeApiError(error: unknown): unknown {
  // 保留取消标记与原始编程异常，避免将它们误识别为可重试的网络故障。
  if (isCancel(error) || error instanceof ApiError || !isAxiosError<unknown>(error)) {
    return error
  }

  if (error.response) {
    const { status, data } = error.response
    const body = isApiResponse(data) ? data : undefined

    return new ApiError({
      kind: 'http',
      message: body?.message || `请求失败（HTTP ${status}）`,
      status,
      code: body?.code,
      data: body ? body.data : data,
      cause: error,
    })
  }

  if (error.code === AxiosError.ETIMEDOUT) {
    return new ApiError({
      kind: 'timeout',
      message: '请求超时，请稍后重试',
      cause: error,
    })
  }

  if (error.code === AxiosError.ERR_NETWORK || error.code === AxiosError.ECONNABORTED) {
    return new ApiError({
      kind: 'network',
      message: '网络连接失败，请检查连接后重试',
      cause: error,
    })
  }

  return error
}
