import { Card, Typography } from 'antd'
import { useCurrentAdmin } from '@/features/auth/hooks/use-auth'

const { Title, Paragraph } = Typography

// 默认入口只说明当前真实可用的能力，不展示没有接口支撑的统计或操作。
export function HomePage() {
  const { data: admin } = useCurrentAdmin()

  return (
    <Card className="border-border-glass bg-surface-glass shadow-soft backdrop-blur-xl backdrop-saturate-150">
      <Title level={3} className="mb-2 font-semibold tracking-tight">
        欢迎，{admin?.username}
      </Title>
      <Paragraph type="secondary" className="mb-0">
        管理端基础能力已就绪。条目维护、富文本正文、图片附件与 AI 录入审核将在后续接入。
      </Paragraph>
    </Card>
  )
}
