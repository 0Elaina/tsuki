import { useDialog, useMessage, type DataTableRowKey } from "naive-ui";
import { computed, ref, shallowRef } from "vue";
import {
  useBatchCategoryStatusMutation,
  useBatchDeleteCategoryMutation,
  useCreateCategoryMutation,
  useDeleteCategoryMutation,
  useUpdateCategoryMutation,
  useUpdateCategoryStatusMutation,
} from "../api/category.mutations";
import {
  CATEGORY_LIMITS,
  CATEGORY_STATUS,
  type CategoryItem,
  type CategoryStatus,
  type UpdateCategoryPayload,
} from "../types/category.types";

/** 分类新增、编辑、单行操作与批量操作编排 */
export function useCategoryActions() {
  const message = useMessage();
  const dialog = useDialog();

  const modalVisible = ref(false);
  const editingCategory = shallowRef<CategoryItem | null>(null);
  const togglingId = ref<string | null>(null);
  const deletingId = ref<string | null>(null);
  const checkedRowKeys = ref<DataTableRowKey[]>([]);

  const categoryCreateMutation = useCreateCategoryMutation();
  const categoryUpdateMutation = useUpdateCategoryMutation();
  const categoryUpdateStatusMutation = useUpdateCategoryStatusMutation();
  const categoryDeleteMutation = useDeleteCategoryMutation();
  const categoryBatchStatusMutation = useBatchCategoryStatusMutation();
  const categoryBatchDeleteMutation = useBatchDeleteCategoryMutation();

  const formSubmitting = computed(
    () =>
      categoryCreateMutation.isPending.value ||
      categoryUpdateMutation.isPending.value,
  );
  const selectedIds = computed(() => checkedRowKeys.value.map(String));
  const batchStatusPending = computed(
    () => categoryBatchStatusMutation.isPending.value,
  );
  const batchDeletePending = computed(
    () => categoryBatchDeleteMutation.isPending.value,
  );
  const batchPending = computed(
    () => batchStatusPending.value || batchDeletePending.value,
  );

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

  return {
    modalVisible,
    editingCategory,
    togglingId,
    deletingId,
    checkedRowKeys,
    selectedIds,
    formSubmitting,
    batchPending,
    batchStatusPending,
    batchDeletePending,
    openCreateModal,
    openEditModal,
    handleModalSubmit,
    handleToggleStatus,
    handleDelete,
    handleClearSelection,
    handleBatchStatus,
    handleBatchDelete,
  };
}
