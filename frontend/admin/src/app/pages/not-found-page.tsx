import { Button, Result } from 'antd'
import { Link } from 'react-router'

export function NotFoundPage() {
  return (
    <div className="rounded-card border border-border-glass bg-surface-glass shadow-soft backdrop-blur-xl backdrop-saturate-150">
      <Result
        status="404"
        title="页面不存在"
        subTitle="该地址没有对应的管理端页面。"
        extra={
          <Link to="/">
            <Button type="primary">返回首页</Button>
          </Link>
        }
      />
    </div>
  )
}
