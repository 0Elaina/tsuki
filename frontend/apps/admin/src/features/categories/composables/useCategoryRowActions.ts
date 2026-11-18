import { useMessage } from "naive-ui";
import { ref } from "vue";
import {
  useDeleteCategoryMutation,
  useUpdateCategoryStatusMutation,
} from "../api/category.mutations";
import { CATEGORY_STATUS, type CategoryItem } from "../types/category.types";

export interface UseCategoryRowActionsOptions {
  onDeleted?: (id: string) => void;
}

/** 分类单条启停与删除操作编排 */
export function useCategoryRowActions(
  options: UseCategoryRowActionsOptions = {},
) {
  const message = useMessage();

  const togglingId = ref<string | null>(null);
  const deletingId = ref<string | null>(null);

  const updateStatusMutation = useUpdateCategoryStatusMutation();
  const deleteMutation = useDeleteCategoryMutation();

  function handleToggleStatus(row: CategoryItem): void {
    const nextStatus =
      row.status === CATEGORY_STATUS.ACTIVE
        ? CATEGORY_STATUS.DISABLED
        : CATEGORY_STATUS.ACTIVE;
    togglingId.value = row.id;
    updateStatusMutation.mutate(
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
    deleteMutation.mutate(id, {
      onSuccess: () => {
        options.onDeleted?.(id);
        message.success("分类已删除");
      },
      onSettled: () => {
        if (deletingId.value === id) deletingId.value = null;
      },
    });
  }

  return {
    togglingId,
    deletingId,
    handleToggleStatus,
    handleDelete,
  };
}
