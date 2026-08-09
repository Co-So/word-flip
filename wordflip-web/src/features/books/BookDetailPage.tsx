import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { BookOverview } from "@/domain/books";
import styles from "./books.module.css";

export function BookDetailPage() {
  const { bookId = "" } = useParams();
  const { books } = useRepositories();
  const [book, setBook] = useState<BookOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    books.getDetail(bookId)
      .then((detail) => { if (active) setBook(detail); })
      .catch((reason) => { if (active) setError((reason as AppError).message ?? "暂时无法获取词书"); });
    return () => { active = false; };
  }, [bookId, books]);

  return <AsyncState error={error ?? undefined} status={error ? "error" : book ? "ready" : "loading"}>
    {book ? <div className={styles.page}>
      <Link className={styles.backLink} to="/books">返回词书</Link>
      <header className={styles.detailHero}>
        <div><p className={styles.eyebrow}>BOOK DETAIL</p><h1>{book.title}</h1><p>{book.cardCount.toLocaleString()} 张已发布学习卡</p></div>
        <StatusTag tone={book.planStatus === "current" ? "sage" : "neutral"}>
          {book.planStatus === "current" ? "当前计划" : book.planStatus === "history" ? "历史计划" : "未开始"}
        </StatusTag>
      </header>
      <Panel title="学习进度">
        {book.progress ? <div className={styles.detailProgress}>
          <strong>{book.progress.completionPercent}%</strong>
          <div><span>{book.progress.masteredCount} / {book.progress.assignedCardCount} 张已掌握</span>
            <div aria-label="词书完成度" className={styles.progressTrack}><i style={{ width: `${book.progress.completionPercent}%` }} /></div>
          </div>
        </div> : <p className={styles.historyNote}>{book.planId === null
          ? "创建学习计划后即可查看进度。"
          : "进度统计暂时不可用，请稍后重试。"}</p>}
      </Panel>
    </div> : null}
  </AsyncState>;
}
