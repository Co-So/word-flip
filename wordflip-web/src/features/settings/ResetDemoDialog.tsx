import { useEffect, useRef, type KeyboardEvent } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./settings.module.css";

interface ResetDemoDialogProps {
  onCancel: () => void;
  onConfirm: () => void;
}

export function ResetDemoDialog({ onCancel, onConfirm }: ResetDemoDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cancelRef.current?.focus();
  }, []);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      onCancel();
      return;
    }
    if (event.key !== "Tab") return;
    const first = cancelRef.current;
    const last = confirmRef.current;
    if (!first || !last) return;
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return <div className={styles.backdrop}>
    <div
      aria-labelledby="reset-demo-title"
      aria-modal="true"
      className={styles.dialog}
      onKeyDown={handleKeyDown}
      role="dialog"
    >
      <p className={styles.eyebrow}>DEMO RESET</p>
      <h2 id="reset-demo-title">确认重置演示数据</h2>
      <p>当前浏览器中的学习、媒体和设置演示变更会恢复为固定种子。</p>
      <div className={styles.dialogActions}>
        <Button onClick={onCancel} ref={cancelRef} variant="secondary">取消</Button>
        <Button onClick={onConfirm} ref={confirmRef}>确认重置</Button>
      </div>
    </div>
  </div>;
}
