<script setup lang="ts">
import {
  NButton,
  NCard,
  NDataTable,
  NFlex,
  NH2,
  NIcon,
  NInput,
  NPopconfirm,
  NSelect,
  NTag,
  NText,
  useMessage,
  type DataTableColumns,
  type SelectOption,
} from "naive-ui";
import {
  CATEGORY_STATUS,
  CATEGORY_STATUS_SET,
  type CategoryItem,
  type CategoryStatus,
  type UpdateCategoryPayload,
} from "../types/category.types";
import { computed, h, ref, shallowRef, watch } from "vue";
import { PAGE_LIMITS } from "@tsuki/shared/constants/pagination";
import { useCategoryListQuery } from "../api/category.queries";
import { useRoute, useRouter, type LocationQueryRaw } from "vue-router";
import { usePagination } from "@/shared/composables/usePagination";
import { formatDateTime } from "@tsuki/shared/utils/time";
import { Plus, RotateCcw, Search } from "lucide-vue-next";
import LoadingState from "@/shared/ui/LoadingState.vue";
import ErrorState from "@/shared/ui/ErrorState.vue";
import { getErrorMessage } from "@tsuki/api-client";
import EmptyState from "@/shared/ui/EmptyState.vue";
import {
  useCreateCategoryMutation,
  useDeleteCategoryMutation,
  useUpdateCategoryMutation,
  useUpdateCategoryStatusMutation,
} from "../api/category.mutations";
import CategoryFormModal from "../components/CategoryFormModal.vue";

const route = useRoute();
const router = useRouter();
const { page, pageSize, setPage, createPagination } = usePagination();
const message = useMessage();
const modalVisible = ref(false);
const editingCategory = shallowRef<CategoryItem | null>(null);

// 声明单行操作加锁
const togglingId = ref<string | null>(null);
const deletingId = ref<string | null>(null);

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
const categoryCreateMutation = useCreateCategoryMutation();
const categoryUpdateMutation = useUpdateCategoryMutation();
const categoryUpdateStatusMutation = useUpdateCategoryStatusMutation();
const categoryDeleteMutation = useDeleteCategoryMutation();

const categoryListItems = computed(
  () => categoryListQuery.data.value?.items ?? [],
);
const total = computed(() => categoryListQuery.data.value?.total ?? 0);
const hasActiveFilters = computed(
  () =>
    Boolean(queryParams.value.keyword) ||
    queryParams.value.status !== undefined,
);

const formSubmitting = computed(
  () =>
    categoryCreateMutation.isPending.value ||
    categoryUpdateMutation.isPending.value,
);

/** 空页自动回退 */
watch(
  () =>
    [categoryListQuery.isFetching.value, categoryListQuery.data.value] as const,
  ([fetching, data]) => {
    if (fetching || !data) return;
    if (data.page > 1 && data.items.length === 0) {
      const lastPage = Math.max(1, Math.ceil(data.total / data.pageSize));
      if (page.value !== lastPage) {
        setPage(lastPage);
      }
    }
  },
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

function openCreateModal(): void {
  editingCategory.value = null;
  modalVisible.value = true;
}

function openEditModal(row: CategoryItem): void {
  editingCategory.value = row;
  modalVisible.value = true;
}

function handleModalSubmit(payload: UpdateCategoryPayload): void {
  if (editingCategory.value) {
    categoryUpdateMutation.mutate(
      {
        id: editingCategory.value.id,
        data: payload,
      },
      {
        onSuccess: () => {
          modalVisible.value = false;
          message.success("分类更新成功");
        },
      },
    );
  } else {
    categoryCreateMutation.mutate(payload, {
      onSuccess: () => {
        modalVisible.value = false;
        message.success("分类创建成功");
      },
    });
  }
}

function handleToggleStatus(row: CategoryItem): void {
  const nextStatus =
    row.status === CATEGORY_STATUS.ACTIVE
      ? CATEGORY_STATUS.DISABLED
      : CATEGORY_STATUS.ACTIVE;
  togglingId.value = row.id;
  categoryUpdateStatusMutation.mutate(
    {
      id: row.id,
      status: nextStatus,
    },
    {
      onSuccess: () => {
        message.success(
          nextStatus === CATEGORY_STATUS.ACTIVE ? "分类已启用" : "分类已停用",
        );
      },
      onSettled: () => {
        if (togglingId.value === row.id) togglingId.value = null;
      },
    },
  );
}

function handleDelete(id: string): void {
  deletingId.value = id;
  categoryDeleteMutation.mutate(id, {
    onSuccess: () => {
      message.success("分类已删除");
    },
    onSettled: () => {
      if (deletingId.value === id) deletingId.value = null;
    },
  });
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
  {
    title: "操作",
    key: "actions",
    width: 200,
    render(row) {
      const isActive = row.status === CATEGORY_STATUS.ACTIVE;
      return h(
        NFlex,
        {
          size: 8,
          wrap: false,
        },
        {
          default: () => [
            h(
              NButton,
              {
                size: "small",
                quaternary: true,
                onClick: () => openEditModal(row),
              },
              {
                default: () => "编辑",
              },
            ),
            h(
              NButton,
              {
                size: "small",
                quaternary: true,
                type: isActive ? "warning" : "success",
                loading: togglingId.value === row.id,
                disabled: togglingId.value === row.id,
                onClick: () => handleToggleStatus(row),
              },
              {
                default: () => (isActive ? "停用" : "启用"),
              },
            ),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => handleDelete(row.id),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    {
                      size: "small",
                      quaternary: true,
                      type: "error",
                      loading: deletingId.value === row.id,
                      disabled: deletingId.value === row.id,
                    },
                    {
                      default: () => "删除",
                    },
                  ),
                default: () => `确认删除分类 ${row.name} 吗 ?`,
              },
            ),
          ],
        },
      );
    },
  },
];
</script>

<template>
  <NFlex vertical :size="12" class="w-full">
    <NCard>
      <NFlex align="center" justify="space-between" :wrap="true" :size="12">
        <NFlex vertical :size="4">
          <NH2 class="mb-0">分类管理</NH2>
          <NText :depth="3">维护曲库音乐分类、排序权重和启停状态</NText>
        </NFlex>
        <NButton type="primary" @click="openCreateModal">
          <template #icon>
            <NIcon :component="Plus" />
          </template>
          新建分类
        </NButton>
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
    <CategoryFormModal
      v-model:show="modalVisible"
      :category="editingCategory"
      :submitting="formSubmitting"
      @submit="handleModalSubmit"
    />
  </NFlex>
</template>
