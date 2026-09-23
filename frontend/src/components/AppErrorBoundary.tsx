import { Component, type ErrorInfo, type ReactNode } from "react";

import { GlobalErrorCard } from "./ErrorCard";

interface State {
  failed: boolean;
}

// Last-resort boundary around `RouterProvider` (main.tsx): the router's own error components
// cannot catch a throw in the router itself or in the root layout, which used to leave a blank
// page. React 19 still has no hook form of `componentDidCatch`, hence the class. "Try again"
// reloads: after a crash outside any route there is no narrower state worth keeping.
export class AppErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("BlocBuy: unrecoverable render error", error, info.componentStack);
  }

  render() {
    if (this.state.failed) {
      return <GlobalErrorCard onRetry={() => window.location.reload()} />;
    }
    return this.props.children;
  }
}
