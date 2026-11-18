import { toValue, type MaybeRefOrGetter } from "vue";
import type { CategoryListQuery } from "../types/category.types";
import { keepPreviousData, queryOptions, useQuery } from "@tanstack/vue-query";
import { categoryKeys } from "./category.keys";
import { categoryApi } from "./category.api";

export function categoryListQueryOptions(
  params: MaybeRefOrGetter<CategoryListQuery>,
) {
  return queryOptions({
    queryKey: () => categoryKeys.list(toValue(params)),
    queryFn: ({ signal }) => categoryApi.list(toValue(params), signal),
    placeholderData: keepPreviousData,
  });
}

export function useCategoryListQuery(
  params: MaybeRefOrGetter<CategoryListQuery>,
) {
  return useQuery(categoryListQueryOptions(params));
}
