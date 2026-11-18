import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";
import { useRoute, useRouter, type LocationQueryRaw } from "vue-router";
import { TAG_STATUS_SET, type TagStatus } from "../types/tag.types";
import { PAGE_LIMITS } from "@tsuki/shared/constants/pagination";

export function useTagFilters(pageSize: MaybeRefOrGetter<number>) {
  const route = useRoute();
  const router = useRouter();

  const keyword = computed(() =>
    typeof route.query.keyword === "string" ? route.query.keyword.trim() : "",
  );
  const status = computed(() =>
    typeof route.query.status === "string" &&
    TAG_STATUS_SET.has(route.query.status)
      ? (route.query.status as TagStatus)
      : undefined,
  );
  const keywordDraft = ref("");
  const statusDraft = ref<TagStatus | null>(null);
  const hasActiveFilters = computed(
    () => Boolean(keyword.value) || status.value !== undefined,
  );

  watch(
    () => [keyword.value, status.value] as const,
    ([nextKeyword, nextStatus]) => {
      keywordDraft.value = nextKeyword;
      statusDraft.value = nextStatus ?? null;
    },
    {
      immediate: true,
    },
  );

  function applyFilters(
    nextKeyword: string,
    nextStatus: TagStatus | undefined,
  ): void {
    const query: LocationQueryRaw = {};
    const curPageSize = toValue(pageSize);
    if (curPageSize !== PAGE_LIMITS.DEFAULT_PAGE_SIZE) {
      query.pageSize = String(curPageSize);
    }
    const trimmed = nextKeyword.trim();
    if (trimmed) {
      query.keyword = trimmed;
    }
    if (nextStatus !== undefined) {
      query.status = nextStatus;
    }
    void router.replace({ query });
  }

  function handleSearch(): void {
    applyFilters(keywordDraft.value, statusDraft.value ?? undefined);
  }

  function handleReset(): void {
    keywordDraft.value = "";
    statusDraft.value = null;
    applyFilters("", undefined);
  }

  return {
    keyword,
    status,
    keywordDraft,
    statusDraft,
    hasActiveFilters,
    handleSearch,
    handleReset,
  };
}
