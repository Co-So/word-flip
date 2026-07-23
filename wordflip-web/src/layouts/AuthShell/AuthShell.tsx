import type { ReactNode } from "react";

export interface AuthShellProps { children: ReactNode; }

export function AuthShell({ children }: AuthShellProps) {
  return <main className="wf-auth-shell"><section>{children}</section></main>;
}
