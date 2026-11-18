import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import type { RouteRecordRaw } from "vue-router";

export const tagRoutes: RouteRecordRaw[] = [
  {
    name: ROUTE_NAMES.TAGS,
    path: ROUTE_PATHS.TAGS,
    component: () => import("./views/TagListView.vue"),
    meta: {
      title: "标签管理",
    },
  },
];
