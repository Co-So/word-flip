import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button } from "@/components/Button/Button";
import type { AppError } from "@/data/contracts/AppError";
import { useRepositories } from "@/data/runtime/RepositoryContext";
import type { QuizScope, QuizSkill } from "@/domain/quiz";
import styles from "./quiz.module.css";

function errorMessage(error: unknown): string {
  return (error as AppError).message ?? "暂时无法创建测验";
}

export function QuizSetupPage() {
  const { quiz } = useRepositories();
  const navigate = useNavigate();
  const [scope, setScope] = useState<QuizScope>("current-plan");
  const [starting, setStarting] = useState<QuizSkill | null>(null);
  const [error, setError] = useState<string | null>(null);

  const start = async (skill: QuizSkill) => {
    setStarting(skill);
    setError(null);
    try {
      const session = await quiz.createSession(skill, scope);
      navigate(`/quiz/${session.sessionId}`);
    } catch (reason) {
      setError(errorMessage(reason));
      setStarting(null);
    }
  };

  return (
    <section className={styles.setupPage}>
      <header className={styles.setupHeader}>
        <p className={styles.eyebrow}>QUIZ WORKSPACE</p>
        <h1>选择测验方式</h1>
        <p>题目和结果来自当前学习计划的服务端演示快照。</p>
      </header>

      <fieldset className={styles.scopePicker}>
        <legend>测验范围</legend>
        <label>
          <input
            checked={scope === "current-plan"}
            name="quiz-scope"
            onChange={() => setScope("current-plan")}
            type="radio"
          />
          <span><strong>当前计划</strong><small>核心词汇 · 固定演示题</small></span>
        </label>
        <label>
          <input
            checked={scope === "due-today"}
            name="quiz-scope"
            onChange={() => setScope("due-today")}
            type="radio"
          />
          <span><strong>今日到期</strong><small>服务端预选范围 · 固定演示题</small></span>
        </label>
      </fieldset>

      <div className={styles.modeGrid}>
        <article className={styles.modeCard}>
          <span className="material-symbols-outlined" aria-hidden="true">keyboard</span>
          <p className={styles.eyebrow}>DICTATION</p>
          <h2>听写测验</h2>
          <p>根据 primary 中文义和音标输入英文，结果只写听写轨道。</p>
          <Button disabled={starting !== null} onClick={() => start("dictation")}>
            {starting === "dictation" ? "正在准备…" : "开始听写测验"}
          </Button>
        </article>
        <article className={styles.modeCard}>
          <span className="material-symbols-outlined" aria-hidden="true">checklist</span>
          <p className={styles.eyebrow}>CHOICE</p>
          <h2>选择测验</h2>
          <p>从已清洗的 primary 释义中选择答案，结果只写选择轨道。</p>
          <Button disabled={starting !== null} onClick={() => start("choice")}>
            {starting === "choice" ? "正在准备…" : "开始选择测验"}
          </Button>
        </article>
      </div>
      {error ? <p className={styles.error} role="alert">{error}</p> : null}
    </section>
  );
}
