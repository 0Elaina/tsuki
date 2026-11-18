import type { CategoryListQuery } from "../types/category.types";

export const categoryKeys = {
  all: ["admin", "categories"] as const,
  lists: () => [...categoryKeys.all, "list"] as const,
  list: (params: CategoryListQuery) =>
    [...categoryKeys.lists(), params] as const,
  detail: (id: string) => [...categoryKeys.all, "detail", id] as const,
} as const;
