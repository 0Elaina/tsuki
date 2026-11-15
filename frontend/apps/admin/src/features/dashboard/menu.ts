import { ROUTE_NAMES } from "@/shared/constants/routes";
import { renderIcon } from "@/shared/utils/icon";
import type { MenuOption } from "naive-ui";
import { LayoutDashboard } from "lucide-vue-next";

export const dashboardMenus: MenuOption[] = [
  {
    key: ROUTE_NAMES.HOME,
    label: "工作台",
    icon: renderIcon(LayoutDashboard),
  },
];
