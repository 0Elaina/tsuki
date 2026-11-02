import { Navigate, Outlet, useLocation } from 'react-router'
import { describeIdentityFailure } from '@/app/router/describe-identity-failure'
import { FullPageError } from '@/app/router/full-page-error'
import { FullPageStatus } from '@/app/router/full-page-status'
import { isSessionExpired } from '@/features/auth/api/session-cache'
import { useCurrentAdmin } from '@/features/auth/hooks/use-auth'

// 已登录才挂载受保护内容；身份查询本身失败时保留重试入口，不当作未登录。
export function AuthGate() {
  const location = useLocation()
  const { data, isError, error, refetch } = useCurrentAdmin()

  if (data === undefined) {
    if (isError) {
      return (
        <FullPageError
          title="无法确认登录状态"
          description={describeIdentityFailure(error)}
          onRetry={() => void refetch()}
        />
      )
    }

    return <FullPageStatus description="正在恢复登录状态" />
  }

  if (data === null) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`

    // 只有「曾经登录、现已失效」才带原因；首次访问的未登录不带提示。
    const state = isSessionExpired() ? { reason: 'session-expired' } : undefined

    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace state={state} />
  }

  return <Outlet />
}
