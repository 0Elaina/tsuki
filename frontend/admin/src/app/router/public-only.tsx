import { Navigate, Outlet, useSearchParams } from 'react-router'
import { describeIdentityFailure } from '@/app/router/describe-identity-failure'
import { resolveReturnTo } from '@/app/router/return-to'
import { FullPageError } from '@/app/router/full-page-error'
import { FullPageStatus } from '@/app/router/full-page-status'
import { useCurrentAdmin } from '@/features/auth/hooks/use-auth'

// 已有身份时离开登录页；身份尚未恢复前不渲染登录表单，避免表单闪现。
export function PublicOnly() {
  const [searchParams] = useSearchParams()
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

  if (data !== null) {
    return (
      <Navigate
        to={resolveReturnTo(searchParams.get('returnTo'), window.location.origin)}
        replace
      />
    )
  }

  return <Outlet />
}
