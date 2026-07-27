import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import { Panel } from "@/components/Panel/Panel";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { AppSettings } from "@/domain/settings";
import { ResetDemoDialog } from "./ResetDemoDialog";
import styles from "./settings.module.css";

const groupSizes = [10, 20, 30, 50] as const;

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "暂时无法读取设置";
}

export function SettingsPage() {
  const { settings } = useRepositories();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const resetTriggerRef = useRef<HTMLButtonElement>(null);
  const resettingRef = useRef(false);
  const wasDialogOpen = useRef(false);
  const [form, setForm] = useState<AppSettings | null>(null);
  const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
  const [error, setError] = useState<string>();
  const [saved, setSaved] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [resetError, setResetError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      setForm(await settings.getSettings());
      setStatus("ready");
    } catch (reason) {
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
    if (!form) return;
    setSaved(false);
    const snapshot = await settings.updateSettings(form);
    setForm(snapshot);
    setSaved(true);
  }

  async function reset() {
    if (resettingRef.current) return;
    resettingRef.current = true;
    setResetting(true);
    setResetError(null);
    try {
      await settings.resetDemo();
      queryClient.clear();
      setDialogOpen(false);
      navigate("/today", { replace: true });
    } catch (reason) {
      setResetError(reason instanceof Error ? reason.message : "暂时无法重置演示数据");
    } finally {
      resettingRef.current = false;
      setResetting(false);
    }
  }

  return <div className={styles.page}>
    <header className={styles.hero}><p className={styles.eyebrow}>PREFERENCES · LOCAL DEMO</p>
      <h1>设置</h1><p>调整稳定的演示偏好，学习业务仍由服务端权威处理。</p></header>
    <AsyncState error={error} onRetry={load} status={status}>
      {form ? <div className={styles.workspace}>
        <Panel title="学习偏好">
          <form className={styles.form} onSubmit={save}>
            <label className={styles.switchRow}>
              <span><strong>播放发音</strong><small>打开卡片时允许播放单词音频</small></span>
              <input
                aria-label="播放发音"
                checked={form.soundEnabled}
                onChange={(event) => setForm({ ...form, soundEnabled: event.target.checked })}
                type="checkbox"
              />
            </label>
            <label className={styles.switchRow}>
              <span><strong>减少动态效果</strong><small>减少翻转和位移动画</small></span>
              <input
                aria-label="减少动态效果"
                checked={form.reducedMotion}
                onChange={(event) => setForm({ ...form, reducedMotion: event.target.checked })}
                type="checkbox"
              />
            </label>
            <label className={styles.selectRow}>每组单词数
              <select
                onChange={(event) => setForm({ ...form, groupSize: Number(event.target.value) as AppSettings["groupSize"] })}
                value={form.groupSize}
              >
                {groupSizes.map((size) => <option key={size} value={size}>{size} 词每组</option>)}
              </select>
            </label>
            <div className={styles.saveRow}>
              <Button type="submit">保存设置</Button>
              {saved ? <span role="status">设置已保存</span> : null}
            </div>
          </form>
        </Panel>
        <Panel title="演示数据">
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
        </Panel>
      </div> : null}
    </AsyncState>
    {dialogOpen ? <ResetDemoDialog
      error={resetError}
      onCancel={() => {
        if (!resettingRef.current) setDialogOpen(false);
      }}
      onConfirm={() => { void reset(); }}
      resetting={resetting}
    /> : null}
  </div>;
}
