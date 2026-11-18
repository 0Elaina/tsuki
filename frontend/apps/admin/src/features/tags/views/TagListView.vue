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
  type SelectOption,
} from "naive-ui";
import { TAG_STATUS } from "../types/tag.types";
import { usePagination } from "@/shared/composables/usePagination";
import { useTagFilters } from "../composables/useTagFilters";
import { computed, watch } from "vue";
import { useTagListQuery } from "../api/tag.queries";
import { Plus, RotateCcw, Search } from "lucide-vue-next";
import TagTable from "../components/TagTable.vue";
import { getErrorMessage } from "@tsuki/api-client";
import { useTagFormModal } from "../composables/useTagFormModal";
import { useTagRowActions } from "../composables/useTagRowActions";
import TagFormModal from "../components/TagFormModal.vue";

const STATUS_OPTIONS: SelectOption[] = [
  {
    label: "启用",
    value: TAG_STATUS.ACTIVE,
  },
  {
    label: "停用",
    value: TAG_STATUS.DISABLED,
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
} = useTagFilters(pageSize);
const {
  modalVisible,
  editingTag,
  formSubmitting,
  openCreateTagModal,
  openEditTagModal,
  handleModalSubmit,
} = useTagFormModal();
const { togglingId, deletingId, handleToggleStatus, handleDelete } =
  useTagRowActions();

const queryParams = computed(() => ({
  page: page.value,
  pageSize: pageSize.value,
  keyword: keyword.value,
  status: status.value,
}));
const tagListQuery = useTagListQuery(queryParams);
const tagListItems = computed(() => tagListQuery.data.value?.items ?? []);
const total = computed(() => tagListQuery.data.value?.total ?? 0);
const pagination = createPagination(total);

watch(
  () => [tagListQuery.isFetching.value, tagListQuery.data.value] as const,
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
  await tagListQuery.refetch();
}
</script>

<template>
  <div class="flex min-h-120 w-full flex-1 flex-col">
    <NCard
      class="flex min-h-0 flex-1 flex-col"
      content-class="flex min-h-0 flex-1 flex-col"
    >
      <!-- 标题栏 -->
      <template #header>
        <NFlex vertical :size="4">
          <NH2 class="m-0!">标签管理</NH2>
          <NText :depth="3" class="text-sm font-normal">
            维护曲库音乐标签、排序权重和启停状态
          </NText>
        </NFlex>
      </template>

      <template #header-extra>
        <NButton type="primary" @click="openCreateTagModal">
          <template #icon>
            <NIcon :component="Plus" />
          </template>
          新建标签
        </NButton>
      </template>

      <!-- 主体 -->
      <div class="flex min-h-0 flex-1 flex-col gap-3">
        <!-- 筛选栏 -->
        <NFlex align="center" :wrap="true" :size="8" class="shrink-0">
          <!-- 关键词输入 -->
          <div class="w-60 shrink-0">
            <NInput
              v-model:value="keywordDraft"
              placeholder="搜索标签名称"
              clearable
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <NIcon :component="Search" />
              </template>
            </NInput>
          </div>
          <!-- 状态选择 -->
          <div class="w-36 shrink-0">
            <NSelect
              v-model:value="statusDraft"
              :options="STATUS_OPTIONS"
              placeholder="全部状态"
              clearable
            />
          </div>
          <!-- 查询按钮 -->
          <NButton
            type="primary"
            secondary
            :loading="tagListQuery.isFetching.value"
            @click="handleSearch"
          >
            查询
          </NButton>
          <!-- 重置按钮 -->
          <NButton
            :disabled="tagListQuery.isFetching.value"
            @click="handleReset"
          >
            <template #icon>
              <NIcon :component="RotateCcw" />
            </template>
            重置
          </NButton>
        </NFlex>
        <!-- 数据表格 -->
        <TagTable
          :items="tagListItems"
          :toggling-id="togglingId"
          :deleting-id="deletingId"
          :loading="tagListQuery.isLoading.value"
          :is-error="tagListQuery.isLoadingError.value"
          :fetching="tagListQuery.isFetching.value"
          :error-message="
            getErrorMessage(
              tagListQuery.error.value,
              '加载标签列表失败, 请稍候重试',
            )
          "
          :pagination="pagination"
          :has-active-filters="hasActiveFilters"
          @edit="openEditTagModal"
          @toggle-status="handleToggleStatus"
          @delete="handleDelete"
          @retry="handleRetry"
        />
      </div>
    </NCard>
    <TagFormModal
      v-model:show="modalVisible"
      :tag="editingTag"
      :submitting="formSubmitting"
      @submit="handleModalSubmit"
    />
  </div>
</template>
