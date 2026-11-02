import { Spin } from 'antd'
import type { ReactNode } from 'react'
import { BrandMark } from '@/app/components/brand-mark'

interface FullPageStatusProps {
  description?: string
  children?: ReactNode
}

// 首屏与身份恢复共用同一套居中等待视觉，避免两段等待各闪一次。
export function FullPageStatus({ description, children }: FullPageStatusProps) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-5">
      <div className="[animation:fade-in_250ms_var(--ease-standard)_both]">
        <BrandMark size={52} />
      </div>
      <Spin size="large" description={description} />
      {children}
    </div>
  )
}
