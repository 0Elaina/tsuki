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

  const createTagMutation = useCreateTagMutation();
  const updateTagMutation = useUpdateTagMutation();

  const formSubmitting = computed(
    () =>
      createTagMutation.isPending.value || updateTagMutation.isPending.value,
  );

  function openCreateTagModal(): void {
    editingTag.value = null;
    modalVisible.value = true;
  }

  function openEditTagModal(row: TagItem): void {
    editingTag.value = row;
    modalVisible.value = true;
  }

  function handleModalSubmit(payload: UpdateTagPayload): void {
    if (editingTag.value) {
      updateTagMutation.mutate(
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
      createTagMutation.mutate(payload, {
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
    openCreateTagModal,
    openEditTagModal,
    handleModalSubmit,
  };
}
