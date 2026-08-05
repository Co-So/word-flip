import { useEffect, useRef, type KeyboardEvent, type RefObject } from "react";
import { Button } from "@/components/Button/Button";
import styles from "./settings.module.css";

interface SignOutDialogProps {
  error: string | null;
  onCancel: () => void;
  onConfirm: () => void;
  returnFocusRef: RefObject<HTMLElement>;
  signingOut: boolean;
}

/** 退出当前浏览器会话前二次确认，并在提交期间阻止重复操作。 */
export function SignOutDialog({ error, onCancel, onConfirm, returnFocusRef, signingOut }: SignOutDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const returnFocusTarget = returnFocusRef.current;
    cancelRef.current?.focus();
    return () => { returnFocusTarget?.focus(); };
  }, [returnFocusRef]);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (signingOut && (event.key === "Escape" || event.key === "Tab")) {
      event.preventDefault();
      return;
    }
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
      aria-labelledby="sign-out-title"
      aria-modal="true"
      className={styles.dialog}
      onKeyDown={handleKeyDown}
      role="dialog"
    >
      <p className={styles.eyebrow}>ACCOUNT SIGN OUT</p>
      <h2 id="sign-out-title">确认退出登录</h2>
      <p>当前浏览器会退出 WordFlip，并返回登录页。</p>
      {error ? <p className={styles.signOutError} role="alert">{error}</p> : null}
      <div className={styles.dialogActions}>
        <Button disabled={signingOut} onClick={onCancel} ref={cancelRef} variant="secondary">取消</Button>
        <Button className={styles.dangerButton} disabled={signingOut} onClick={onConfirm} ref={confirmRef}>确认退出</Button>
      </div>
    </div>
  </div>;
}
