import { safeReturnTo } from "./login-search";

// Login is a full-page navigation to the backend (ADR 0002); the backend carries no return URL
// through Google (CR-13, B1 optional item), so the SPA parks the path in sessionStorage before
// leaving and the /auth/callback route takes it back. sessionStorage is per tab, which matches
// a redirect that comes back to the same tab, and it survives the two cross-origin hops.
export const GOOGLE_LOGIN_URL = "/api/v1/auth/oauth2/google";
const KEY = "blocbuy.returnTo";

export function rememberReturnTo(path: string): void {
  try {
    sessionStorage.setItem(KEY, safeReturnTo(path));
  } catch {
    // storage blocked (private mode, quota): the callback falls back to /account
  }
}

export function takeReturnTo(): string {
  try {
    const stored = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
    return safeReturnTo(stored);
  } catch {
    return safeReturnTo(null);
  }
}

export function startGoogleLogin(
  returnTo: string,
  navigate: (url: string) => void = (url) => window.location.assign(url),
): void {
  rememberReturnTo(returnTo);
  navigate(GOOGLE_LOGIN_URL);
}
