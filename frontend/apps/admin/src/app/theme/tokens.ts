/**
 * 管理端设计 token 原始值。
 *
 * 配色方向：月白底 + 墨蓝当黑 + 樱粉点缀。
 * 墨蓝只承担「黑色」的职责（主按钮填充、标题文字），不做大面积色块，
 * 避免落入传统后台「主色刷满全屏」的观感。
 */

export const palette = {
  /** 页面底色，极浅的冷月白 */
  moon: "#F7F8FB",
  /** 卡片 / 浮层表面 */
  surface: "#FFFFFF",
  /** 最高层级表面，用于弹窗 */
  surfaceRaised: "#FFFFFF",
  /** 墨蓝，全站唯一的「深色」，当黑用 */
  ink: "#1F2B45",
  /** 墨蓝的交互态 */
  inkHover: "#2B3A5C",
  inkPressed: "#162034",
  /** 浅一档的墨蓝，用于次级强调与信息态 */
  inkSoft: "#4A5B7E",
  /** 正文与标题 */
  text: "#232A3B",
  /** 次级文字 */
  textMuted: "#6B7285",
  /** 弱化文字、占位符 */
  textFaint: "#9AA0B4",
  /** 分割线与描边 */
  border: "#E8EAF2",
  /** 描边交互态 */
  borderHover: "#D5D9E8",
  /** 樱粉，只用于选中态、焦点环、徽标 */
  sakura: "#F2789F",
  sakuraHover: "#F58FB1",
  sakuraPressed: "#DF5F89",
  /** 樱粉的极浅底，用于选中项背景 */
  sakuraSoft: "#FDEDF3",
  /** 危险态，与樱粉拉开区分 */
  danger: "#E5484D",
  dangerHover: "#EC6A6E",
  dangerPressed: "#C93A3F",
  /** 成功态 */
  success: "#2A9D6F",
  successHover: "#37B580",
  successPressed: "#20805A",
  /** 警告态 */
  warning: "#D98324",
  warningHover: "#E79A3F",
  warningPressed: "#B96D19",
} as const;

/** 圆角尺度，整体比默认更圆润，但不做胶囊化 */
export const radius = {
  small: "8px",
  base: "10px",
  large: "14px",
  xl: "18px",
} as const;

/** 字体栈，全站统一，不引入网络字体 */
export const fontFamily =
  "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Hiragino Sans GB', " +
  "'Microsoft YaHei', 'Helvetica Neue', Inter, system-ui, sans-serif";

export const fontFamilyMono =
  "'SF Mono', 'JetBrains Mono', Menlo, Consolas, 'Liberation Mono', monospace";

/** 阴影，刻意做淡：悬浮感靠层次，不靠重投影 */
export const shadow = {
  sm: "0 1px 2px rgba(31, 43, 69, 0.04)",
  md: "0 4px 12px rgba(31, 43, 69, 0.06)",
  lg: "0 12px 32px rgba(31, 43, 69, 0.08)",
} as const;
