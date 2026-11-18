import { createRouter, createWebHistory } from "vue-router";
import { setupRouterGuards } from "./guards";
import { authRoutes } from "@/features/auth/routes";
import { dashboardRoutes } from "@/features/dashboard/routes";
import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import { categoryRoutes } from "@/features/categories/routes";
import { tagRoutes } from "@/features/tags/routes";

declare module "vue-router" {
  interface RouteMeta {
    public?: boolean;
    title?: string;
    activeMenu?: string;
  }
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...authRoutes,
    {
      name: ROUTE_NAMES.FORBIDDEN,
      path: ROUTE_PATHS.FORBIDDEN,
      component: () => import("@/app/views/ForbiddenView.vue"),
      meta: {
        title: "无访问权限",
      },
    },
    {
      name: ROUTE_NAMES.SHELL,
      path: ROUTE_PATHS.HOME,
      component: () => import("@/app/layouts/AdminLayout.vue"),
      children: [
        ...dashboardRoutes,
        ...categoryRoutes,
        ...tagRoutes,
        {
          name: ROUTE_NAMES.NOT_FOUND,
          path: ROUTE_PATHS.NOT_FOUND,
          component: () => import("@/app/views/NotFoundView.vue"),
          meta: {
            title: "页面不存在",
          },
        },
      ],
    },
  ],
});
setupRouterGuards(router);
