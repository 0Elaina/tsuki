import { type DataTableRowKey, useDialog, useMessage } from "naive-ui";
import { computed, ref } from "vue";
import {
  useBatchDeleteTagMutation,
  useBatchTagStatusMutation,
} from "../api/tag.mutations";
import { TAG_LIMITS, type TagStatus } from "../types/tag.types";

/** 标签多选勾选与批量启停、删除编排 */
export function useTagBatchActions() {
  const message = useMessage();
  const dialog = useDialog();

  const checkedRowKeys = ref<DataTableRowKey[]>([]);

  const batchStatusMutation = useBatchTagStatusMutation();
  const batchDeleteMutation = useBatchDeleteTagMutation();

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
    if (selectedIds.value.length > TAG_LIMITS.BATCH_MAX_IDS) {
      message.warning(`单次最多操作 ${TAG_LIMITS.BATCH_MAX_IDS} 个标签`);
      return false;
    }
    return true;
  }

  function handleBatchStatus(targetStatus: TagStatus): void {
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
            message.success(`已更新 ${count} 个标签的状态`);
          } else {
            message.success("所选标签均已处于目标状态");
          }
        },
      },
    );
  }

  function handleBatchDelete(): void {
    if (!validateBatchSelection()) return;

    const d = dialog.warning({
      title: "确认批量删除",
      content: `确定删除选中的 ${selectedIds.value.length} 个标签吗, 此操作不可撤销`,
      positiveText: "确认删除",
      negativeText: "取消",
      onPositiveClick: () =>
        batchDeleteMutation
          .mutateAsync({
            ids: selectedIds.value,
          })
          .then((count) => {
            checkedRowKeys.value = [];
            message.success(`已删除 ${count} 个标签`);
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
