import { Outlet, useNavigation } from 'react-router'

// 后续导航的等待反馈：不遮挡内容，只提示路由正在加载。
export function RootLayout() {
  const navigation = useNavigation()
  const isNavigating = navigation.state !== 'idle'

  return (
    <div className="relative min-h-dvh">
      {isNavigating ? (
        <div
          aria-hidden
          className="fixed inset-x-0 top-0 z-50 h-0.5 overflow-hidden bg-primary-subtle"
        >
          <div className="h-full w-1/3 animate-[progress_1.2s_ease-in-out_infinite] bg-linear-to-r from-primary to-accent" />
        </div>
      ) : null}
      <Outlet />
    </div>
  )
}
