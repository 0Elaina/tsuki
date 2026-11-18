import { PAGE_LIMITS } from "@tsuki/shared/constants/pagination";
import { parsePositiveInt } from "@tsuki/shared/utils/number";
import type { PaginationProps } from "naive-ui";
import {
  computed,
  toValue,
  type ComputedRef,
  type MaybeRefOrGetter,
} from "vue";
import { useRoute, useRouter, type LocationQueryRaw } from "vue-router";

export function usePagination() {
  const route = useRoute();
  const router = useRouter();

  const page = computed(
    () => parsePositiveInt(route.query.page) ?? PAGE_LIMITS.DEFAULT_PAGE,
  );
  const pageSize = computed(
    () =>
      parsePositiveInt(route.query.pageSize, PAGE_LIMITS.MAX_PAGE_SIZE) ??
      PAGE_LIMITS.DEFAULT_PAGE_SIZE,
  );

  function setPage(nextPage: number): void {
    const query: LocationQueryRaw = {
      ...route.query,
      page:
        nextPage !== PAGE_LIMITS.DEFAULT_PAGE ? String(nextPage) : undefined,
    };
    void router.replace({ query });
  }

  function setPageSize(nextPageSize: number): void {
    const query: LocationQueryRaw = {
      ...route.query,
      pageSize:
        nextPageSize !== PAGE_LIMITS.DEFAULT_PAGE_SIZE
          ? String(nextPageSize)
          : undefined,
    };
    void router.replace({ query });
  }

  function createPagination(
    total: MaybeRefOrGetter<number>,
  ): ComputedRef<PaginationProps> {
    return computed(() => ({
      page: page.value,
      pageSize: pageSize.value,
      itemCount: toValue(total),
      showSizePicker: true,
      pageSizes: [...PAGE_LIMITS.DEFAULT_PAGE_SIZES],
      onUpdatePage: setPage,
      onUpdatePageSize: setPageSize,
    }));
  }

  return { page, pageSize, setPage, setPageSize, createPagination };
}
