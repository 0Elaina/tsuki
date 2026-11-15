import { createRouter, createWebHistory } from "vue-router";

import EmptyPage from "@/shared/ui/EmptyPage.vue";
import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import { setupRouterGuards } from "./guards";

declare module "vue-router" {
  interface RouteMeta {
    public?: boolean;
    title?: string;
  }
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      name: ROUTE_NAMES.HOME,
      path: ROUTE_PATHS.HOME,
      component: EmptyPage,
      meta: {
        title: "工作台",
      },
    },
  ],
});
setupRouterGuards(router);
