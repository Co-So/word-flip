import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import { EmptyState } from "@/components/EmptyState/EmptyState";
import { ExitConfirmationDialog } from "@/components/ExitConfirmationDialog/ExitConfirmationDialog";
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

/** 卡片朗读使用浏览器系统语音，并用取消队列模拟 Android 的 QUEUE_FLUSH 行为。 */
function speakStudyCard(headword: string) {
  if (
    typeof window === "undefined" ||
    !("speechSynthesis" in window) ||
    typeof SpeechSynthesisUtterance === "undefined"
  ) return;

  const utterance = new SpeechSynthesisUtterance(headword);
  utterance.lang = "en-US";
  utterance.rate = 1;
  window.speechSynthesis.cancel();
  window.speechSynthesis.speak(utterance);
}

function isInteractiveTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  return (
    target.closest(
      "button, a[href], input, textarea, select, summary, [contenteditable]:not([contenteditable='false']), [role='button'], [role='link'], [role='checkbox'], [role='radio'], [role='switch'], [role='slider'], [role='textbox'], [tabindex]:not([tabindex='-1'])"
    ) !== null ||
    target.isContentEditable ||
    target.closest("[contenteditable]:not([contenteditable='false'])") !== null
  );
}

/** 方向键只在当前卡片墙内移动焦点，并在首尾边界停止。 */
function moveCardFocus(target: EventTarget | null, direction: -1 | 1) {
  if (!(target instanceof HTMLElement)) return;
  const currentCard = target.closest<HTMLButtonElement>("[data-study-card]");
  const cardWall = currentCard?.closest("[data-testid='study-card-wall']");
  if (!currentCard || !cardWall) return;

  const cards = Array.from(
    cardWall.querySelectorAll<HTMLButtonElement>("[data-study-card]")
  );
  const currentIndex = cards.indexOf(currentCard);
  if (currentIndex < 0) return;
  const nextIndex = Math.min(Math.max(currentIndex + direction, 0), cards.length - 1);
  cards[nextIndex]?.focus();
}

export function StudyPage() {
  const { sessionId = "" } = useParams();
  const { settings, study } = useRepositories();
  const navigate = useNavigate();
  const [session, setSession] = useState<StudySessionView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [flippedCardIds, setFlippedCardIds] = useState<Set<string>>(() => new Set());
  const [isCompleting, setIsCompleting] = useState(false);
  const [isExitOpen, setIsExitOpen] = useState(false);
  const exitButtonRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    let active = true;
    setSession(null);
    setError(null);
    setSoundEnabled(false);
    setFlippedCardIds(new Set());
    Promise.all([study.getSession(sessionId), settings.getSettings()])
      .then(([snapshot, appSettings]) => {
        if (!active) return;
        setSession(snapshot);
        setSoundEnabled(appSettings.soundEnabled);
        if (snapshot.status === "completed") {
          navigate(`/study/${sessionId}/complete`, { replace: true });
        }
      })
      .catch((reason) => {
        if (active) setError(errorMessage(reason));
      });
    return () => { active = false; };
  }, [navigate, sessionId, settings, study]);

  const flipCard = useCallback((cardId: string, headword: string) => {
    setFlippedCardIds((current) => {
      const next = new Set(current);
      if (next.has(cardId)) {
        next.delete(cardId);
      } else {
        next.add(cardId);
      }
      return next;
    });
    if (soundEnabled) speakStudyCard(headword);
  }, [soundEnabled]);

  const requestExit = useCallback(() => {
    if (!session || session.status === "completed") {
      navigate("/today");
      return;
    }
    returnFocusRef.current = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : exitButtonRef.current;
    setIsExitOpen(true);
  }, [navigate, session]);

  useEffect(() => {
    if (!session || session.cards.length === 0) return;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        if (!isExitOpen) requestExit();
        return;
      }
      if (event.key === "ArrowRight" && targetIsStudyCard(event.target)) {
        event.preventDefault();
        moveCardFocus(event.target, 1);
        return;
      }
      if (event.key === "ArrowLeft" && targetIsStudyCard(event.target)) {
        event.preventDefault();
        moveCardFocus(event.target, -1);
        return;
      }
      if (isInteractiveTarget(event.target)) return;
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [isExitOpen, requestExit, session]);

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
      exitButtonRef={exitButtonRef}
      onExit={requestExit}
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
          <div className={styles.wallHeader}>
            <p className={styles.wallEyebrow}>CARD WALL</p>
            <p className={styles.wallCount}>{session.cards.length} 张词汇卡</p>
          </div>
          <div className={styles.cardWall} data-testid="study-card-wall">
            {session.cards.map((card) => (
              <StudyCard
                card={card}
                isFlipped={flippedCardIds.has(card.cardId)}
                key={card.cardId}
                onFlip={() => flipCard(card.cardId, card.headword)}
              />
            ))}
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
      {isExitOpen ? (
        <ExitConfirmationDialog
          description="本次翻卡浏览尚未完成。退出不会写入记忆，但会离开当前卡片位置。"
          onCancel={() => setIsExitOpen(false)}
          onConfirm={() => navigate("/today")}
          returnFocusRef={returnFocusRef}
          title="退出本次学习？"
        />
      ) : null}
    </FocusShell>
  );
}

function targetIsStudyCard(target: EventTarget | null): boolean {
  return target instanceof HTMLElement && target.closest("[data-study-card]") !== null;
}
