import { useMessage } from "naive-ui";
import { computed, ref, shallowRef } from "vue";
import {
  useCreateCategoryMutation,
  useUpdateCategoryMutation,
} from "../api/category.mutations";
import type {
  CategoryItem,
  UpdateCategoryPayload,
} from "../types/category.types";

/** 分类新建与编辑弹窗状态编排 */
export function useCategoryFormModal() {
  const message = useMessage();

  const modalVisible = ref(false);
  const editingCategory = shallowRef<CategoryItem | null>(null);

  const createMutation = useCreateCategoryMutation();
  const updateMutation = useUpdateCategoryMutation();

  const formSubmitting = computed(
    () => createMutation.isPending.value || updateMutation.isPending.value,
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
      updateMutation.mutate(
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
      return;
    }

    createMutation.mutate(payload, {
      onSuccess: () => {
        modalVisible.value = false;
        message.success("分类创建成功");
      },
    });
  }

  return {
    modalVisible,
    editingCategory,
    formSubmitting,
    openCreateModal,
    openEditModal,
    handleModalSubmit,
  };
}
