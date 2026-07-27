import { useState, type FormEvent } from "react";
import { Button } from "@/components/Button/Button";
import type { QuizQuestion } from "@/domain/quiz";
import styles from "./quiz.module.css";

interface ChoiceQuestionProps {
  question: QuizQuestion;
  disabled: boolean;
  onSubmit: (answer: string) => void;
}

export function ChoiceQuestion({ question, disabled, onSubmit }: ChoiceQuestionProps) {
  const [selected, setSelected] = useState("");

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!disabled && selected) {
      onSubmit(selected);
    }
  };

  return (
    <form className={styles.questionForm} onSubmit={submit}>
      <p className={styles.promptLabel}>选择正确的 primary 释义</p>
      <h2 className={styles.prompt}>{question.prompt}</h2>
      <p className={styles.hint}>{question.hint}</p>
      <fieldset
        aria-label="选择正确释义"
        className={styles.choiceList}
        disabled={disabled}
        role="radiogroup"
      >
        <legend className={styles.srOnly}>选择正确释义</legend>
        {question.options?.map((option) => (
          <label key={option.key}>
            <input
              checked={selected === option.key}
              name="quiz-choice"
              onChange={() => setSelected(option.key)}
              type="radio"
              value={option.key}
            />
            <span>{option.label}</span>
          </label>
        ))}
      </fieldset>
      <Button disabled={disabled || !selected} type="submit">
        {disabled ? "正在提交…" : "提交答案"}
      </Button>
    </form>
  );
}
