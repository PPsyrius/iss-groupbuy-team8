import isologoDark from "@/assets/brand/blocbuy-isologo-dark.svg";
import isologo from "@/assets/brand/blocbuy-isologo.svg";
import logo from "@/assets/brand/blocbuy-logo.svg";

// The BlocBuy marks (CR-7): the horizontal logo (isologo + wordmark + tagline) and the bag-only
// isologo, with a `dark` tone for `background-inverse` (the admin rail; the storefront is light
// only). Rendered as <img>, never inlined: every file reuses `clipPath id="bag"`, which would
// collide in one document. The wordmark text is outlined, so no font loads inside the image.
// The intrinsic size reserves the box before decode; callers set the height and `w-auto`.
// Decorative uses (beside a heading that already names the screen) pass `alt=""`.
type MarkProps = Readonly<{ alt?: string; className?: string }>;

export function BrandLogo({ alt = "BlocBuy", className }: MarkProps) {
  return <img src={logo} alt={alt} width={560} height={180} className={className} />;
}

export function BrandIsologo({
  alt = "BlocBuy",
  className,
  tone,
}: MarkProps & Readonly<{ tone?: "dark" }>) {
  const src = tone === "dark" ? isologoDark : isologo;
  return <img src={src} alt={alt} width={140} height={172} className={className} />;
}
