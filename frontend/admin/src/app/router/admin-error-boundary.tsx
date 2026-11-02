import { Button, Result, Space } from 'antd'
import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

// 管理端子页面错误边界：外层布局仍在，错误只替换内容区。
export function AdminErrorBoundary() {
  const error = useRouteError()
  const isNotFound = isRouteErrorResponse(error) && error.status === 404

  const title = isNotFound ? '页面不存在' : '页面加载失败'
  const description = isRouteErrorResponse(error)
    ? error.statusText || '该地址没有对应的管理端页面。'
    : '当前页面渲染或数据加载出错，其他页面仍可正常访问。'

  return (
    <div className="rounded-card border border-border-glass bg-surface-glass shadow-soft backdrop-blur-xl backdrop-saturate-150">
      <Result
        status={isNotFound ? '404' : 'error'}
        title={title}
        subTitle={description}
        extra={
          <Space>
            <Link to="/">
              <Button type="primary">返回首页</Button>
            </Link>
          </Space>
        }
      />
    </div>
  )
}
