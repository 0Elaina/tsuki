import { ROUTE_NAMES, ROUTE_PATHS } from "@/shared/constants/routes";
import type { RouteRecordRaw } from "vue-router";

export const authRoutes: RouteRecordRaw[] = [
  {
    name: ROUTE_NAMES.LOGIN,
    path: ROUTE_PATHS.LOGIN,
    component: () => import("./views/LoginView.vue"),
    meta: {
      public: true,
      title: "登录",
    },
  },
];
