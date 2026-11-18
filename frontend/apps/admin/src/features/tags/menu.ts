import { ROUTE_NAMES } from "@/shared/constants/routes";
import { renderIcon } from "@/shared/utils/icon";
import { Tag } from "lucide-vue-next";
import type { MenuOption } from "naive-ui";

export const tagMenus: MenuOption[] = [
  {
    key: ROUTE_NAMES.TAGS,
    label: "标签管理",
    icon: renderIcon(Tag),
  },
];
