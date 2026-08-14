import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { GroupCardPage } from "@/domain/groups";
import styles from "./groups.module.css";

const candidateQuery = { all: true, page: 1, size: 100 } as const;
const appErrorKinds = new Set<AppError["kind"]>([
  "validation", "unauthorized", "not-found", "conflict", "unavailable", "unknown"
]);

function isAppError(reason: unknown): reason is AppError {
  return typeof reason === "object" && reason !== null &&
    "kind" in reason && typeof reason.kind === "string" && appErrorKinds.has(reason.kind as AppError["kind"]) &&
    "message" in reason && typeof reason.message === "string";
}

function safeErrorMessage(reason: unknown, fallback: string): string {
  return isAppError(reason) ? reason.message : fallback;
}

export function CustomGroupPage() {
  const { groups } = useRepositories();
  const navigate = useNavigate();
  const [candidates, setCandidates] = useState<GroupCardPage | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set());
  const [name, setName] = useState("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const [feedback, setFeedback] = useState<{ kind: "error" | "success"; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const mountedRef = useRef(false);
  const requestVersionRef = useRef(0);
  const navigateTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const loadCandidates = useCallback(async (reset: boolean): Promise<GroupCardPage | null> => {
    const requestVersion = ++requestVersionRef.current;
    if (reset) {
      setCandidates(null);
      setLoadError(null);
    }
    try {
      const result = await groups.listUnassigned(candidateQuery);
      if (!mountedRef.current || requestVersion !== requestVersionRef.current) return null;
      setCandidates(result);
      const availableIds = new Set(result.cards.map((candidate) => candidate.cardId));
      // 所有候选恢复路径都统一剔除已失效的 cardId，避免重试再次提交冲突成员。
      setSelectedIds((current) => new Set([...current].filter((cardId) => availableIds.has(cardId))));
      setLoadError(null);
      return result;
    } catch (reason: unknown) {
      if (!mountedRef.current || requestVersion !== requestVersionRef.current) return null;
      setLoadError(safeErrorMessage(reason, "暂时无法获取未入组学习卡"));
      return null;
    }
  }, [groups]);

  useEffect(() => {
    mountedRef.current = true;
    void loadCandidates(true);
    return () => {
      mountedRef.current = false;
      requestVersionRef.current += 1;
      if (navigateTimerRef.current !== null) clearTimeout(navigateTimerRef.current);
    };
  }, [loadCandidates]);

  function toggleCard(cardId: string) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(cardId)) {
        next.delete(cardId);
      } else if (next.size < 500) {
        // 前端与 API 契约一致限制 500 张，不在浏览器计算掌握度。
        next.add(cardId);
      }
      return next;
    });
    setFeedback(null);
  }

  async function submit() {
    if (selectedIds.size === 0) {
      setFeedback({ kind: "error", message: "请先选择单词" });
      return;
    }
    if (submitting) return;
    setSubmitting(true);
    setFeedback(null);
    const submittedIds = [...selectedIds];
    try {
      await groups.createCustomGroup({
        name: name.trim() || undefined,
        cardIds: submittedIds
      });
      if (!mountedRef.current) return;
      setFeedback({
        kind: "success",
        message: `已创建包含 ${submittedIds.length} 张卡片的自定义分组`
      });
      // 先让成功反馈进入可访问的 live region，再返回词书页。
      navigateTimerRef.current = setTimeout(() => {
        if (mountedRef.current) navigate("/books");
      }, 500);
    } catch (reason: unknown) {
      if (!mountedRef.current) return;
      if (isAppError(reason) && reason.kind === "conflict") {
        // 冲突公告必须先于候选刷新，即使刷新失败也不能吞掉。
        setFeedback({ kind: "error", message: "部分选择已失效，请刷新候选后重新确认" });
        await loadCandidates(false);
      } else {
        setFeedback({ kind: "error", message: safeErrorMessage(reason, "暂时无法创建自定义分组") });
      }
    } finally {
      if (mountedRef.current) setSubmitting(false);
    }
  }

  const status = candidates === null ? (loadError ? "error" : "loading") : "ready";
  return <AsyncState error={loadError ?? undefined} status={status} onRetry={() => { void loadCandidates(true); }}>
    {candidates ? <div className={styles.page}>
      <Link className={styles.backLink} to="/groups">返回分组</Link>
      <header className={styles.detailHero}>
        <div>
          <p className={styles.eyebrow}>CUSTOM GROUP</p>
          <h1>新建自定义分组</h1>
          <p>从尚未入组的单词中点选，组成自定义分组。</p>
        </div>
        <strong aria-live="polite" className={styles.selectedCount}>已选 {selectedIds.size} 个</strong>
      </header>

      <label className={styles.nameField}>
        <span>分组名称</span>
        <input maxLength={64} onChange={(event) => setName(event.target.value)} placeholder="留空则自动命名" value={name} />
      </label>

      {candidates.cards.length === 0 ? <p className={styles.emptyCandidates}>当前词书没有未入组的已发布学习卡</p> :
        <fieldset className={styles.chipFieldset}>
          <legend>选择学习卡</legend>
          <div className={styles.chipGrid}>
            {candidates.cards.map((candidate) => {
              const selected = selectedIds.has(candidate.cardId);
              return <label className={styles.choiceChip} key={candidate.cardId}>
                <input
                  aria-label={`${candidate.headword} ${candidate.primaryDefinition}`}
                  checked={selected}
                  disabled={submitting || (!selected && selectedIds.size >= 500)}
                  onChange={() => toggleCard(candidate.cardId)}
                  type="checkbox"
                />
                <span><strong>{candidate.headword}</strong><small>{candidate.primaryDefinition}</small></span>
              </label>;
            })}
          </div>
        </fieldset>}

      {feedback && <p aria-live="assertive" className={feedback.kind === "success" ? styles.successFeedback : styles.errorFeedback} role={feedback.kind === "error" ? "alert" : "status"}>
        {feedback.message}
      </p>}
      {loadError && <div className={styles.retryFeedback}>
        <p aria-live="polite" className={styles.errorFeedback} role="status">{loadError}</p>
        <Button onClick={() => { void loadCandidates(false); }} variant="ghost">重新尝试</Button>
      </div>}
      <div className={styles.formActions}>
        <Button disabled={submitting} onClick={() => { void submit(); }}>
          {submitting ? "正在保存…" : "保存分组"}
        </Button>
      </div>
    </div> : null}
  </AsyncState>;
}
