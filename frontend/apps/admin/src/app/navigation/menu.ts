import { categoryMenus } from "@/features/categories/menu";
import { dashboardMenus } from "@/features/dashboard/menu";
import { tagMenus } from "@/features/tags/menu";
import { ROUTE_NAMES } from "@/shared/constants/routes";
import { renderIcon } from "@/shared/utils/icon";
import { Tags } from "lucide-vue-next";
import type { MenuOption } from "naive-ui";
import type {
  RouteLocationNormalizedLoaded,
  RouteRecordName,
} from "vue-router";

export interface BreadcrumbItem {
  title: string;
  name?: RouteRecordName;
}

export const adminMenuOptions: MenuOption[] = [
  ...dashboardMenus,
  {
    key: ROUTE_NAMES.TAXONOMY_GROUP,
    label: "分类与标签",
    icon: renderIcon(Tags),
    children: [...categoryMenus, ...tagMenus],
  },
];

export function resolveActiveMenuKey(
  route: RouteLocationNormalizedLoaded,
): string | null {
  if (route.meta.activeMenu) return route.meta.activeMenu;
  return typeof route.name === "string" ? route.name : null;
}

export function buildBreadcrumbs(
  route: RouteLocationNormalizedLoaded,
): BreadcrumbItem[] {
  return route.matched.flatMap((record): BreadcrumbItem[] => {
    const title = record.meta?.title;
    if (typeof title !== "string" || title.length === 0) return [];
    return [
      {
        title,
        name: record.name,
      },
    ];
  });
}
