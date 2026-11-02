import { StyleProvider } from '@ant-design/cssinjs'
import { App as AntApp, ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import type { PropsWithChildren } from 'react'
import { appTheme } from '@/app/theme/theme'

export function ThemeProvider({ children }: PropsWithChildren) {
  return (
    <StyleProvider layer>
      <ConfigProvider locale={zhCN} theme={appTheme}>
        <AntApp className="min-h-dvh bg-canvas font-sans text-content antialiased">
          {children}
        </AntApp>
      </ConfigProvider>
    </StyleProvider>
  )
}
