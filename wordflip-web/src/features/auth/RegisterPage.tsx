import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import { AuthShell } from "@/layouts/AuthShell/AuthShell";
import styles from "./auth.module.css";

function messageOf(error: unknown): string {
  return (error as AppError).message ?? "创建账户暂时无法完成";
}

export function RegisterPage() {
  const { auth } = useRepositories();
  const navigate = useNavigate();
  const [account, setAccount] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await auth.register({ account, password });
      navigate("/today", { replace: true });
    } catch (reason) {
      setError(messageOf(reason));
    } finally {
      setSubmitting(false);
    }
  }

  return <AuthShell><div className={styles.flow}>
    <p className={styles.eyebrow}>WORDFLIP · STUDY DESK</p>
    <h1>创建账户</h1>
    <p>使用邮箱或手机号开始学习。</p>
    <form onSubmit={submit}>
      <label>邮箱或手机号<input autoComplete="username" onChange={(event) => setAccount(event.target.value)} required value={account} /></label>
      <label>密码<input autoComplete="new-password" minLength={8} onChange={(event) => setPassword(event.target.value)} required type="password" value={password} /></label>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      <Button disabled={submitting} type="submit">{submitting ? "正在创建" : "创建账户"}</Button>
    </form>
    <p className={styles.footer}>已有账户？<Link to="/login">返回登录</Link></p>
  </div></AuthShell>;
}
