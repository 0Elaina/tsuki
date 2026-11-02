import { theme } from 'antd'
import type { ThemeConfig } from 'antd'

// 系统字体栈：中文按平台取本机自带字体，不下载字体文件。
const FONT_FAMILY =
  "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans CJK SC', sans-serif, 'Apple Color Emoji', 'Segoe UI Emoji'"

// 主题值在此配置，业务样式通过 Ant Design CSS 变量复用派生结果。
export const appTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  // 与 styles/theme.css 中的变量映射保持一致。
  cssVar: { prefix: 'ant' },
  token: {
    // 薰衣草紫：白字压主色对比 5.70，过 WCAG AA。
    colorPrimary: '#7C3AED',
    // colorInfo 不随 colorPrimary 派生，显式对齐避免出现第二种蓝。
    colorInfo: '#7C3AED',
    // 内容区底色，与 styles 中的光晕叠加构成轻微氛围。
    colorBgLayout: '#F7F4FF',
    fontFamily: FONT_FAMILY,
    // 行高 1.6 比 antd 默认的 1.57 更接近苹果的排版比例。
    lineHeight: 1.6,
    // 圆角：基础 10，显式覆盖派生值以拿到 8 / 16 / 20。
    borderRadius: 10,
    borderRadiusSM: 8,
    borderRadiusLG: 16,
    borderRadiusXS: 4,
    borderRadiusOuter: 20,
    // 控件高度 36 比默认 32 更接近苹果的触控尺度。
    controlHeight: 36,
    // 中性色带一点紫调，避免灰字与紫底撞出脏色。
    colorTextBase: '#1F1B2E',
    // 多层柔和阴影替代 antd 默认的硬投影。
    boxShadow: '0 1px 3px rgba(31, 27, 46, 0.04), 0 8px 24px rgba(31, 27, 46, 0.06)',
    boxShadowSecondary: '0 2px 6px rgba(31, 27, 46, 0.05), 0 12px 32px rgba(31, 27, 46, 0.08)',
  },
  components: {
    Button: {
      fontWeight: 500,
      primaryShadow: '0 4px 14px rgba(124, 58, 237, 0.24)',
      defaultShadow: 'none',
      dangerShadow: 'none',
    },
    Menu: {
      itemBorderRadius: 10,
      itemHeight: 42,
      itemMarginInline: 8,
      itemSelectedBg: '#F3E8FF',
      itemSelectedColor: '#7C3AED',
      itemActiveBg: '#FAF5FF',
      itemColor: '#5B5470',
      activeBarWidth: 3,
      activeBarHeight: 18,
      activeBarBorderWidth: 0,
    },
    Card: {
      // 卡片默认带边框，苹果风格改用阴影分层，边框在组件层关闭。
      headerBg: 'transparent',
      bodyPadding: 24,
    },
  },
}
