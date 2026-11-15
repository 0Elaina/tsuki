<script setup lang="ts">
import { useRoute, useRouter } from "vue-router";
import { reactive, shallowRef } from "vue";
import {
  NButton,
  NCard,
  NFlex,
  NForm,
  NFormItem,
  NH2,
  NInput,
  NText,
  type FormInst,
  type FormRules,
} from "naive-ui";
import { AUTH_LIMITS } from "@tsuki/shared/constants/auth";
import { resolveSafeRedirect } from "@tsuki/shared/router/safe-redirect";
import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import { useLoginMutation } from "../api/auth.mutations";
import { validateForm } from "@/shared/utils/form";

const route = useRoute();
const router = useRouter();
const loginMutation = useLoginMutation();
const formRef = shallowRef<FormInst | null>(null);
const formModel = reactive({
  username: "",
  password: "",
});

const rules: FormRules = {
  username: [
    {
      required: true,
      whitespace: true,
      message: "请输入用户名",
      trigger: ["blur", "input"],
    },
    {
      max: AUTH_LIMITS.USERNAME_MAX_LENGTH,
      message: `用户名不能超过 ${AUTH_LIMITS.USERNAME_MAX_LENGTH} 个字符`,
      trigger: ["blur", "input"],
    },
  ],
  password: [
    {
      required: true,
      whitespace: true,
      message: "请输入密码",
      trigger: ["blur", "input"],
    },
    {
      max: AUTH_LIMITS.PASSWORD_MAX_LENGTH,
      message: `密码不能超过 ${AUTH_LIMITS.PASSWORD_MAX_LENGTH} 个字符`,
      trigger: ["blur", "input"],
    },
  ],
};

async function handleSubmit(): Promise<void> {
  if (!(await validateForm(formRef.value))) return;
  loginMutation.mutate(
    {
      username: formModel.username.trim(),
      password: formModel.password,
    },
    {
      onSuccess: () => {
        // 安全跳转
        const redirectPath = resolveSafeRedirect(route.query.redirect, {
          excludePaths: [ROUTE_PATHS.LOGIN],
        });

        void router.replace(redirectPath ?? { name: ROUTE_NAMES.HOME });
      },
    },
  );
}
</script>

<template>
  <main class="flex min-h-screen items-center justify-center bg-moon px-4 py-8">
    <NCard class="w-full max-w-100">
      <!-- 标题 -->
      <NFlex vertical :size="4" class="mb-6">
        <NH2>Tsuki 管理端</NH2>
        <NText depth="3">请使用管理员账号登录</NText>
      </NFlex>
      <!-- 表单 -->
      <NForm
        ref="formRef"
        :model="formModel"
        :rules="rules"
        label-placement="top"
        size="large"
        @submit.prevent="handleSubmit"
      >
        <NFormItem label="用户名" path="username">
          <NInput
            v-model:value="formModel.username"
            placeholder="请输入用户名"
            :maxlength="AUTH_LIMITS.USERNAME_MAX_LENGTH"
            :input-props="{ autocomplete: 'username' }"
          />
        </NFormItem>
        <NFormItem label="密码" path="password">
          <NInput
            v-model:value="formModel.password"
            type="password"
            show-password-on="click"
            placeholder="请输入密码"
            :maxlength="AUTH_LIMITS.PASSWORD_MAX_LENGTH"
            :input-props="{ autocomplete: 'current-password' }"
          />
        </NFormItem>

        <NButton
          type="primary"
          attr-type="submit"
          block
          size="large"
          class="mt-2"
          :loading="loginMutation.isPending.value"
          >登录</NButton
        >
      </NForm>
    </NCard>
  </main>
</template>
