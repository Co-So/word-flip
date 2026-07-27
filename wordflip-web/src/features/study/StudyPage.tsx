import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { StudySessionView } from "@/domain/learning";
import { FocusShell } from "@/layouts/FocusShell/FocusShell";
import { StudyCard } from "./StudyCard";
import { StudySidebar } from "./StudySidebar";
import styles from "./study.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取学习会话";
}

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.matches("input, textarea, select") ||
    target.isContentEditable ||
    target.closest("[contenteditable]:not([contenteditable='false'])") !== null
  );
}

export function StudyPage() {
  const { sessionId = "" } = useParams();
  const { study } = useRepositories();
  const navigate = useNavigate();
  const [session, setSession] = useState<StudySessionView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cardIndex, setCardIndex] = useState(0);
  const [isFlipped, setIsFlipped] = useState(false);
  const [isCompleting, setIsCompleting] = useState(false);

  useEffect(() => {
    let active = true;
    setSession(null);
    setError(null);
    setCardIndex(0);
    setIsFlipped(false);
    study.getSession(sessionId)
      .then((snapshot) => {
        if (!active) return;
        setSession(snapshot);
        if (snapshot.status === "completed") {
          navigate(`/study/${sessionId}/complete`, { replace: true });
        }
      })
      .catch((reason) => {
        if (active) setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [navigate, sessionId, study]);

  const flipCard = useCallback(() => {
    setIsFlipped((value) => !value);
  }, []);

  const moveCard = useCallback((direction: -1 | 1) => {
    setCardIndex((current) => {
      const lastIndex = Math.max((session?.cards.length ?? 1) - 1, 0);
      return Math.min(Math.max(current + direction, 0), lastIndex);
    });
    setIsFlipped(false);
  }, [session?.cards.length]);

  useEffect(() => {
    if (!session || session.cards.length === 0) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (isEditableTarget(event.target)) return;
      if (event.key === " " || event.key === "Enter") {
        event.preventDefault();
        flipCard();
      } else if (event.key === "ArrowRight") {
        event.preventDefault();
        moveCard(1);
      } else if (event.key === "ArrowLeft") {
        event.preventDefault();
        moveCard(-1);
      }
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [flipCard, moveCard, session]);

  const completeSession = async () => {
    setIsCompleting(true);
    setError(null);
    try {
      await study.completeSession(sessionId);
      navigate(`/study/${sessionId}/complete`);
    } catch (reason) {
      setError(errorMessage(reason));
      setIsCompleting(false);
    }
  };

  const aside = session ? <StudySidebar session={session} /> : (
    <p className={styles.asidePlaceholder}>学习会话加载后会显示队列摘要。</p>
  );

  return (
    <FocusShell
      aside={aside}
      onExit={() => navigate("/today")}
      progress={session?.progressLabel ?? "STUDY SESSION"}
      title="专注学习"
    >
      {error ? (
        <EmptyState
          action={<Link className={styles.textLink} to="/today">返回 Today</Link>}
          description={error}
          title="无法继续本次学习"
        />
      ) : !session ? (
        <div aria-live="polite" className={styles.loading} role="status">正在整理学习卡…</div>
      ) : session.cards.length === 0 ? (
        <EmptyState
          action={<Link className={styles.textLink} to="/today">返回 Today</Link>}
          description="本次会话没有可浏览的学习卡。"
          title="学习队列为空"
        />
      ) : (
        <div className={styles.workspace}>
          <StudyCard
            card={session.cards[cardIndex]}
            isFlipped={isFlipped}
            onFlip={flipCard}
          />
          <div className={styles.controls}>
            <Button
              aria-label="上一词"
              disabled={cardIndex === 0}
              onClick={() => moveCard(-1)}
              variant="ghost"
            >
              上一词
            </Button>
            <span>{cardIndex + 1} / {session.cards.length}</span>
            <Button
              aria-label="下一词"
              disabled={cardIndex === session.cards.length - 1}
              onClick={() => moveCard(1)}
              variant="ghost"
            >
              下一词
            </Button>
          </div>
          <Button
            className={styles.completeButton}
            disabled={isCompleting}
            onClick={completeSession}
          >
            {isCompleting ? "正在完成…" : "完成学习"}
          </Button>
        </div>
      )}
    </FocusShell>
  );
}
