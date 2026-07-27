import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { QuizSessionResult } from "@/domain/quiz";
import { FocusShell } from "@/layouts/FocusShell/FocusShell";
import styles from "./quiz.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取测验结果";
}

export function QuizResultPage() {
  const { sessionId = "" } = useParams();
  const { quiz } = useRepositories();
  const navigate = useNavigate();
  const [result, setResult] = useState<QuizSessionResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setResult(null);
    setError(null);
    quiz.getSession(sessionId)
      .then((session) => {
        if (!active) return;
        if (session.status !== "completed") {
          navigate(`/quiz/${sessionId}`, { replace: true });
          return;
        }
        return quiz.getResult(sessionId).then((snapshot) => {
          if (active) setResult(snapshot);
        });
      })
      .catch((reason) => {
        if (active) setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [navigate, quiz, sessionId]);

  const aside = result ? (
    <div className={styles.resultAside}>
      <p className={styles.eyebrow}>SERVER SNAPSHOT</p>
      <strong>{result.score} / {result.total} · {result.accuracy}%</strong>
      <p>两个 skill 始终独立保存，本页只展示仓储返回的固定结果。</p>
    </div>
  ) : <p className={styles.loading}>正在读取结果快照…</p>;

  return (
    <FocusShell
      aside={aside}
      onExit={() => navigate("/quiz")}
      progress="QUIZ COMPLETE"
      title="测验结果"
    >
      {error ? (
        <EmptyState
          action={<Link className={styles.textLink} to="/quiz">返回测验设置</Link>}
          description={error}
          title="无法显示测验结果"
        />
      ) : !result ? (
        <div className={styles.loading} role="status">正在整理测验结果…</div>
      ) : (
        <section className={styles.resultPanel}>
          <p className={styles.eyebrow}>SESSION COMPLETE</p>
          <h2>测验完成</h2>
          <p className={styles.score}>{result.score} / {result.total}</p>
          <div className={styles.summaryGrid}>
            {[result.dictation, result.choice].map((summary) => (
              <article key={summary.label}>
                <h3>{summary.label}</h3>
                <strong>{summary.correct} / {summary.attempted}</strong>
                <p>{summary.progressLabel}</p>
              </article>
            ))}
          </div>
          <p className={styles.sourceNote}>本次变化来自测验结果；翻卡学习不会改变掌握度。</p>
          <div className={styles.resultActions}>
            <Link className={styles.primaryLink} to="/quiz">再来一次</Link>
            <Link className={styles.secondaryLink} to="/stats">查看统计</Link>
            <Link className={styles.textLink} to="/today">返回 Today</Link>
          </div>
        </section>
      )}
    </FocusShell>
  );
}
