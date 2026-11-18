import { PAGE_LIMITS } from "@tsuki/shared/constants/pagination";
import { computed, ref, toValue, watch, type MaybeRefOrGetter } from "vue";
import { useRoute, useRouter, type LocationQueryRaw } from "vue-router";
import {
  CATEGORY_STATUS_SET,
  type CategoryStatus,
} from "../types/category.types";

/** 分类列表 URL 筛选参数与输入草稿同步 */
export function useCategoryFilters(pageSize: MaybeRefOrGetter<number>) {
  const route = useRoute();
  const router = useRouter();

  const keyword = computed(() =>
    typeof route.query.keyword === "string" ? route.query.keyword.trim() : "",
  );
  const status = computed<CategoryStatus | undefined>(() =>
    typeof route.query.status === "string" &&
    CATEGORY_STATUS_SET.has(route.query.status)
      ? (route.query.status as CategoryStatus)
      : undefined,
  );

  const keywordDraft = ref("");
  const statusDraft = ref<CategoryStatus | null>(null);
  const hasActiveFilters = computed(
    () => Boolean(keyword.value) || status.value !== undefined,
  );

  /** URL 参数变化时回填草稿 */
  watch(
    () => [keyword.value, status.value] as const,
    ([nextKeyword, nextStatus]) => {
      keywordDraft.value = nextKeyword;
      statusDraft.value = nextStatus ?? null;
    },
    { immediate: true },
  );

  function applyFilters(
    nextKeyword: string,
    nextStatus: CategoryStatus | undefined,
  ): void {
    const query: LocationQueryRaw = {};
    // 筛选时保持 pageSize 不变
    const currentSize = toValue(pageSize);
    if (currentSize !== PAGE_LIMITS.DEFAULT_PAGE_SIZE) {
      query.pageSize = String(currentSize);
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
