import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { AsyncState } from "@/components/AsyncState/AsyncState";
import { Button } from "@/components/Button/Button";
import { EmptyState } from "@/components/EmptyState/EmptyState";
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
  const activatingRef = useRef(false);
  const loadEpochRef = useRef(0);
  const mountedRef = useRef(false);
  const [items, setItems] = useState<BookOverview[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activating, setActivating] = useState(false);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      loadEpochRef.current += 1;
    };
  }, []);

  const load = useCallback(async () => {
    const requestEpoch = ++loadEpochRef.current;
    try {
      const nextItems = await books.list();
      if (!mountedRef.current || requestEpoch !== loadEpochRef.current) return;
      setItems(nextItems);
      setError(null);
    } catch (reason) {
      if (!mountedRef.current || requestEpoch !== loadEpochRef.current) return;
      setError(messageOf(reason));
    }
  }, [books]);

  useEffect(() => { void load(); }, [load]);

  async function activate(bookId: string) {
    // 激活请求必须串行，避免较慢的旧选择覆盖用户当前计划。
    if (activatingRef.current) return;
    activatingRef.current = true;
    setActivating(true);
    try {
      await books.activateBook(bookId);
      if (!mountedRef.current) return;
      await load();
    } catch (reason) {
      if (mountedRef.current) setError(messageOf(reason));
    } finally {
      activatingRef.current = false;
      if (mountedRef.current) setActivating(false);
    }
  }

  return <AsyncState error={error ?? undefined} onRetry={load} status={error ? "error" : items ? "ready" : "loading"}>
    {items ? <div className={styles.page}>
      <header className={styles.hero}>
        <div><p className={styles.eyebrow}>BOOKSHELF</p><h1>词书与学习计划</h1></div>
        <p>一次只使用一个当前计划；切换不会删除旧计划的分组和进度。</p>
      </header>
      {items.length === 0 ? <EmptyState
        action={<Link className={styles.detailLink} to="/onboarding">返回首次设置</Link>}
        description="当前没有可创建计划的已发布词书。"
        title="还没有可用词书"
      /> : <div className={styles.bookGrid}>
        {items.map((book) => <article aria-label={book.title} key={book.bookId}>
          <Panel>
            <div className={styles.bookHeader}>
              <div><span className={styles.index}>{book.bookId.replace("book-", "").toUpperCase()}</span><h2>{book.title}</h2></div>
              <StatusTag tone={book.planStatus === "current" ? "sage" : "neutral"}>{statusCopy[book.planStatus]}</StatusTag>
            </div>
            <p className={styles.cardCount}>{book.cardCount.toLocaleString()} 张已发布学习卡</p>
            {book.progress ? <div className={styles.progress}>
              <span>当前进度</span><strong>{book.progress.completionPercent}%</strong>
            </div> : <p className={styles.historyNote}>{book.planId === null
              ? "创建学习计划后即可查看进度。"
              : "进度统计暂时不可用，请稍后重试。"}</p>}
            <div className={styles.actions}>
              <Link className={styles.detailLink} to={`/books/${book.bookId}`}>查看详情</Link>
              {book.planStatus === "current"
                ? <Button disabled variant="ghost">当前计划</Button>
                : <Button disabled={activating} onClick={() => void activate(book.bookId)}>
                  {book.planStatus === "history" ? "切换到此计划" : "开始学习"}
                </Button>}
            </div>
          </Panel>
        </article>)}
      </div>}
    </div> : null}
  </AsyncState>;
}
