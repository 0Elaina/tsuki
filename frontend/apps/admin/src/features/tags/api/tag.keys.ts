import type { TagListQuery } from "../types/tag.types";

export const tagKeys = {
  all: ["admin", "tags"] as const,
  lists: () => [...tagKeys.all, "list"] as const,
  list: (params: TagListQuery) => [...tagKeys.lists(), params] as const,
  detail: (id: string) => [...tagKeys.all, "detail", id] as const,
} as const;
