<script setup lang="ts">
import {
  NDataTable,
  NTag,
  type DataTableColumns,
  type PaginationProps,
} from "naive-ui";
import { TAG_STATUS, type TagItem } from "../types/tag.types";
import { h } from "vue";
import { formatDateTime } from "@tsuki/shared/utils/time";
import LoadingState from "@/shared/ui/LoadingState.vue";
import ErrorState from "@/shared/ui/ErrorState.vue";
import EmptyState from "@/shared/ui/EmptyState.vue";

defineProps<{
  items: TagItem[];
  loading: boolean;
  fetching: boolean;
  isError: boolean;
  errorMessage: string;
  pagination: PaginationProps;
  hasActiveFilters: boolean;
}>();

const emits = defineEmits<{
  retry: [];
}>();

const columns: DataTableColumns<TagItem> = [
  {
    title: "标签名称",
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
      const isActive = row.status === TAG_STATUS.ACTIVE;
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
  <LoadingState v-if="loading" label="正在加载标签列表..." class="flex-1" />
  <ErrorState
    v-else-if="isError"
    :description="errorMessage"
    :retrying="fetching"
    class="flex-1"
    @retry="emits('retry')"
  />
  <NDataTable
    v-else
    remote
    flex-height
    class="min-h-0 flex-1"
    :columns="columns"
    :data="items"
    :loading="fetching"
    :row-key="(row: TagItem) => row.id"
    :pagination="pagination"
  >
    <template #empty>
      <EmptyState
        :description="hasActiveFilters ? '没有匹配的标签' : '暂无标签数据'"
      />
    </template>
  </NDataTable>
</template>
