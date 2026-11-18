import { toValue, type MaybeRefOrGetter } from "vue";
import type { TagListQuery } from "../types/tag.types";
import { keepPreviousData, queryOptions, useQuery } from "@tanstack/vue-query";
import { tagKeys } from "./tag.keys";
import { tagApi } from "./tag.api";

export function tagListQueryOptions(params: MaybeRefOrGetter<TagListQuery>) {
  return queryOptions({
    queryKey: () => tagKeys.list(toValue(params)),
    queryFn: ({ signal }) => tagApi.list(toValue(params), signal),
    placeholderData: keepPreviousData,
  });
}

export function useTagQuery(params: MaybeRefOrGetter<TagListQuery>) {
  return useQuery(tagListQueryOptions(params));
}
