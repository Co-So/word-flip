import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import { Panel } from "@/components/Panel/Panel";
import { StatusTag } from "@/components/StatusTag/StatusTag";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { BookOverview } from "@/domain/books";
import styles from "./books.module.css";

function messageOf(error: unknown): string {
  return (error as AppError).message ?? "暂时无法获取词书";
}

const statusCopy = {
  current: "当前计划",
  history: "历史计划",
  available: "未开始"
} as const;

export function BooksPage() {
  const { books } = useRepositories();
  const [items, setItems] = useState<BookOverview[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activating, setActivating] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await books.list());
      setError(null);
    } catch (reason) {
      setError(messageOf(reason));
    }
  }, [books]);

  useEffect(() => { void load(); }, [load]);

  async function activate(bookId: string) {
    setActivating(bookId);
    try {
      await books.activateBook(bookId);
      await load();
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setActivating(null);
    }
  }

  return <AsyncState error={error ?? undefined} status={error ? "error" : items ? "ready" : "loading"}>
    {items ? <div className={styles.page}>
      <header className={styles.hero}>
        <div><p className={styles.eyebrow}>BOOKSHELF</p><h1>词书与学习计划</h1></div>
        <p>一次只使用一个当前计划；切换不会删除旧计划的分组和进度。</p>
      </header>
      <div className={styles.bookGrid}>
        {items.map((book) => <article aria-label={book.title} key={book.bookId}>
          <Panel>
            <div className={styles.bookHeader}>
              <div><span className={styles.index}>{book.bookId.replace("book-", "").toUpperCase()}</span><h2>{book.title}</h2></div>
              <StatusTag tone={book.planStatus === "current" ? "sage" : "neutral"}>{statusCopy[book.planStatus]}</StatusTag>
            </div>
            <p className={styles.cardCount}>{book.cardCount.toLocaleString()} 张已发布学习卡</p>
            {book.progress ? <div className={styles.progress}>
              <span>当前进度</span><strong>{book.progress.completionRate}%</strong>
            </div> : <p className={styles.historyNote}>{book.planStatus === "history" ? "历史数据已保留，切回后可查看。" : "将从固定计划快照开始。"}</p>}
            <div className={styles.actions}>
              <Link className={styles.detailLink} to={`/books/${book.bookId}`}>查看详情</Link>
              {book.planStatus === "current"
                ? <Button disabled variant="ghost">当前计划</Button>
                : <Button disabled={activating === book.bookId} onClick={() => void activate(book.bookId)}>
                  {book.planStatus === "history" ? "切换到此计划" : "开始学习"}
                </Button>}
            </div>
          </Panel>
        </article>)}
      </div>
    </div> : null}
  </AsyncState>;
}
