import { Button, Result, Space, Typography } from 'antd'

const { Text } = Typography

interface FullPageErrorProps {
  title: string
  description: string
  onRetry?: () => void
  retryLabel?: string
}

// 首次身份恢复失败等场景：给出说明与重试，不把用户误判为未登录。
// 重试之外保留重新加载出口，避免配置类故障把用户困在同一个失败请求里。
export function FullPageError({
  title,
  description,
  onRetry,
  retryLabel = '重试',
}: FullPageErrorProps) {
  return (
    <div className="flex min-h-dvh items-center justify-center px-6">
      <div className="w-full max-w-lg [animation:fade-in_250ms_var(--ease-standard)_both] rounded-card border border-border-glass bg-surface-glass p-6 shadow-raised backdrop-blur-xl backdrop-saturate-150">
        <Result
          status="warning"
          title={title}
          subTitle={<Text type="secondary">{description}</Text>}
          extra={
            <Space>
              {onRetry ? (
                <Button type="primary" onClick={onRetry}>
                  {retryLabel}
                </Button>
              ) : null}
              <Button onClick={() => window.location.reload()}>重新加载页面</Button>
            </Space>
          }
        />
      </div>
    </div>
  )
}
