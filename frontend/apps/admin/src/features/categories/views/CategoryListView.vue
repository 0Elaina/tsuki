<script setup lang="ts">
import {
  type DataTableRowKey,
  NAlert,
  NButton,
  NCard,
  NFlex,
  NH2,
  NIcon,
  NInput,
  NSelect,
  NText,
  useDialog,
  useMessage,
  type SelectOption,
} from "naive-ui";
import {
  CATEGORY_LIMITS,
  CATEGORY_STATUS,
  type CategoryItem,
  type CategoryStatus,
  type UpdateCategoryPayload,
} from "../types/category.types";
import { computed, ref, shallowRef, watch } from "vue";
import { useCategoryListQuery } from "../api/category.queries";
import { usePagination } from "@/shared/composables/usePagination";
import { Plus, RotateCcw, Search } from "lucide-vue-next";
import { getErrorMessage } from "@tsuki/api-client";
import {
  useBatchCategoryStatusMutation,
  useBatchDeleteCategoryMutation,
  useCreateCategoryMutation,
  useDeleteCategoryMutation,
  useUpdateCategoryMutation,
  useUpdateCategoryStatusMutation,
} from "../api/category.mutations";
import CategoryFormModal from "../components/CategoryFormModal.vue";
import CategoryTable from "../components/CategoryTable.vue";
import { useCategoryFilters } from "../composables/useCategoryFilters";

const { page, pageSize, setPage, createPagination } = usePagination();
const {
  keyword,
  status,
  keywordDraft,
  statusDraft,
  hasActiveFilters,
  handleSearch,
  handleReset,
} = useCategoryFilters(pageSize);

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
  keyword: keyword.value,
  status: status.value,
}));

const categoryListQuery = useCategoryListQuery(queryParams);
const categoryCreateMutation = useCreateCategoryMutation();
const categoryUpdateMutation = useUpdateCategoryMutation();
const categoryUpdateStatusMutation = useUpdateCategoryStatusMutation();
const categoryDeleteMutation = useDeleteCategoryMutation();
const categoryBatchStatusMutation = useBatchCategoryStatusMutation();
const categoryBatchDeleteMutation = useBatchDeleteCategoryMutation();

const categoryListItems = computed(
  () => categoryListQuery.data.value?.items ?? [],
);
const total = computed(() => categoryListQuery.data.value?.total ?? 0);
const formSubmitting = computed(
  () =>
    categoryCreateMutation.isPending.value ||
    categoryUpdateMutation.isPending.value,
);

const dialog = useDialog();
const checkedRowKeys = ref<DataTableRowKey[]>([]);
const selectedIds = computed(() => checkedRowKeys.value.map(String));
const batchPending = computed(
  () =>
    categoryBatchDeleteMutation.isPending.value ||
    categoryBatchStatusMutation.isPending.value,
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
      checkedRowKeys.value = checkedRowKeys.value.filter(
        (key) => String(key) !== id,
      );
      message.success("分类已删除");
    },
    onSettled: () => {
      if (deletingId.value === id) deletingId.value = null;
    },
  });
}

function handleClearSelection(): void {
  checkedRowKeys.value = [];
}

function validateBatchSelection(): boolean {
  if (selectedIds.value.length === 0) return false;
  if (selectedIds.value.length > CATEGORY_LIMITS.BATCH_MAX_IDS) {
    message.warning(`单次最多操作 ${CATEGORY_LIMITS.BATCH_MAX_IDS} 个分类`);
    return false;
  }
  return true;
}

function handleBatchStatus(targetStatus: CategoryStatus): void {
  if (!validateBatchSelection()) return;
  categoryBatchStatusMutation.mutate(
    {
      ids: selectedIds.value,
      status: targetStatus,
    },
    {
      onSuccess: (count) => {
        checkedRowKeys.value = [];
        if (count > 0) {
          message.success(`已更新 ${count} 个分类的状态`);
        } else {
          message.success("所选分类均已处于目标状态");
        }
      },
    },
  );
}

function handleBatchDelete(): void {
  if (!validateBatchSelection()) return;

  const d = dialog.warning({
    title: "确认批量删除",
    content: `确定删除选中的 ${selectedIds.value.length} 个分类吗, 此操作不可撤销`,
    positiveText: "确认删除",
    negativeText: "取消",
    onPositiveClick: () =>
      categoryBatchDeleteMutation
        .mutateAsync({
          ids: selectedIds.value,
        })
        .then((count) => {
          checkedRowKeys.value = [];
          message.success(`已删除 ${count} 个分类`);
        })
        // 防止弹窗点击后自动关闭
        .catch(() => false)
        // 防止重复点击确认
        .finally(() => {
          d.loading = false;
        }),
  });
}
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

        <!-- 批量操作栏 -->
        <NAlert v-if="selectedIds.length > 0" type="info" :show-icon="false">
          <NFlex align="center" justify="space-between" :wrap="true" :size="8">
            <NText>已选择 {{ selectedIds.length }} 项</NText>
            <NFlex align="center" :size="8">
              <NButton
                size="small"
                type="success"
                secondary
                :loading="categoryBatchStatusMutation.isPending.value"
                :disabled="batchPending"
                @click="handleBatchStatus(CATEGORY_STATUS.ACTIVE)"
              >
                批量启用
              </NButton>
              <NButton
                size="small"
                type="warning"
                secondary
                :loading="categoryBatchStatusMutation.isPending.value"
                :disabled="batchPending"
                @click="handleBatchStatus(CATEGORY_STATUS.DISABLED)"
              >
                批量停用
              </NButton>
              <NButton
                size="small"
                type="error"
                secondary
                :loading="categoryBatchDeleteMutation.isPending.value"
                :disabled="batchPending"
                @click="handleBatchDelete"
              >
                批量删除
              </NButton>
              <NButton size="small" quaternary @click="handleClearSelection">
                取消选择
              </NButton>
            </NFlex>
          </NFlex>
        </NAlert>

        <!-- 数据区域 -->
        <CategoryTable
          v-model:checked-row-keys="checkedRowKeys"
          :items="categoryListItems"
          :loading="categoryListQuery.isLoading.value"
          :fetching="categoryListQuery.isFetching.value"
          :is-error="categoryListQuery.isLoadingError.value"
          :error-message="
            getErrorMessage(
              categoryListQuery.error.value,
              '加载分类列表失败, 请稍候重试',
            )
          "
          :pagination="pagination"
          :has-active-filters="hasActiveFilters"
          :toggling-id="togglingId"
          :deleting-id="deletingId"
          @retry="handleRetry"
          @edit="openEditModal"
          @toggle-status="handleToggleStatus"
          @delete="handleDelete"
        />
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
