export interface ResolveSafeRedirectOptions {
  fallback?: string;
  excludePaths?: readonly string[];
}

export function resolveSafeRedirect(
  raw: unknown,
  options: ResolveSafeRedirectOptions = {},
): string {
  const { fallback = "/", excludePaths = [] } = options;
  if (typeof raw !== "string") return fallback;
  const trimmed = raw.trim();
  // 拒绝站外 URL 与 协议相对路径
  if (!trimmed.startsWith("/") || trimmed.startsWith("//")) return fallback;
  // 提取去掉 query 与 hash 后的路径
  const pathname = trimmed.split(/[?#]/, 1)[0] ?? "";
  if (excludePaths.includes(pathname)) return fallback;
  return trimmed;
}
