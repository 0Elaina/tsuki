<script setup lang="ts">
import { computed, reactive, shallowRef, watch } from "vue";
import {
  TAG_LIMITS,
  type TagItem,
  type UpdateTagPayload,
} from "../types/tag.types";
import {
  NButton,
  NFlex,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  type FormInst,
  type FormRules,
} from "naive-ui";
import { validateForm } from "@/shared/utils/form";
import { INT32_MAX } from "@tsuki/shared/constants/number";

const show = defineModel<boolean>("show", {
  required: true,
});
const props = withDefaults(
  defineProps<{
    tag: TagItem | null;
    submitting?: boolean;
  }>(),
  {
    submitting: false,
  },
);
const emits = defineEmits<{
  submit: [payload: UpdateTagPayload];
}>();

const isEdit = computed(() => props.tag !== null);
const formRef = shallowRef<FormInst | null>(null);
const formModel = reactive<{
  name: string;
  sortOrder: number | null;
}>({
  name: "",
  sortOrder: 0,
});

const rules: FormRules = {
  name: [
    {
      required: true,
      whitespace: true,
      message: "请输入标签名称",
      trigger: ["blur", "input"],
    },
    {
      max: TAG_LIMITS.NAME_MAX_LENGTH,
      message: `名称长度不能超过 ${TAG_LIMITS.NAME_MAX_LENGTH} 个字符`,
      trigger: ["blur", "input"],
    },
  ],
  sortOrder: [
    {
      type: "number",
      required: true,
      message: "请输入排序值",
      trigger: ["blur", "change"],
    },
  ],
};

watch(
  () => show.value,
  (visible) => {
    if (visible) {
      if (props.tag) {
        formModel.name = props.tag.name;
        formModel.sortOrder = props.tag.sortOrder;
      } else {
        formModel.name = "";
        formModel.sortOrder = 0;
      }
    }
    formRef.value?.restoreValidation();
  },
);

async function handleSubmit(): Promise<void> {
  if (!(await validateForm(formRef.value))) return;
  emits("submit", {
    name: formModel.name.trim(),
    sortOrder: formModel.sortOrder ?? 0,
  });
}
</script>

<template>
  <NModal
    v-model:show="show"
    preset="card"
    :title="isEdit ? '编辑标签' : '新建标签'"
    class="w-full max-w-110"
    :mask-closable="!submitting"
  >
    <NForm
      ref="formRef"
      :model="formModel"
      :rules="rules"
      label-placement="top"
      @submit.prevent="handleSubmit"
    >
      <NFormItem label="标签名称" path="name">
        <NInput
          v-model:value="formModel.name"
          placeholder="请输入标签名称"
          show-count
          :maxlength="TAG_LIMITS.NAME_MAX_LENGTH"
        />
      </NFormItem>
      <NFormItem label="排序值" path="sortOrder">
        <NInputNumber
          v-model:value="formModel.sortOrder"
          :min="TAG_LIMITS.SORT_ORDER_MIN"
          :max="INT32_MAX"
          :precision="0"
          class="w-full"
        />
      </NFormItem>
      <NFlex justify="end" :size="8">
        <NButton :disabled="submitting" @click="show = false">取消</NButton>
        <NButton type="primary" attr-type="submit" :loading="submitting">
          {{ isEdit ? "保存" : "创建" }}
        </NButton>
      </NFlex>
    </NForm>
  </NModal>
</template>
