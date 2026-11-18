import { useMessage } from "naive-ui";
import { computed, ref, shallowRef } from "vue";
import { type TagItem, type UpdateTagPayload } from "../types/tag.types";
import {
  useCreateTagMutation,
  useUpdateTagMutation,
} from "../api/tag.mutations";

export function useTagFormModal() {
  const message = useMessage();
  const modalVisible = ref(false);
  const editingTag = shallowRef<TagItem | null>(null);

  const createMutation = useCreateTagMutation();
  const updateMutation = useUpdateTagMutation();

  const formSubmitting = computed(
    () => createMutation.isPending.value || updateMutation.isPending.value,
  );

  function openCreateModal(): void {
    editingTag.value = null;
    modalVisible.value = true;
  }

  function openEditModal(row: TagItem): void {
    editingTag.value = row;
    modalVisible.value = true;
  }

  function handleModalSubmit(payload: UpdateTagPayload): void {
    if (editingTag.value) {
      updateMutation.mutate(
        {
          id: editingTag.value.id,
          data: payload,
        },
        {
          onSuccess: () => {
            modalVisible.value = false;
            message.success("标签更新成功");
          },
        },
      );
    } else {
      createMutation.mutate(payload, {
        onSuccess: () => {
          modalVisible.value = false;
          message.success("标签创建成功");
        },
      });
    }
  }

  return {
    modalVisible,
    editingTag,
    formSubmitting,
    openCreateModal,
    openEditModal,
    handleModalSubmit,
  };
}
