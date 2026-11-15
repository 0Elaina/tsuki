import { useAuthStore } from "@/features/auth/stores/auth.store";
import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import type { Router } from "vue-router";
import { resolveSafeRedirect } from "@tsuki/shared/router/safe-redirect";

/** 设置路由守卫 */
export function setupRouterGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore();
    if (!authStore.bootstrapped) await authStore.bootstrap();
    const isPublic = to.matched.some((record) => record.meta.public === true);

    // 未登录拦截至登录页
    if (!isPublic && !authStore.isAuthenticated) {
      const isPlainHome =
        to.name === ROUTE_NAMES.HOME &&
        Object.keys(to.query).length === 0 &&
        !to.hash;
      return isPlainHome
        ? { name: ROUTE_NAMES.LOGIN }
        : { name: ROUTE_NAMES.LOGIN, query: { redirect: to.fullPath } };
    }

    // 已登录不再退回登录页
    if (to.name === ROUTE_NAMES.LOGIN && authStore.isAuthenticated) {
      const redirectPath = resolveSafeRedirect(to.query.redirect, {
        excludePaths: [ROUTE_PATHS.LOGIN],
      });
      return redirectPath ?? { name: ROUTE_NAMES.HOME };
    }
  });

  router.afterEach((to) => {
    const pageTitle = to.meta.title;
    document.title = pageTitle ? `${pageTitle} - Tsuki 管理端` : "Tsuki 管理端";
  });
}
