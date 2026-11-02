import {
  HomeOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons'
import { Alert, Avatar, Button, Menu, Space, Tooltip, Typography } from 'antd'
import { useEffect, useState } from 'react'
import { Outlet, useLocation, useNavigate } from 'react-router'
import { BrandMark } from '@/app/components/brand-mark'
import { useCurrentAdmin, useLogout } from '@/features/auth/hooks/use-auth'

const { Text } = Typography

const HOME_MENU_KEY = '/'
// 与 Tailwind 的 lg 断点一致：低于该宽度时侧栏改为浮层抽屉。
const NARROW_QUERY = '(max-width: 1023px)'

// 管理端外壳：顶栏承载身份与退出，悬浮侧栏承载导航，内容区交给业务页面。
export function AdminLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const { data: admin, isRefetchError, refetch } = useCurrentAdmin()
  const logout = useLogout()
  const [collapsed, setCollapsed] = useState(false)
  const [narrow, setNarrow] = useState(false)

  // 窄屏用浮层抽屉：展开时覆盖内容并加遮罩，避免挤压内容区。
  useEffect(() => {
    const query = window.matchMedia(NARROW_QUERY)
    const sync = () => {
      setNarrow(query.matches)
      setCollapsed(query.matches)
    }

    sync()
    query.addEventListener('change', sync)
    return () => query.removeEventListener('change', sync)
  }, [])

  const selectedKey = location.pathname === HOME_MENU_KEY ? HOME_MENU_KEY : ''

  function handleLogout(): void {
    logout.mutate(undefined, {
      onSuccess: () => {
        void navigate('/login', { replace: true })
      },
    })
  }

  return (
    <div className="flex min-h-dvh">
      {/* 窄屏展开时的遮罩，点击关闭 */}
      {narrow && !collapsed ? (
        <button
          type="button"
          aria-label="关闭导航"
          className="fixed inset-0 z-40 cursor-default bg-[rgb(31_27_46/0.24)] backdrop-blur-sm"
          onClick={() => setCollapsed(true)}
        />
      ) : null}

      <aside
        className={`p-4 ${
          narrow
            ? `fixed inset-y-0 left-0 z-50 w-55 transition-transform duration-200 ease-(--ease-standard) ${
                collapsed ? '-translate-x-full' : 'translate-x-0'
              }`
            : `sticky top-0 h-dvh shrink-0 transition-[width] duration-200 ease-(--ease-standard) ${
                collapsed ? 'w-0 p-0' : 'w-55'
              }`
        }`}
      >
        <div
          className={`flex h-[calc(100dvh-2rem)] flex-col overflow-hidden rounded-card border border-border-glass bg-surface-glass shadow-soft backdrop-blur-xl backdrop-saturate-150 transition-opacity duration-[--duration-base] ${
            collapsed ? 'pointer-events-none opacity-0' : 'opacity-100'
          }`}
        >
          <div className="flex items-center gap-2.5 px-4 pt-5 pb-2">
            <BrandMark size={30} />
            <Text strong className="text-[15px] tracking-tight">
              Tsuki
            </Text>
          </div>

          <Menu
            mode="inline"
            className="flex-1 border-none bg-transparent"
            selectedKeys={selectedKey ? [selectedKey] : []}
            onClick={({ key }) => {
              void navigate(key)
              if (narrow) {
                setCollapsed(true)
              }
            }}
            items={[{ key: HOME_MENU_KEY, icon: <HomeOutlined />, label: '首页' }]}
          />
        </div>
      </aside>

      <div className={`flex min-w-0 flex-1 flex-col gap-4 py-4 pr-4 ${narrow ? 'pl-4' : ''}`}>
        {/* 吸附时保持 16px 间距，内容从卡片下方穿过，形成悬浮层次。 */}
        <header className="sticky top-4 z-30 flex h-16 shrink-0 items-center justify-between gap-4 rounded-card border border-border-glass bg-surface-glass px-4 shadow-soft backdrop-blur-xl backdrop-saturate-150 sm:px-5">
          <Space size="middle">
            <Button
              type="text"
              aria-label={collapsed ? '展开导航' : '收起导航'}
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed((value) => !value)}
            />
            <Text strong className="text-[15px] tracking-tight">
              Tsuki
            </Text>
          </Space>

          <Space size="middle">
            {logout.isError ? (
              <Text type="danger" role="alert" className="text-[13px]">
                退出失败，请重试
              </Text>
            ) : null}

            <Space size="small">
              <Avatar
                size={30}
                className="bg-linear-to-br from-primary to-accent text-[13px] font-medium ring-2 ring-white/70"
              >
                {admin?.username.slice(0, 1).toUpperCase()}
              </Avatar>
              <Text className="hidden text-[13px] sm:inline">{admin?.username}</Text>
            </Space>

            <Tooltip title="退出登录">
              <Button
                type="text"
                icon={<LogoutOutlined />}
                loading={logout.isPending}
                onClick={handleLogout}
              >
                <span className="hidden sm:inline">退出</span>
              </Button>
            </Tooltip>
          </Space>
        </header>

        <main className="min-w-0 flex-1">
          {isRefetchError ? (
            <Alert
              className="mb-4"
              type="warning"
              showIcon
              closable
              title="身份信息刷新失败，当前显示的是上次结果"
              action={
                <Button size="small" type="link" onClick={() => void refetch()}>
                  重试
                </Button>
              }
            />
          ) : null}

          <div className="animate-[fade-in_250ms_var(--ease-standard)_both]">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
