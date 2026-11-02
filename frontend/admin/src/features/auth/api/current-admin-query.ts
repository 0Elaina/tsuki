import { queryOptions } from '@tanstack/react-query'
import { getCurrentAdmin } from '@/features/auth/api/auth-api'
import { noteSessionResult } from '@/features/auth/api/session-cache'
import { ApiError } from '@/shared/api/api-error'

export const currentAdminQueryKey = ['auth', 'me'] as const

// 登录凭据错误由登录表单处理，不属于已有会话需要重新认证的情况。
export function isAuthenticationRequired(error: unknown): error is ApiError {
  return (
    error instanceof ApiError &&
    error.status === 401 &&
    (error.code === 'AUTHENTICATION_REQUIRED' || error.code === 'INVALID_TOKEN')
  )
}

export const currentAdminQueryOptions = queryOptions({
  queryKey: currentAdminQueryKey,
  queryFn: async ({ signal }) => {
    // 在结果写入缓存前记录判断依据，守卫读取时标记已经就位。
    try {
      const admin = await getCurrentAdmin(signal)
      noteSessionResult(admin)
      return admin
    } catch (error) {
      if (isAuthenticationRequired(error)) {
        noteSessionResult(null)
        return null
      }

      throw error
    }
  },
})
