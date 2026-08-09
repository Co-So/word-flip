import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import { Panel } from "@/components/Panel/Panel";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { AppSettings } from "@/domain/settings";
import { ResetDemoDialog } from "./ResetDemoDialog";
import { SignOutDialog } from "./SignOutDialog";
import styles from "./settings.module.css";

const groupSizes = [10, 20, 30, 50] as const;

function messageOf(error: unknown, fallback = "暂时无法读取设置"): string {
  if (error instanceof Error) return error.message;
  if (typeof error === "object" && error !== null && "message" in error && typeof error.message === "string") {
    return error.message;
  }
  return fallback;
}

export function SettingsPage() {
  const { auth, settings } = useRepositories();
  const supportsDemoReset = settings.supportsDemoReset();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const lifecycleEpochRef = useRef(0);
  const loadEpochRef = useRef(0);
  const mountedRef = useRef(false);
  const resetTriggerRef = useRef<HTMLButtonElement>(null);
  const resetEpochRef = useRef(0);
  const resettingRef = useRef(false);
  const saveEpochRef = useRef(0);
  const savingRef = useRef(false);
  const signOutTriggerRef = useRef<HTMLButtonElement>(null);
  const signOutEpochRef = useRef(0);
  const signingOutRef = useRef(false);
  const wasDialogOpen = useRef(false);
  const [form, setForm] = useState<AppSettings | null>(null);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
  const [error, setError] = useState<string>();
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);
  const [signOutDialogOpen, setSignOutDialogOpen] = useState(false);
  const [signingOut, setSigningOut] = useState(false);
  const [signOutError, setSignOutError] = useState<string | null>(null);

  useEffect(() => {
    mountedRef.current = true;
    lifecycleEpochRef.current += 1;
    return () => {
      mountedRef.current = false;
      lifecycleEpochRef.current += 1;
      loadEpochRef.current += 1;
      saveEpochRef.current += 1;
      resetEpochRef.current += 1;
      signOutEpochRef.current += 1;
      savingRef.current = false;
      resettingRef.current = false;
      signingOutRef.current = false;
    };
  }, []);

  const load = useCallback(async () => {
    if (!mountedRef.current) return;
    const lifecycleEpoch = lifecycleEpochRef.current;
    const requestEpoch = ++loadEpochRef.current;
    // 新加载以服务端快照为准，并淘汰仍未完成的旧保存结果。
    saveEpochRef.current += 1;
    savingRef.current = false;
    setSaving(false);
    setSaved(false);
    setSaveError(null);
    setStatus("loading");
    try {
      const snapshot = await settings.getSettings();
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== loadEpochRef.current) return;
      setForm(snapshot);
      setError(undefined);
      setStatus("ready");
    } catch (reason) {
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== loadEpochRef.current) return;
      setError(messageOf(reason));
      setStatus("error");
    }
  }, [settings]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (wasDialogOpen.current && !dialogOpen) {
      resetTriggerRef.current?.focus();
    }
    wasDialogOpen.current = dialogOpen;
  }, [dialogOpen]);

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form || savingRef.current) return;
    savingRef.current = true;
    const lifecycleEpoch = lifecycleEpochRef.current;
    const requestEpoch = ++saveEpochRef.current;
    setSaved(false);
    setSaveError(null);
    setSaving(true);
    try {
      const snapshot = await settings.updateSettings(form);
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== saveEpochRef.current) return;
      setForm(snapshot);
      setSaved(true);
    } catch {
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== saveEpochRef.current) return;
      setSaveError("暂时无法保存设置");
    } finally {
      if (requestEpoch === saveEpochRef.current) {
        savingRef.current = false;
        if (mountedRef.current && lifecycleEpoch === lifecycleEpochRef.current) setSaving(false);
      }
    }
  }

  async function reset() {
    if (resettingRef.current) return;
    resettingRef.current = true;
    const lifecycleEpoch = lifecycleEpochRef.current;
    const requestEpoch = ++resetEpochRef.current;
    setResetting(true);
    setResetError(null);
    try {
      await settings.resetDemo();
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== resetEpochRef.current) return;
      queryClient.clear();
      setDialogOpen(false);
      navigate("/today", { replace: true });
    } catch (reason) {
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== resetEpochRef.current) return;
      setResetError(reason instanceof Error ? reason.message : "暂时无法重置演示数据");
    } finally {
      if (requestEpoch === resetEpochRef.current) {
        resettingRef.current = false;
        if (mountedRef.current && lifecycleEpoch === lifecycleEpochRef.current) setResetting(false);
      }
    }
  }

  async function signOut() {
    if (signingOutRef.current) return;
    signingOutRef.current = true;
    const lifecycleEpoch = lifecycleEpochRef.current;
    const requestEpoch = ++signOutEpochRef.current;
    setSigningOut(true);
    setSignOutError(null);
    try {
      await auth.signOut();
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== signOutEpochRef.current) return;
      queryClient.clear();
      setSignOutDialogOpen(false);
      navigate("/login", { replace: true });
    } catch (reason) {
      if (!mountedRef.current
        || lifecycleEpoch !== lifecycleEpochRef.current
        || requestEpoch !== signOutEpochRef.current) return;
      setSignOutError(messageOf(reason, "暂时无法退出登录，请重试"));
    } finally {
      if (requestEpoch === signOutEpochRef.current) {
        signingOutRef.current = false;
        if (mountedRef.current && lifecycleEpoch === lifecycleEpochRef.current) setSigningOut(false);
      }
    }
  }

  return <div className={styles.page}>
    <header className={styles.hero}>
      <p className={styles.eyebrow}>
        {supportsDemoReset ? "PREFERENCES · LOCAL DEMO" : "PREFERENCES · SYNCED"}
      </p>
      <h1>设置</h1>
      <p>
        {supportsDemoReset
          ? "调整稳定的演示偏好，学习业务仍由服务端权威处理。"
          : "调整学习偏好；发音与分组设置会同步到服务端。"}
      </p>
    </header>
    <AsyncState error={error} onRetry={load} status={status}>
      {form ? <div className={styles.workspace}>
        <Panel title="学习偏好">
          <form className={styles.form} onSubmit={save}>
            <label className={styles.switchRow}>
              <span><strong>播放发音</strong><small>点击学习卡片时自动发音（正反面）</small></span>
              <input
                aria-label="播放发音"
                checked={form.soundEnabled}
                disabled={saving}
                onChange={(event) => setForm({ ...form, soundEnabled: event.target.checked })}
                type="checkbox"
              />
            </label>
            <label className={styles.switchRow}>
              <span><strong>减少动态效果</strong><small>减少翻转和位移动画</small></span>
              <input
                aria-label="减少动态效果"
                checked={form.reducedMotion}
                disabled={saving}
                onChange={(event) => setForm({ ...form, reducedMotion: event.target.checked })}
                type="checkbox"
              />
            </label>
            <label className={styles.selectRow}>每组单词数
              <select
                disabled={saving}
                onChange={(event) => setForm({ ...form, groupSize: Number(event.target.value) as AppSettings["groupSize"] })}
                value={form.groupSize}
              >
                {groupSizes.map((size) => <option key={size} value={size}>{size} 词每组</option>)}
              </select>
            </label>
            <div className={styles.saveRow}>
              <Button disabled={saving} type="submit">保存设置</Button>
              {saved ? <span role="status">设置已保存</span> : null}
              {saveError
                ? <p role="alert" style={{ color: "var(--wf-error)", margin: 0 }}>{saveError}</p>
                : null}
            </div>
          </form>
        </Panel>
        {supportsDemoReset ? <Panel title="演示数据">
            <p className={styles.muted}>恢复固定 configured 种子，历史模拟变更将从当前浏览器移除。</p>
            <Link className={styles.mediaLink} to="/media">管理卡片图片</Link>
            <Button
              onClick={() => {
                setResetError(null);
                setDialogOpen(true);
              }}
              ref={resetTriggerRef}
              variant="ghost"
            >
              重置演示数据
            </Button>
          </Panel> : null}
        <div className={styles.accountPanel}>
          <Panel title="账户">
            <div className={styles.accountActions}>
              <span><strong>退出当前账户</strong><small>清除当前浏览器会话并返回登录页。</small></span>
              <Button
                className={styles.dangerButton}
                onClick={() => {
                  setSignOutError(null);
                  setSignOutDialogOpen(true);
                }}
                ref={signOutTriggerRef}
                variant="ghost"
              >
                退出登录
              </Button>
            </div>
          </Panel>
        </div>
      </div> : null}
    </AsyncState>
    {supportsDemoReset && dialogOpen ? <ResetDemoDialog
      error={resetError}
      onCancel={() => {
        if (!resettingRef.current) setDialogOpen(false);
      }}
      onConfirm={() => { void reset(); }}
      resetting={resetting}
    /> : null}
    {signOutDialogOpen ? <SignOutDialog
      error={signOutError}
      onCancel={() => {
        if (!signingOutRef.current) setSignOutDialogOpen(false);
      }}
      onConfirm={() => { void signOut(); }}
      returnFocusRef={signOutTriggerRef}
      signingOut={signingOut}
    /> : null}
  </div>;
}
