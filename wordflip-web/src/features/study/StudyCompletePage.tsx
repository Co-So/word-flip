import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { StudySessionView } from "@/domain/learning";
import { FocusShell } from "@/layouts/FocusShell/FocusShell";
import styles from "./study.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取学习会话";
}

export function StudyCompletePage() {
  const { sessionId = "" } = useParams();
  const { study } = useRepositories();
  const navigate = useNavigate();
  const [session, setSession] = useState<StudySessionView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setSession(null);
    setError(null);
    study.getSession(sessionId)
      .then((snapshot) => {
        if (!active) return;
        if (snapshot.status !== "completed") {
          navigate(`/study/${sessionId}`, { replace: true });
          return;
        }
        setSession(snapshot);
        setError(null);
      })
      .catch((reason) => {
        if (!active) return;
        setSession(null);
        setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [navigate, sessionId, study]);

  return (
    <FocusShell
      aside={<div className={styles.completeAside}><p>SESSION CLOSED</p><strong>浏览不会改变掌握度</strong></div>}
      onExit={() => navigate("/today")}
      progress={session?.progressLabel ?? "STUDY COMPLETE"}
      title="学习完成"
    >
      {error ? (
        <EmptyState
          action={<Link className={styles.textLink} to="/today">返回 Today</Link>}
          description={error}
          title="无法恢复学习会话"
        />
      ) : !session ? (
        <div aria-live="polite" className={styles.loading} role="status">正在确认学习结果…</div>
      ) : (
        <section className={styles.completePanel}>
          <p className={styles.completeMark}>COMPLETE</p>
          <h2>本次学习已完成</h2>
          <p>已回放固定的会话与 Today 快照；翻卡过程没有修改双轨掌握度。</p>
          <div className={styles.completeActions}>
            <Link className={styles.primaryLink} to="/today">返回 Today</Link>
            <Link className={styles.secondaryLink} to="/quiz">进入 Quiz</Link>
          </div>
        </section>
      )}
    </FocusShell>
  );
}
