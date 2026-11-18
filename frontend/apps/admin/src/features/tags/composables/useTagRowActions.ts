import { useMessage } from "naive-ui";
import { ref } from "vue";
import {
  useDeleteTagMutation,
  useUpdateTagStatusMutation,
} from "../api/tag.mutations";
import { TAG_STATUS, type TagItem } from "../types/tag.types";

export interface UseTagRowActionsOptions {
  onDeleted?: (id: string) => void;
}

export function useTagRowActions(options: UseTagRowActionsOptions = {}) {
  const message = useMessage();
  const togglingId = ref<string | null>(null);
  const deletingId = ref<string | null>(null);

  const updateTagStatusMutation = useUpdateTagStatusMutation();
  const deleteTagMutation = useDeleteTagMutation();

  function handleToggleStatus(row: TagItem): void {
    const nextStatus =
      row.status === TAG_STATUS.ACTIVE
        ? TAG_STATUS.DISABLED
        : TAG_STATUS.ACTIVE;
    togglingId.value = row.id;

    updateTagStatusMutation.mutate(
      {
        id: row.id,
        status: nextStatus,
      },
      {
        onSuccess: () => {
          message.success(
            nextStatus === TAG_STATUS.ACTIVE ? "标签已启用" : "标签已停用",
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
    deleteTagMutation.mutate(id, {
      onSuccess: () => {
        options.onDeleted?.(id);
        message.success("标签已删除");
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
