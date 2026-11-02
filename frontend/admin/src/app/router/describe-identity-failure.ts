import { ApiError } from '@/shared/api/api-error'

// 把身份查询失败翻译成可操作说明；配置类问题与网络故障的处理方向不同。
export function describeIdentityFailure(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.kind === 'invalid-response') {
      return '服务器返回的不是管理接口数据，通常是前端 API 地址配置错误。请检查 VITE_API_BASE_URL。'
    }

    if (error.kind === 'network') {
      return '无法连接后端服务，请确认后端已启动且允许当前来源访问。'
    }

    if (error.kind === 'timeout') {
      return '后端响应超时，请稍后重试。'
    }

    if (error.status !== undefined && error.status >= 500) {
      return '后端服务处理失败，请查看服务端日志后重试。'
    }
  }

  return '身份信息加载失败，请检查网络或后端服务后重试。'
}
