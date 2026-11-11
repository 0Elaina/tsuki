import type { GlobalThemeOverrides } from "naive-ui";

import { fontFamily, fontFamilyMono, palette, radius, shadow } from "./tokens";

/**
 * 管理端 Naive UI 主题覆盖。
 *
 * 取色与尺度全部来自 `./tokens`，此文件只负责把这些值映射到 Naive UI
 * 实际暴露的变量名上。语义分工：
 * - `ink` 当黑用：主按钮填充、强调标题。不做大面积色块。
 * - `sakura` 只点缀：选中态、焦点环、徽标，不用于主按钮。
 * - 圆角与阴影统一从 tokens 取，避免各组件视觉节奏不一致。
 */
export const themeOverrides: GlobalThemeOverrides = {
  common: {
    // 主色：Naive UI 的 primary 是全站交互色的源头（按钮、选中、链接、焦点）。
    // 这里刻意放在墨蓝而不是抹粉，粉色留给下面的局部强调。
    primaryColor: palette.ink,
    primaryColorHover: palette.inkHover,
    primaryColorPressed: palette.inkPressed,
    primaryColorSuppl: palette.inkHover,

    infoColor: palette.inkSoft,
    infoColorHover: palette.inkSoft,
    infoColorPressed: palette.inkHover,
    successColor: palette.success,
    successColorHover: palette.successHover,
    successColorPressed: palette.successPressed,
    warningColor: palette.warning,
    warningColorHover: palette.warningHover,
    warningColorPressed: palette.warningPressed,
    errorColor: palette.danger,
    errorColorHover: palette.dangerHover,
    errorColorPressed: palette.dangerPressed,

    textColorBase: palette.text,
    textColor1: palette.text,
    textColor2: palette.textMuted,
    textColor3: palette.textFaint,
    textColorDisabled: palette.textFaint,
    placeholderColor: palette.textFaint,

    borderColor: palette.border,
    dividerColor: palette.border,

    bodyColor: palette.moon,
    cardColor: palette.surface,
    modalColor: palette.surfaceRaised,
    popoverColor: palette.surface,
    tableColor: palette.surface,
    tableHeaderColor: palette.moon,
    inputColor: palette.surface,
    hoverColor: palette.moon,

    borderRadius: radius.base,
    borderRadiusSmall: radius.small,

    boxShadow1: shadow.sm,
    boxShadow2: shadow.md,
    boxShadow3: shadow.lg,

    fontFamily,
    fontFamilyMono,
    fontSize: "14px",
    fontSizeSmall: "13px",
    fontSizeMedium: "14px",
    fontSizeLarge: "15px",
    lineHeight: "1.6",

    // 控件高度略放宽，苹果式的从容感主要来自这里
    heightSmall: "30px",
    heightMedium: "34px",
    heightLarge: "40px",

    cubicBezierEaseInOut: "cubic-bezier(0.4, 0, 0.2, 1)",
    cubicBezierEaseOut: "cubic-bezier(0.16, 1, 0.3, 1)",
    cubicBezierEaseIn: "cubic-bezier(0.4, 0, 1, 1)",
  },

  Button: {
    borderRadiusTiny: radius.small,
    borderRadiusSmall: radius.small,
    borderRadiusMedium: radius.base,
    borderRadiusLarge: radius.base,
    fontWeight: "500",
    fontWeightStrong: "600",
    paddingMedium: "0 16px",
    paddingLarge: "0 20px",
  },

  Input: {
    borderRadius: radius.base,
    heightMedium: "34px",
    heightLarge: "40px",
  },

  Select: {
    InternalSelection: {
      borderRadius: radius.base,
      heightMedium: "34px",
      heightLarge: "40px",
    },
    InternalSelectMenu: {
      borderRadius: radius.large,
      optionHeightMedium: "34px",
    },
  },

  Card: {
    borderRadius: radius.large,
    // 默认值就是 boxShadow1，改成 token 里的淡阴影，避免卡片浮得太重
    boxShadow: shadow.sm,
    paddingMedium: "20px 24px",
    titleFontWeight: "600",
    titleFontSizeMedium: "15px",
  },

  DataTable: {
    borderRadius: radius.large,
    thColor: palette.moon,
    thTextColor: palette.textMuted,
    tdColor: palette.surface,
    tdTextColor: palette.text,
    tdColorHover: palette.moon,
    borderColor: palette.border,
    thFontWeight: "500",
    thPaddingMedium: "10px 16px",
    tdPaddingMedium: "12px 16px",
  },

  Menu: {
    // 默认 42px，配合放到 14px 圆角后会显得密，这里抬到 38px 更透气
    itemHeight: "38px",
    borderRadius: radius.base,
    itemTextColor: palette.textMuted,
    itemTextColorHover: palette.text,
    itemTextColorActive: palette.ink,
    itemIconColor: palette.textFaint,
    itemIconColorHover: palette.textMuted,
    itemIconColorActive: palette.ink,
    itemColorHover: palette.moon,
    // 选中底色用樱粉极浅底，这是全站唯一大面积出现粉色的地方
    itemColorActive: palette.sakuraSoft,
    itemColorActiveHover: palette.sakuraSoft,
    groupTextColor: palette.textFaint,
  },

  Modal: {
    borderRadius: radius.xl,
  },

  Tag: {
    borderRadius: radius.small,
    heightMedium: "24px",
  },
};
