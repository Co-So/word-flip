import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import { AuthShell } from "@/layouts/AuthShell/AuthShell";
import styles from "./auth.module.css";

function messageOf(error: unknown): string {
  return (error as AppError).message ?? "登录暂时无法完成";
}

export function LoginPage() {
  const { auth } = useRepositories();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await auth.signIn({ email, password });
      navigate("/today", { replace: true });
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return <AuthShell><div className={styles.flow}>
    <p className={styles.eyebrow}>WORDFLIP · WEB DEMO</p>
    <h1>登录 WordFlip</h1>
    <p>继续你的单词学习节奏。</p>
    <form onSubmit={submit}>
      <label>邮箱<input autoComplete="email" onChange={(event) => setEmail(event.target.value)} required type="email" value={email} /></label>
      <label>密码<input autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} required type="password" value={password} /></label>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <Button disabled={submitting} type="submit">{submitting ? "正在登录" : "登录"}</Button>
    </form>
    <p className={styles.footer}>还没有账户？<Link to="/register">创建演示账户</Link></p>
  </div></AuthShell>;
}
