export type DateTimePreset = "datetime" | "date" | "time";

export const FORMATTERS: Record<DateTimePreset, Intl.DateTimeFormat> = {
  datetime: new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "short",
    timeStyle: "medium",
  }),
  date: new Intl.DateTimeFormat("Zh-CN", {
    dateStyle: "short",
  }),
  time: new Intl.DateTimeFormat("Zh-CN", {
    timeStyle: "medium",
  }),
};
