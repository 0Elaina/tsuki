import { matchQuery } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import type { AdminIdentity } from '@/features/auth/api/auth-api'
import { currentAdminQueryKey } from '@/features/auth/api/current-admin-query'

// 本次页面生命周期内最后一次确认的身份。首次访问的 /me 同样返回 null，
// 只有「曾为管理员、后变为 null」才说明会话是被动失效。
let lastKnownAdmin: AdminIdentity | null | undefined
let sessionExpired = false

// 身份查询给出权威结论时调用：管理员即恢复正常，null 则按此前的身份判断是否失效。
export function noteSessionResult(admin: AdminIdentity | null): void {
  if (admin !== null) {
    lastKnownAdmin = admin
    sessionExpired = false
    return
  }

  sessionExpired = lastKnownAdmin !== undefined && lastKnownAdmin !== null
  lastKnownAdmin = null
}

// 主动退出不是失效：清掉判断依据，避免后续未登录访问被误报为「登录已失效」。
export function noteVoluntaryLogout(): void {
  lastKnownAdmin = null
  sessionExpired = false
}

// 401 等被动失效：仅在已有身份时才认定为失效。
export function noteSessionInvalid(): void {
  sessionExpired = true
  lastKnownAdmin = null
}

export function isSessionExpired(): boolean {
  return sessionExpired
}

export async function updateSessionCache(
  queryClient: QueryClient,
  admin: AdminIdentity | null,
): Promise<void> {
  await queryClient.cancelQueries()

  // 保留身份查询对象，确保现有观察者能收到新的身份，而不是观察被移除的旧查询。
  queryClient.removeQueries({
    predicate: (query) => !matchQuery({ queryKey: currentAdminQueryKey, exact: true }, query),
  })
  queryClient.setQueryData<AdminIdentity | null>(currentAdminQueryKey, admin)
}
