<script setup lang="ts">
import {
  NButton,
  NCard,
  NFlex,
  NH2,
  NIcon,
  NInput,
  NSelect,
  NText,
  useMessage,
  type SelectOption,
} from "naive-ui";
import {
  CATEGORY_STATUS,
  type CategoryItem,
  type UpdateCategoryPayload,
} from "../types/category.types";
import { computed, ref, shallowRef, watch } from "vue";
import { useCategoryListQuery } from "../api/category.queries";
import { usePagination } from "@/shared/composables/usePagination";
import { Plus, RotateCcw, Search } from "lucide-vue-next";
import { getErrorMessage } from "@tsuki/api-client";
import {
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

const categoryListItems = computed(
  () => categoryListQuery.data.value?.items ?? [],
);
const total = computed(() => categoryListQuery.data.value?.total ?? 0);
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
        <CategoryTable
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
