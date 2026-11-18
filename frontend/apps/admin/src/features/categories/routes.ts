import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import type { RouteRecordRaw } from "vue-router";

export const categoryRoutes: RouteRecordRaw[] = [
  {
    name: ROUTE_NAMES.CATEGORIES,
    path: ROUTE_PATHS.CATEGORIES,
    component: () => import("./views/CategoryListView.vue"),
    meta: {
      title: "分类管理",
    },
  },
];
