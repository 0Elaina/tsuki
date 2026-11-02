// 只接受以单个 / 开头的站内地址；缺失、非法或指向登录页时回到根路径。
const ROOT_PATH = '/'
const LOGIN_PATH = '/login'

export function resolveReturnTo(value: string | null, origin: string): string {
  if (value === null) {
    return ROOT_PATH
  }

  // 协议相对地址（//host）与反斜杠变体都会被 URL 解析到其他 origin，统一在此拒绝。
  if (!value.startsWith('/') || value.startsWith('//')) {
    return ROOT_PATH
  }

  let url: URL
  try {
    url = new URL(value, origin)
  } catch {
    return ROOT_PATH
  }

  if (url.origin !== origin || url.pathname === LOGIN_PATH) {
    return ROOT_PATH
  }

  return `${url.pathname}${url.search}${url.hash}`
}
