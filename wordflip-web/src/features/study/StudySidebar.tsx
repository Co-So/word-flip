import type { StudySessionView } from "@/domain/learning";
import styles from "./study.module.css";

export function StudySidebar({ session }: { session: StudySessionView }) {
  return (
    <div className={styles.sidebar}>
      <section aria-labelledby="study-progress">
        <p className={styles.sidebarEyebrow}>SESSION PROGRESS</p>
        <h2 id="study-progress">{session.progressLabel}</h2>
      </section>

      <section aria-labelledby="queue-summary">
        <h3 id="queue-summary">队列摘要</h3>
        <dl className={styles.queueSummary}>
          {session.queueSummary.map((item) => (
            <div key={item.label}>
              <dt>{item.label}</dt>
              <dd>{item.value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section aria-labelledby="keyboard-help">
        <h3 id="keyboard-help">键盘帮助</h3>
        <ul className={styles.keyboardHelp}>
          <li><kbd>Space</kbd><span>翻面</span></li>
          <li><kbd>←</kbd><span>上一词</span></li>
          <li><kbd>→</kbd><span>下一词</span></li>
        </ul>
      </section>
    </div>
  );
}
