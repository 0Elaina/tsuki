import { Button, Result, Space } from 'antd'
import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

function describeError(error: unknown): {
  isNotFound: boolean
  title: string
  description: string
} {
  if (isRouteErrorResponse(error)) {
    return {
      isNotFound: error.status === 404,
      title: error.status === 404 ? '页面不存在' : '请求无法完成',
      description: error.statusText || '路由处理请求时返回了错误状态。',
    }
  }

  if (error instanceof Error) {
    return {
      isNotFound: false,
      title: '页面出现异常',
      description: '页面渲染或模块加载失败，可以返回首页或刷新后重试。',
    }
  }

  return {
    isNotFound: false,
    title: '页面出现异常',
    description: '发生未知异常，可以返回首页或刷新后重试。',
  }
}

// 根错误边界：整棵路由树失效时使用，因此只提供站内链接与刷新，不依赖外层布局。
export function RootErrorBoundary() {
  const error = useRouteError()
  const { isNotFound, title, description } = describeError(error)

  return (
    <div className="flex min-h-dvh items-center justify-center px-6">
      <div className="w-full max-w-lg [animation:fade-in_250ms_var(--ease-standard)_both] rounded-card border border-border-glass bg-surface-glass p-6 shadow-raised backdrop-blur-xl backdrop-saturate-150">
        <Result
          status={isNotFound ? '404' : 'error'}
          title={title}
          subTitle={description}
          extra={
            <Space>
              <Link to="/">
                <Button type="primary">返回首页</Button>
              </Link>
              <Button onClick={() => window.location.reload()}>刷新页面</Button>
            </Space>
          }
        />
      </div>
    </div>
  )
}
