import { useDialog, useMessage, type DataTableRowKey } from "naive-ui";
import { computed, ref } from "vue";
import {
  useBatchCategoryStatusMutation,
  useBatchDeleteCategoryMutation,
} from "../api/category.mutations";
import { CATEGORY_LIMITS, type CategoryStatus } from "../types/category.types";

/** 分类多选勾选与批量启停、删除编排 */
export function useCategoryBatchActions() {
  const message = useMessage();
  const dialog = useDialog();

  const checkedRowKeys = ref<DataTableRowKey[]>([]);

  const batchStatusMutation = useBatchCategoryStatusMutation();
  const batchDeleteMutation = useBatchDeleteCategoryMutation();

  const selectedIds = computed(() => checkedRowKeys.value.map(String));
  const batchPending = computed(
    () =>
      batchStatusMutation.isPending.value ||
      batchDeleteMutation.isPending.value,
  );

  function removeSelectedKey(id: string): void {
    checkedRowKeys.value = checkedRowKeys.value.filter(
      (key) => String(key) !== id,
    );
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
    batchStatusMutation.mutate(
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
        batchDeleteMutation
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

  return {
    checkedRowKeys,
    selectedIds,
    batchPending,
    removeSelectedKey,
    handleClearSelection,
    handleBatchStatus,
    handleBatchDelete,
  };
}
