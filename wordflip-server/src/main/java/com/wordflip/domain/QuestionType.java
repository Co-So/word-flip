package com.wordflip.domain;

/**
 * 测验题目类型；choice_* 共用 Skill.choice。
 */
public enum QuestionType {
    dictation,
    choice_en_cn,
    choice_cn_en;

    public Skill toSkill() {
        return this == dictation ? Skill.dictation : Skill.choice;
    }
}
