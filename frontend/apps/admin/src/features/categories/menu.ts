import { ROUTE_NAMES } from "@/shared/constants/routes";
import { renderIcon } from "@/shared/utils/icon";
import { FolderTree } from "lucide-vue-next";
import type { MenuOption } from "naive-ui";

export const categoryMenus: MenuOption[] = [
  {
    key: ROUTE_NAMES.CATEGORIES,
    label: "分类管理",
    icon: renderIcon(FolderTree),
  },
];
