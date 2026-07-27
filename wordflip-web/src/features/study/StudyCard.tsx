import type { LearningCard } from "@/domain/learning";
import styles from "./study.module.css";

export interface StudyCardProps {
  card: LearningCard;
  isFlipped: boolean;
  onFlip: () => void;
}

/** 学习卡只展示服务端卡片与记忆快照；翻面本身不产生任何业务写入。 */
export function StudyCard({ card, isFlipped, onFlip }: StudyCardProps) {
  return (
    <button
      aria-label={`翻转 ${card.headword} 学习卡`}
      aria-pressed={isFlipped}
      className={`${styles.card} ${isFlipped ? styles.flipped : ""}`}
      onClick={onFlip}
      type="button"
    >
      <div className={styles.cardContent} key={isFlipped ? "back" : "front"}>
        {isFlipped ? (
          <>
            <p className={styles.cardEyebrow}>DEFINITION</p>
            <h2>{card.headword}</h2>
            <strong className={styles.definition}>{card.definition}</strong>
            <p className={styles.example}>{card.example}</p>
            <span className={styles.flipHint}>点击或按 Enter 返回正面</span>
          </>
        ) : (
          <>
            <div
              aria-label={card.imageDescription}
              className={styles.imagePlaceholder}
              role="img"
            >
              <span />
            </div>
            <p className={styles.cardEyebrow}>WORD CARD</p>
            <h2>{card.headword}</h2>
            <p className={styles.phonetic}>{card.phonetic}</p>
            <span className={styles.heatLabel}>
              听写热力 {card.progress.dictation.heatLevel} · 只读
            </span>
            <span className={styles.flipHint}>点击、空格或 Enter 查看释义</span>
          </>
        )}
      </div>
    </button>
  );
}
