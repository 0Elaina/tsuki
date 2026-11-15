export interface ResolveSafeRedirectOptions {
  excludePaths?: readonly string[];
}

export function resolveSafeRedirect(
  raw: unknown,
  options: ResolveSafeRedirectOptions = {},
): string | null {
  const { excludePaths = [] } = options;
  if (typeof raw !== "string") return null;
  const trimmed = raw.trim();
  // 拒绝站外 URL 与 协议相对路径
  if (!trimmed.startsWith("/") || trimmed.startsWith("//")) return null;
  // 提取去掉 query 与 hash 后的路径
  const pathname = trimmed.split(/[?#]/, 1)[0] ?? "";
  if (excludePaths.includes(pathname)) return null;
  return trimmed;
}
