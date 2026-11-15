import { dashboardMenus } from "@/features/dashboard/menu";
import type { MenuOption } from "naive-ui";
import type {
  RouteLocationNormalizedLoaded,
  RouteRecordName,
} from "vue-router";

export interface BreadcrumbItem {
  title: string;
  name?: RouteRecordName;
}

export const adminMenuOptions: MenuOption[] = [...dashboardMenus];

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
