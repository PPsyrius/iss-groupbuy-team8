// The striped tile every image sits on (design screens §4.1 "Card anatomy"; admin plan Global
// Constraints: "Every image has the striped fallback tile"). The image covers the stripes when it
// loads; while it loads, or when its host is gone (an empty `alt` renders no broken icon), the
// stripes show. Decorative: the surrounding text names the product. Size it through `className`.
export function ImageFallback({
  src,
  className = "",
}: {
  src?: string | null | undefined;
  className?: string;
}) {
  return (
    <div
      aria-hidden="true"
      data-image-fallback=""
      className={`bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_10px,var(--surface-tertiary)_10px_20px)] ${className}`}
    >
      {src ? <img alt="" className="h-full w-full object-cover" loading="lazy" src={src} /> : null}
    </div>
  );
}
