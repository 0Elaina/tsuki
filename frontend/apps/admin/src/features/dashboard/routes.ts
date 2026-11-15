import { ROUTE_NAMES } from "@/shared/constants/routes";
import type { RouteRecordRaw } from "vue-router";

export const dashboardRoutes: RouteRecordRaw[] = [
  {
    name: ROUTE_NAMES.HOME,
    path: "",
    component: () => import("./views/DashboardView.vue"),
    meta: {
      title: "工作台",
    },
  },
];
