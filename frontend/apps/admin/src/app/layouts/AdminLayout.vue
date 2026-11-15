<script setup lang="ts">
import { useLogoutMutation } from "@/features/auth/api/auth.mutations";
import { useMeQuery } from "@/features/auth/api/auth.queries";
import { useStorage } from "@vueuse/core";
import { computed } from "vue";
import { useRoute, useRouter, RouterLink, RouterView } from "vue-router";
import {
  adminMenuOptions,
  buildBreadcrumbs,
  resolveActiveMenuKey,
  type BreadcrumbItem,
} from "../navigation/menu";
import {
  NAvatar,
  NBreadcrumb,
  NBreadcrumbItem,
  NButton,
  NDropdown,
  NFlex,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NSkeleton,
  NTag,
  NText,
  type DropdownOption,
} from "naive-ui";
import { renderIcon } from "@/shared/utils/icon";
import {
  ChevronDown,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
} from "lucide-vue-next";
import { ROUTE_NAMES } from "@/shared/constants/routes";
import { palette } from "../theme/tokens";

const SIDER_COLLAPSED_STORAGE_KEY = "tsuki_admin_sider_collapsed";
const USER_MENU_KEYS = {
  LOGOUT: "logout",
} as const;
const route = useRoute();
const router = useRouter();
const meQuery = useMeQuery();
const logoutMutation = useLogoutMutation();
const collapsed = useStorage<boolean>(SIDER_COLLAPSED_STORAGE_KEY, false);

const activeMenuKey = computed(() => resolveActiveMenuKey(route));
const breadCrumbs = computed(() => buildBreadcrumbs(route));
const displayName = computed(
  () =>
    meQuery.data.value?.nickname || meQuery.data.value?.username || "管理员",
);
const userInitial = computed(() => displayName.value.slice(0, 1).toUpperCase());
const userDropdownOptions: DropdownOption[] = [
  {
    key: USER_MENU_KEYS.LOGOUT,
    label: "退出登录",
    icon: renderIcon(LogOut),
  },
];

function handleToggleCollapsed(): void {
  collapsed.value = !collapsed.value;
}

function handleMenuSelect(key: string | number): void {
  // Naive UI 的 key 类型是 string | number，但本项目约定 key 恒为路由 name
  if (typeof key === "string")
    void router.push({
      name: key,
    });
}

function handleBreadcrumbClick(item: BreadcrumbItem, isLast: boolean): void {
  if (!isLast && item.name) {
    void router.push({ name: item.name });
  }
}

function handleUserDropdownSelect(key: string | number): void {
  if (key === USER_MENU_KEYS.LOGOUT) {
    logoutMutation.mutate(undefined, {
      onSettled: () => {
        void router.replace({ name: ROUTE_NAMES.LOGIN });
      },
    });
  }
}
</script>

<template>
  <NLayout
    has-sider
    class="h-screen bg-moon"
    content-class="flex h-full gap-3 p-3"
  >
    <NLayoutSider
      collapse-mode="width"
      :collapsed-width="64"
      :width="228"
      :collapsed="collapsed"
      :native-scrollbar="false"
      class="overflow-hidden rounded-large border border-line bg-surface shadow-soft-sm"
    >
      <!-- 品牌区 -->
      <RouterLink
        :to="{ name: ROUTE_NAMES.HOME }"
        class="flex h-14 items-center border-b border-line px-4 no-underline"
        :class="collapsed ? 'justify-center' : 'gap-2.5'"
      >
        <div
          class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-sm font-semibold text-white"
          :style="{ backgroundColor: palette.sakura }"
        >
          T
        </div>
        <span
          v-if="!collapsed"
          class="truncate text-base font-semibold tracking-tight text-ink"
        >
          Tsuki Admin
        </span>
      </RouterLink>

      <!-- 导航菜单 -->
      <div class="py-1.5">
        <NMenu
          :collapsed="collapsed"
          :collapsed-width="64"
          :collapsed-icon-size="20"
          :options="adminMenuOptions"
          :value="activeMenuKey"
          @update:value="handleMenuSelect"
        />
      </div>
    </NLayoutSider>

    <!-- 主内容区 -->
    <NLayout
      class="min-w-0 flex-1 bg-transparent"
      content-class="flex h-full flex-col gap-3"
    >
      <!-- 顶栏 -->
      <NLayoutHeader
        class="flex h-14 shrink-0 items-center justify-between rounded-large border border-line bg-surface px-5 shadow-soft-sm"
      >
        <!-- 左侧区域 -->
        <NFlex align="center" :size="12" :wrap="false">
          <NButton
            quaternary
            circle
            size="small"
            :aria-label="collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="handleToggleCollapsed"
          >
            <template #icon>
              <NIcon
                :size="18"
                :component="collapsed ? PanelLeftOpen : PanelLeftClose"
              />
            </template>
          </NButton>

          <!-- 面包屑 -->
          <NBreadcrumb>
            <NBreadcrumbItem
              v-for="(item, index) in breadCrumbs"
              :key="String(item.name ?? item.title)"
              :clickable="Boolean(item.name) && index < breadCrumbs.length - 1"
              @click="
                handleBreadcrumbClick(item, index === breadCrumbs.length - 1)
              "
            >
              {{ item.title }}
            </NBreadcrumbItem>
          </NBreadcrumb>
        </NFlex>

        <!-- 右侧账号区 -->
        <div class="flex items-center">
          <NSkeleton v-if="meQuery.isPending.value" text :width="120" round />
          <!-- 正常 -->
          <NDropdown
            v-else-if="meQuery.data.value"
            trigger="click"
            placement="bottom-end"
            :options="userDropdownOptions"
            @select="handleUserDropdownSelect"
          >
            <NButton quaternary :disabled="logoutMutation.isPending.value">
              <NFlex align="center" :size="8" :wrap="false">
                <NAvatar
                  round
                  :size="26"
                  :style="{
                    backgroundColor: palette.sakuraSoft,
                    color: palette.sakura,
                    fontWeight: 600,
                  }"
                >
                  {{ userInitial }}
                </NAvatar>
                <NText class="text-sm font-medium">{{ displayName }}</NText>
                <NTag size="small" :bordered="false" type="info">
                  {{ meQuery.data.value.role }}
                </NTag>
                <NIcon :size="14" :component="ChevronDown" />
              </NFlex>
            </NButton>
          </NDropdown>

          <!-- 降级态 -->
          <NDropdown
            v-else
            trigger="click"
            placement="bottom-end"
            :options="userDropdownOptions"
            @select="handleUserDropdownSelect"
          >
            <NButton quaternary :disabled="logoutMutation.isPending.value">
              账号操作
              <template #icon>
                <NIcon :size="14" :component="ChevronDown" />
              </template>
            </NButton>
          </NDropdown>
        </div>
      </NLayoutHeader>

      <!-- 内容区 -->
      <NLayoutContent
        class="min-h-0 flex-1"
        :native-scrollbar="false"
        content-class="pb-2"
      >
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>
