<script setup lang="ts">
import EmptyState from "@/shared/ui/EmptyState.vue";
import ErrorState from "@/shared/ui/ErrorState.vue";
import LoadingState from "@/shared/ui/LoadingState.vue";
import { formatDateTime } from "@tsuki/shared/utils/time";
import {
  type DataTableRowKey,
  NButton,
  NDataTable,
  NFlex,
  NPopconfirm,
  NTag,
  type DataTableColumns,
  type PaginationProps,
} from "naive-ui";
import { h } from "vue";
import { CATEGORY_STATUS, type CategoryItem } from "../types/category.types";

const props = withDefaults(
  defineProps<{
    items: CategoryItem[];
    loading: boolean;
    fetching: boolean;
    isError: boolean;
    errorMessage: string;
    pagination: PaginationProps;
    hasActiveFilters: boolean;
    togglingId?: string | null;
    deletingId?: string | null;
  }>(),
  {
    togglingId: null,
    deletingId: null,
  },
);

const checkedRowKeys = defineModel<DataTableRowKey[]>("checkedRowKeys", {
  required: true,
});

const emits = defineEmits<{
  retry: [];
  edit: [row: CategoryItem];
  toggleStatus: [row: CategoryItem];
  delete: [id: string];
}>();

const columns: DataTableColumns<CategoryItem> = [
  {
    type: "selection",
  },
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
                onClick: () => emits("edit", row),
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
                loading: props.togglingId === row.id,
                disabled: props.togglingId === row.id,
                onClick: () => emits("toggleStatus", row),
              },
              {
                default: () => (isActive ? "停用" : "启用"),
              },
            ),
            h(
              NPopconfirm,
              {
                onPositiveClick: () => emits("delete", row.id),
              },
              {
                trigger: () =>
                  h(
                    NButton,
                    {
                      size: "small",
                      quaternary: true,
                      type: "error",
                      loading: props.deletingId === row.id,
                      disabled: props.deletingId === row.id,
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
  <LoadingState v-if="loading" label="正在加载分类列表..." class="flex-1" />
  <ErrorState
    v-else-if="isError"
    :description="errorMessage"
    :retrying="fetching"
    class="flex-1"
    @retry="emits('retry')"
  />
  <NDataTable
    v-else
    v-model:checked-row-keys="checkedRowKeys"
    remote
    flex-height
    class="min-h-0 flex-1"
    :columns="columns"
    :data="items"
    :loading="fetching"
    :row-key="(row: CategoryItem) => row.id"
    :pagination="pagination"
  >
    <template #empty>
      <EmptyState
        :description="hasActiveFilters ? '没有匹配的分类' : '暂无分类数据'"
      />
    </template>
  </NDataTable>
</template>
