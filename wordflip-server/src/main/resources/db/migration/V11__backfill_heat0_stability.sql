-- 回填：已有测验史但仍停在「新词」档（S<10）的进度抬到初识下限 12
-- 对齐 StabilityCalculator.INITIAL_CORRECT_STABILITY（修复 gap=0 只涨 0.05 的历史脏数据）
UPDATE word_skill_progress
SET
    stability = 12.00,
    window_correct_gain = 1.00,
    updated_at = CURRENT_TIMESTAMP(3)
WHERE has_quiz_history = 1
  AND stability < 10.00;
