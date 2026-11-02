import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'
import type { AdminIdentity } from '@/features/auth/api/auth-api'
import {
  currentAdminQueryKey,
  isAuthenticationRequired,
} from '@/features/auth/api/current-admin-query'
import { noteSessionInvalid, updateSessionCache } from '@/features/auth/api/session-cache'
import { ApiError } from '@/shared/api/api-error'

const MAX_QUERY_RETRIES = 1

function isSessionQuery(queryKey: readonly unknown[]): boolean {
  return (
    queryKey.length === currentAdminQueryKey.length &&
    currentAdminQueryKey.every((part, index) => queryKey[index] === part)
  )
}

function shouldRetryQuery(failureCount: number, error: Error): boolean {
  if (failureCount >= MAX_QUERY_RETRIES || !(error instanceof ApiError)) {
    return false
  }

  if (error.kind === 'network' || error.kind === 'timeout') {
    return true
  }

  return (
    error.kind === 'http' && error.status !== undefined && error.status >= 500 && error.status < 600
  )
}

// QueryCache 回调不等待异步任务，这里显式承接；重复的失效结果由 updateSessionCache 幂等吸收。
function handleSessionInvalid(error: unknown): void {
  if (!isAuthenticationRequired(error)) {
    return
  }

  // 身份尚未恢复时不抢先判定未登录，交由身份查询自身的结果决定守卫走向。
  const current = queryClient.getQueryData<AdminIdentity | null>(currentAdminQueryKey)
  if (!current) {
    return
  }

  noteSessionInvalid()
  void updateSessionCache(queryClient, null)
}

export const queryClient = new QueryClient({
  queryCache: new QueryCache({
    onError: (error) => handleSessionInvalid(error),
    onSuccess: (data, query) => {
      // 失效判定已在 queryFn 内完成，这里只负责把未登录结论同步到其余缓存。
      if (data === null && isSessionQuery(query.queryKey)) {
        void updateSessionCache(queryClient, null)
      }
    },
  }),
  mutationCache: new MutationCache({
    onError: (error) => handleSessionInvalid(error),
  }),
  defaultOptions: {
    queries: {
      retry: shouldRetryQuery,
      staleTime: 0,
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
    },
    mutations: {
      retry: false,
    },
  },
})
