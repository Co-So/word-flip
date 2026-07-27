import { useEffect, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { Book } from "@/domain/books";
import styles from "./onboarding.module.css";

const groupSizes = [10, 20, 30, 50] as const;

function messageOf(error: unknown): string {
  return (error as AppError).message ?? "暂时无法保存首次设置";
}

export function OnboardingPage() {
  const { books, settings } = useRepositories();
  const navigate = useNavigate();
  const [availableBooks, setAvailableBooks] = useState<Book[]>([]);
  const [bookId, setBookId] = useState("");
  const [groupSize, setGroupSize] = useState<10 | 20 | 30 | 50>(20);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    let active = true;
    books.listBooks().then((items) => {
      if (active) setAvailableBooks(items);
    }).catch(() => {
      if (active) setError("暂时无法获取可选词书");
    });
    return () => { active = false; };
  }, [books]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!bookId) {
      setError("请选择一本主词书");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await settings.saveOnboarding({ bookId, groupSize, groupStrategy: "book_order" });
      navigate("/today", { replace: true });
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return <main className={styles.page}><section className={styles.card}>
    <p className={styles.eyebrow}>FIRST STEP</p>
    <h1>设置你的学习计划</h1>
    <p className={styles.intro}>选择一本主词书，稍后仍可保留旧计划并切换。</p>
    <p className={styles.demoNote}>仅在此浏览器创建演示账户，不会发送或保存到服务器。</p>
    <form onSubmit={submit}>
      <fieldset>
        <legend>主词书</legend>
        <div className={styles.bookGrid}>
          {availableBooks.map((book) => <label className={styles.bookOption} key={book.bookId}>
            <input aria-label={book.title} checked={bookId === book.bookId} name="book" onChange={() => setBookId(book.bookId)} type="radio" value={book.bookId} />
            <span><strong>{book.title}</strong><small>{book.cardCount} 张已发布学习卡</small></span>
          </label>)}
        </div>
      </fieldset>
      <label className={styles.groupSize}>每组单词数
        <select onChange={(event) => setGroupSize(Number(event.target.value) as 10 | 20 | 30 | 50)} value={groupSize}>
          {groupSizes.map((size) => <option key={size} value={size}>{size} 词每组</option>)}
        </select>
      </label>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <Button disabled={submitting || availableBooks.length === 0} type="submit">{submitting ? "正在保存" : "完成设置"}</Button>
    </form>
  </section></main>;
}
