import { useState, type FormEvent } from "react";
import { Button } from "@/components/Button/Button";
import type { QuizQuestion } from "@/domain/quiz";
import styles from "./quiz.module.css";

interface DictationQuestionProps {
  question: QuizQuestion;
  disabled: boolean;
  onSubmit: (answer: string) => void;
}

export function DictationQuestion({ question, disabled, onSubmit }: DictationQuestionProps) {
  const [answer, setAnswer] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!disabled && answer.trim()) {
      onSubmit(answer);
    }
  };

  return (
    <form className={styles.questionForm} onSubmit={submit}>
      <p className={styles.promptLabel}>根据释义写出英文</p>
      <h2 className={styles.prompt}>{question.prompt}</h2>
      <p className={styles.hint}>{question.hint}</p>
      <label className={styles.answerField}>
        输入英文单词
        <input
          autoComplete="off"
          disabled={disabled}
          onChange={(event) => setAnswer(event.target.value)}
          spellCheck={false}
          value={answer}
        />
      </label>
      <Button disabled={disabled || !answer.trim()} type="submit">
        {disabled ? "正在提交…" : "提交答案"}
      </Button>
    </form>
  );
}
