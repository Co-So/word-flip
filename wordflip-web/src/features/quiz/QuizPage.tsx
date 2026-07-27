import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { QuizResult, QuizSession } from "@/domain/quiz";
import { FocusShell } from "@/layouts/FocusShell/FocusShell";
import { ChoiceQuestion } from "./ChoiceQuestion";
import { DictationQuestion } from "./DictationQuestion";
import styles from "./quiz.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取测验会话";
}

function newRequestId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `quiz-request-${Date.now()}-${Math.random()}`;
}

export function QuizPage() {
  const { sessionId = "" } = useParams();
  const { quiz } = useRepositories();
  const navigate = useNavigate();
  const requestId = useRef<string | null>(null);
  const [session, setSession] = useState<QuizSession | null>(null);
  const [feedback, setFeedback] = useState<QuizResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    setSession(null);
    setFeedback(null);
    setError(null);
    setSubmitting(false);
    requestId.current = null;
    quiz.getSession(sessionId)
      .then((snapshot) => {
        if (!active) return;
        if (snapshot.status === "completed") {
          navigate(`/quiz/${sessionId}/result`, { replace: true });
          return;
        }
        setSession(snapshot);
      })
      .catch((reason) => {
        if (active) setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [navigate, quiz, sessionId]);

  const submit = async (answer: string) => {
    if (!session || submitting || feedback) return;
    setSubmitting(true);
    setError(null);
    requestId.current ??= newRequestId();
    try {
      const response = await quiz.submitAnswer({
        sessionId: session.sessionId,
        questionId: session.question.questionId,
        requestId: requestId.current,
        cardId: session.question.cardId,
        answer
      });
      setFeedback(response);
      setSession(response.precomputed.sessionSnapshot);
    } catch (reason) {
      setError(errorMessage(reason));
      setSubmitting(false);
    }
  };

  const title = session?.skill === "choice" ? "选择测验" : "听写测验";
  const aside = (
    <div className={styles.quizAside}>
      <div>
        <p className={styles.eyebrow}>CURRENT QUEUE</p>
        <h2>{session?.progressLabel ?? "QUIZ SESSION"}</h2>
      </div>
      <dl>
        <div><dt>范围</dt><dd>{session?.scope === "due-today" ? "今日到期" : "当前计划"}</dd></div>
        <div><dt>题型</dt><dd>{session?.skill === "choice" ? "选择" : "听写"}</dd></div>
        <div><dt>写入</dt><dd>{session?.skill ?? "等待会话"}</dd></div>
      </dl>
      <section>
        <h3>键盘</h3>
        <p><kbd>Enter</kbd> 提交听写或聚焦按钮</p>
        <p><kbd>Space</kbd> 选择聚焦的选项</p>
      </section>
    </div>
  );

  return (
    <FocusShell
      aside={aside}
      onExit={() => navigate("/quiz")}
      progress={session?.progressLabel ?? "QUIZ SESSION"}
      title={title}
    >
      {error && !session ? (
        <EmptyState
          action={<Link className={styles.textLink} to="/quiz">返回测验设置</Link>}
          description={error}
          title="无法继续本次测验"
        />
      ) : !session ? (
        <div className={styles.loading} role="status">正在准备测验题…</div>
      ) : (
        <section className={styles.workspace}>
          {session.skill === "dictation" ? (
            <DictationQuestion
              disabled={submitting || feedback !== null}
              onSubmit={submit}
              question={session.question}
            />
          ) : (
            <ChoiceQuestion
              disabled={submitting || feedback !== null}
              onSubmit={submit}
              question={session.question}
            />
          )}
          {error ? <p className={styles.error} role="alert">{error}</p> : null}
          {feedback ? (
            <div
              aria-label="答题反馈"
              className={feedback.correct ? styles.feedbackSuccess : styles.feedbackWrong}
              role="status"
            >
              <p className={styles.eyebrow}>{feedback.correct ? "CORRECT" : "KEEP GOING"}</p>
              <h3>{feedback.feedback}</h3>
              {feedback.expectedAnswer ? <p>标准答案：{feedback.expectedAnswer}</p> : null}
              <Link className={styles.primaryLink} to={`/quiz/${session.sessionId}/result`}>
                查看测验结果
              </Link>
            </div>
          ) : null}
        </section>
      )}
    </FocusShell>
  );
}
