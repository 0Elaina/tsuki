import { createRouter, createWebHistory } from "vue-router";

import EmptyPage from "@/shared/ui/EmptyPage.vue";

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [{ path: "/", component: EmptyPage }],
});
