import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import type { RouteRecordRaw } from "vue-router";

export const dashboardRoutes: RouteRecordRaw[] = [
  {
    name: ROUTE_NAMES.HOME,
    path: ROUTE_PATHS.HOME,
    component: () => import("./views/DashboardView.vue"),
    meta: {
      title: "工作台",
    },
  },
];
