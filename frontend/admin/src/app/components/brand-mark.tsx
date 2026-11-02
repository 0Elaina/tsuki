interface BrandMarkProps {
  size?: number
  className?: string
}

// 项目标识：渐变圆底 + 弯月与两颗星。内联 SVG，随尺寸缩放，无外部素材依赖。
export function BrandMark({ size = 40, className }: BrandMarkProps) {
  return (
    <span
      className={`inline-flex shrink-0 items-center justify-center rounded-full bg-linear-to-br from-primary to-accent shadow-[0_4px_14px_rgba(124,58,237,0.28)] ${className ?? ''}`}
      style={{ width: size, height: size }}
      aria-hidden
    >
      <svg
        width={size * 0.56}
        height={size * 0.56}
        viewBox="0 0 24 24"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
      >
        {/* 弯月：外弧减去偏移内圆形成月牙 */}
        <path
          d="M15.4 3.2a9 9 0 1 0 5.4 12.6A7.2 7.2 0 0 1 15.4 3.2Z"
          fill="#FFFFFF"
          fillOpacity="0.96"
        />
        {/* 两颗星，大小错落 */}
        <circle cx="18.6" cy="5.4" r="1.35" fill="#FFFFFF" fillOpacity="0.9" />
        <circle cx="20.4" cy="10.2" r="0.85" fill="#FFFFFF" fillOpacity="0.75" />
      </svg>
    </span>
  )
}
