import { DIGHTS_PATTERN } from "../constants/number";

/** 解析正整数 */
export function parsePositiveInt(raw: unknown, max?: number): number | null {
  if (typeof raw !== "string" || !DIGHTS_PATTERN.test(raw)) {
    return null;
  }

  const parsed = Number(raw);
  if (!Number.isSafeInteger(parsed) || parsed < 1) return null;
  if (max !== undefined && parsed > max) return max;
  return parsed;
}
