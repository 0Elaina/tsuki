import { FORMATTERS, type DateTimePreset } from "../constants/time";

export function formatDateTime(iso: string, preset: DateTimePreset): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return FORMATTERS[preset].format(date);
}
