<script setup lang="ts">
import {
  NButton,
  NCard,
  NDataTable,
  NFlex,
  NH2,
  NIcon,
  NInput,
  NSelect,
  NTag,
  NText,
  type DataTableColumns,
  type SelectOption,
} from "naive-ui";
import {
  CATEGORY_STATUS,
  CATEGORY_STATUS_SET,
  type CategoryItem,
  type CategoryStatus,
} from "../types/category.types";
import { computed, h, ref, watch } from "vue";
import { PAGE_LIMITS } from "@tsuki/shared/constants/pagination";
import { useCategoryListQuery } from "../api/category.queries";
import { useRoute, useRouter, type LocationQueryRaw } from "vue-router";
import { usePagination } from "@/shared/composables/usePagination";
import { formatDateTime } from "@tsuki/shared/utils/time";
import { RotateCcw, Search } from "lucide-vue-next";
import LoadingState from "@/shared/ui/LoadingState.vue";
import ErrorState from "@/shared/ui/ErrorState.vue";
import { getErrorMessage } from "@tsuki/api-client";
import EmptyState from "@/shared/ui/EmptyState.vue";

const route = useRoute();
const router = useRouter();
const { page, pageSize, createPagination } = usePagination();
const STATUS_OPTIONS: SelectOption[] = [
  {
    label: "启用",
    value: CATEGORY_STATUS.ACTIVE,
  },
  {
    label: "停用",
    value: CATEGORY_STATUS.DISABLED,
  },
];
const queryParams = computed(() => ({
  page: page.value,
  pageSize: pageSize.value,
  keyword:
    typeof route.query.keyword === "string" ? route.query.keyword.trim() : "",
  status:
    typeof route.query.status === "string" &&
    CATEGORY_STATUS_SET.has(route.query.status)
      ? (route.query.status as CategoryStatus)
      : undefined,
}));

const keywordDraft = ref("");
const statusDraft = ref<CategoryStatus | null>(null);

const categoryListQuery = useCategoryListQuery(queryParams);
const categoryListItems = computed(
  () => categoryListQuery.data.value?.items ?? [],
);
const total = computed(() => categoryListQuery.data.value?.total ?? 0);
const hasActiveFilters = computed(
  () =>
    Boolean(queryParams.value.keyword) ||
    queryParams.value.status !== undefined,
);

const pagination = createPagination(total);

/** URL 参数变化的监听器 */
watch(
  () => [queryParams.value.keyword, queryParams.value.status] as const,
  ([keyword, status]) => {
    keywordDraft.value = keyword ?? "";
    statusDraft.value = status ?? null;
  },
  { immediate: true },
);

function applyFilters(
  keyword: string,
  status: CategoryStatus | undefined,
): void {
  const query: LocationQueryRaw = {};
  // 筛选时保持 pagesize 不变
  if (pageSize.value !== PAGE_LIMITS.DEFAULT_PAGE_SIZE) {
    query.pageSize = String(pageSize.value);
  }
  const trimmed = keyword.trim();
  if (trimmed) {
    query.keyword = trimmed;
  }
  if (status !== undefined) query.status = status;
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

async function handleRetry(): Promise<void> {
  await categoryListQuery.refetch();
}

const columns: DataTableColumns<CategoryItem> = [
  {
    title: "分类名称",
    key: "name",
    minWidth: 160,
    ellipsis: {
      tooltip: true,
    },
  },
  {
    title: "排序值",
    key: "sortOrder",
    width: 100,
  },
  {
    title: "状态",
    key: "status",
    width: 100,
    render(row) {
      const isActive = row.status === CATEGORY_STATUS.ACTIVE;
      return h(
        NTag,
        {
          size: "small",
          bordered: false,
          type: isActive ? "success" : "default",
        },
        {
          default: () => (isActive ? "启用" : "停用"),
        },
      );
    },
  },
  {
    title: "更新时间",
    key: "updatedAt",
    width: 180,
    render(row) {
      return formatDateTime(row.updatedAt, "datetime");
    },
  },
];
</script>

<template>
  <NFlex vertical :size="12" class="w-full">
    <NCard>
      <NFlex vertical :size="4">
        <NH2 class="mb-0">分类管理</NH2>
        <NText :depth="3">维护曲库音乐分类、排序权重和启停状态</NText>
      </NFlex>
    </NCard>

    <!-- 列表卡片 -->
    <NCard>
      <NFlex vertical :size="12">
        <!-- 筛选栏 -->
        <NFlex align="center" :wrap="true" :size="8">
          <NInput
            v-model:value="keywordDraft"
            placeholder="搜索分类名称"
            clearable
            class="w-60"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <NIcon :component="Search" />
            </template>
          </NInput>
          <NSelect
            v-model:value="statusDraft"
            :options="STATUS_OPTIONS"
            placeholder="全部状态"
            clearable
            class="w-36"
          />
          <NButton
            type="primary"
            secondary
            :loading="categoryListQuery.isFetching.value"
            @click="handleSearch"
          >
            查询
          </NButton>
          <NButton
            :disabled="categoryListQuery.isFetching.value"
            @click="handleReset"
          >
            <template #icon>
              <NIcon :component="RotateCcw" />
            </template>
            重置
          </NButton>
        </NFlex>

        <!-- 数据区域 -->
        <LoadingState
          v-if="categoryListQuery.isLoading.value"
          label="正在加载分类列表..."
        />
        <ErrorState
          v-else-if="categoryListQuery.isLoadingError.value"
          :description="
            getErrorMessage(
              categoryListQuery.error.value,
              '加载分类列表失败, 请稍候重试',
            )
          "
          :retrying="categoryListQuery.isFetching.value"
          @retry="handleRetry"
        />
        <NDataTable
          v-else
          remote
          :columns="columns"
          :data="categoryListItems"
          :loading="categoryListQuery.isFetching.value"
          :row-key="(row: CategoryItem) => row.id"
          :pagination="pagination"
        >
          <template #empty>
            <EmptyState
              :description="
                hasActiveFilters ? '没有匹配的分类' : '暂无分类数据'
              "
            />
          </template>
        </NDataTable>
      </NFlex>
    </NCard>
  </NFlex>
</template>
