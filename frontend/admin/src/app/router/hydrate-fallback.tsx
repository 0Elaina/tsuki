import { FullPageStatus } from '@/app/router/full-page-status'

// 首屏路由模块加载期间的兜底；与身份恢复共用同一套居中等待视觉。
export function HydrateFallback() {
  return <FullPageStatus description="正在加载管理端" />
}
