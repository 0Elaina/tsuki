import { DIGHTS_PATTERN } from "../constants/number";

/** 解析正整数 */
export function parsePositiveInt(
  raw: unknown,
  fallback: number,
  max?: number,
): number {
  if (typeof raw !== "string" || !DIGHTS_PATTERN.test(raw)) {
    return fallback;
  }

  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed < 1) return fallback;
  if (max !== undefined && parsed > max) return max;
  return parsed;
}
