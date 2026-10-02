import { Button } from "@heroui/react";

import { startGoogleLogin } from "./return-to";

// The artboards' single sign-in control (`Button variant="outline" size="lg"`, 20px "G",
// "Continue with Google"): Google is the only provider, so there is no choice architecture. The
// mark is Google's own "G" (CR-16, FB-13): `public/google-g.svg` is the Light icon-only button
// from Google's Sign in with Google asset pack, cropped to the G (circle removed; geometry and
// colours unchanged; never redraw it). Google's rules keep it at 20px with 10px before the label.
// It is served same-origin because the CSP's `img-src` has no `data:`, and it is decorative: the
// label already names Google.
export function GoogleSignInButton({
  returnTo,
  label = "Continue with Google",
  className = "",
}: Readonly<{
  returnTo: string;
  label?: string;
  className?: string;
}>) {
  return (
    <Button
      variant="outline"
      size="lg"
      className={"min-h-12 w-full gap-2.5 text-base font-semibold " + className}
      onPress={() => startGoogleLogin(returnTo)}
    >
      <img src="/google-g.svg" alt="" width={20} height={20} className="size-5 shrink-0" />
      {label}
    </Button>
  );
}
