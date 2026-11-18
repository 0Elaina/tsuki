<script setup lang="ts">
import { usePagination } from "@/shared/composables/usePagination";
import { getErrorMessage } from "@tsuki/api-client";
import { Plus, RotateCcw, Search } from "lucide-vue-next";
import {
  NButton,
  NCard,
  NFlex,
  NH2,
  NIcon,
  NInput,
  NSelect,
  NText,
  type SelectOption,
} from "naive-ui";
import { computed, watch } from "vue";
import { useCategoryListQuery } from "../api/category.queries";
import CategoryBatchBar from "../components/CategoryBatchBar.vue";
import CategoryFormModal from "../components/CategoryFormModal.vue";
import CategoryTable from "../components/CategoryTable.vue";
import { useCategoryBatchActions } from "../composables/useCategoryBatchActions";
import { useCategoryFilters } from "../composables/useCategoryFilters";
import { useCategoryFormModal } from "../composables/useCategoryFormModal";
import { useCategoryRowActions } from "../composables/useCategoryRowActions";
import { CATEGORY_STATUS } from "../types/category.types";

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

const queryParams = computed(() => ({
  page: page.value,
  pageSize: pageSize.value,
  keyword: keyword.value,
  status: status.value,
}));

const categoryListQuery = useCategoryListQuery(queryParams);
const categoryListItems = computed(
  () => categoryListQuery.data.value?.items ?? [],
);
const total = computed(() => categoryListQuery.data.value?.total ?? 0);
const pagination = createPagination(total);

const {
  modalVisible,
  editingCategory,
  formSubmitting,
  openCreateModal,
  openEditModal,
  handleModalSubmit,
} = useCategoryFormModal();

const {
  checkedRowKeys,
  selectedIds,
  batchPending,
  batchStatusPending,
  batchDeletePending,
  removeSelectedKey,
  handleClearSelection,
  handleBatchStatus,
  handleBatchDelete,
} = useCategoryBatchActions();

const { togglingId, deletingId, handleToggleStatus, handleDelete } =
  useCategoryRowActions({
    onDeleted: removeSelectedKey,
  });

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

async function handleRetry(): Promise<void> {
  await categoryListQuery.refetch();
}
</script>

<template>
  <div class="flex min-h-120 w-full flex-1 flex-col">
    <NCard
      class="flex min-h-0 flex-1 flex-col"
      content-class="flex min-h-0 flex-1 flex-col"
    >
      <template #header>
        <NFlex vertical :size="4">
          <NH2 :style="{ margin: 0 }">分类管理</NH2>
          <NText :depth="3" class="text-sm font-normal">
            维护曲库音乐分类、排序权重和启停状态
          </NText>
        </NFlex>
      </template>

      <template #header-extra>
        <NButton type="primary" @click="openCreateModal">
          <template #icon>
            <NIcon :component="Plus" />
          </template>
          新建分类
        </NButton>
      </template>

      <div class="flex min-h-0 flex-1 flex-col gap-3">
        <!-- 筛选栏 -->
        <NFlex align="center" :wrap="true" :size="8" class="shrink-0">
          <div class="w-60 shrink-0">
            <NInput
              v-model:value="keywordDraft"
              placeholder="搜索分类名称"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <NIcon :component="Search" />
              </template>
            </NInput>
          </div>
          <div class="w-36 shrink-0">
            <NSelect
              v-model:value="statusDraft"
              :options="STATUS_OPTIONS"
              placeholder="全部状态"
              clearable
            />
          </div>
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
        <CategoryBatchBar
          :count="selectedIds.length"
          :batch-pending="batchPending"
          :batch-status-pending="batchStatusPending"
          :batch-delete-pending="batchDeletePending"
          @batch-status="handleBatchStatus"
          @batch-delete="handleBatchDelete"
          @clear="handleClearSelection"
        />

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
      </div>
    </NCard>

    <CategoryFormModal
      v-model:show="modalVisible"
      :category="editingCategory"
      :submitting="formSubmitting"
      @submit="handleModalSubmit"
    />
  </div>
</template>
