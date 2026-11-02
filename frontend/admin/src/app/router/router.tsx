import { createBrowserRouter } from 'react-router'
import { AdminErrorBoundary } from '@/app/router/admin-error-boundary'
import { AuthGate } from '@/app/router/auth-gate'
import { HydrateFallback } from '@/app/router/hydrate-fallback'
import { PublicOnly } from '@/app/router/public-only'
import { RootErrorBoundary } from '@/app/router/root-error-boundary'
import { RootLayout } from '@/app/router/root-layout'

// 路由树：登录页与受保护区域平级，两者各自有守卫，共用根布局与根错误边界。
// 子页面错误边界放在布局内部的无路径节点上，使异常只替换内容区，布局与退出入口保留；
// 布局自身加载或渲染失败则交给根错误边界。
export const router = createBrowserRouter([
  {
    path: '/',
    Component: RootLayout,
    ErrorBoundary: RootErrorBoundary,
    HydrateFallback,
    children: [
      {
        Component: PublicOnly,
        children: [
          {
            path: 'login',
            lazy: async () => {
              const { LoginPage } = await import('@/app/pages/login-page')
              return { Component: LoginPage }
            },
          },
        ],
      },
      {
        Component: AuthGate,
        children: [
          {
            lazy: async () => {
              const { AdminLayout } = await import('@/app/layouts/admin-layout')
              return { Component: AdminLayout }
            },
            children: [
              {
                ErrorBoundary: AdminErrorBoundary,
                children: [
                  {
                    index: true,
                    lazy: async () => {
                      const { HomePage } = await import('@/app/pages/home-page')
                      return { Component: HomePage }
                    },
                  },
                  {
                    path: '*',
                    lazy: async () => {
                      const { NotFoundPage } = await import('@/app/pages/not-found-page')
                      return { Component: NotFoundPage }
                    },
                  },
                ],
              },
            ],
          },
        ],
      },
    ],
  },
])
