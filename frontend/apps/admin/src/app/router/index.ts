import { createRouter, createWebHistory } from "vue-router";
import { setupRouterGuards } from "./guards";
import { authRoutes } from "@/features/auth/routes";
import { dashboardRoutes } from "@/features/dashboard/routes";

declare module "vue-router" {
  interface RouteMeta {
    public?: boolean;
    title?: string;
  }
}

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [...authRoutes, ...dashboardRoutes],
});
setupRouterGuards(router);
