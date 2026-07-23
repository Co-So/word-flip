import type { ReactNode } from "react";
import { Button } from "@/components/Button/Button";
import { EmptyState } from "@/components/EmptyState/EmptyState";

export interface AsyncStateProps { status: "loading" | "empty" | "error" | "ready"; error?: string; onRetry?: () => void; children: ReactNode; }

export function AsyncState({ status, error, onRetry, children }: AsyncStateProps) {
  if (status === "loading") return <div aria-live="polite" role="status">正在整理学习资料…</div>;
  if (status === "empty") return <EmptyState title="这里还没有内容" description="完成下一步操作后，内容会显示在这里。" />;
  if (status === "error") return <EmptyState title="暂时无法加载" description={error ?? "请检查网络后重试。"} action={onRetry && <Button variant="ghost" onClick={onRetry}>重新尝试</Button>} />;
  return <>{children}</>;
}
