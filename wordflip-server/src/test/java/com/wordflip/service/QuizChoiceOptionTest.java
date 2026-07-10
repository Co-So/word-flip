package com.wordflip.service;

import com.wordflip.domain.QuestionType;
import com.wordflip.dto.quiz.QuizOptionDto;
import com.wordflip.dto.word.WordSummary;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 选择题选项语言：英选中→中文 label；中选英→英文 label（REQ-QUIZ-11）。
 */
class QuizChoiceOptionTest {

    @Test
    void choiceEnCn_usesChineseLabel() throws Exception {
        QuizOptionDto option = invokeToOption(
                "time",
                new WordSummary("time", "time", "时间 (n.)", "n.", null),
                QuestionType.choice_en_cn
        );
        assertThat(option.key()).isEqualTo("time");
        assertThat(option.label()).isEqualTo("时间");
    }

    @Test
    void choiceCnEn_usesEnglishLabel() throws Exception {
        QuizOptionDto option = invokeToOption(
                "time",
                new WordSummary("time", "time", "时间 (n.)", "n.", null),
                QuestionType.choice_cn_en
        );
        assertThat(option.key()).isEqualTo("time");
        assertThat(option.label()).isEqualTo("time");
    }

    @Test
    void choiceEnCn_missingCn_doesNotFallbackToWordKey() throws Exception {
        QuizOptionDto option = invokeToOption(
                "time",
                new WordSummary("time", "time", "", null, null),
                QuestionType.choice_en_cn
        );
        assertThat(option.label()).isEqualTo("（无释义）");
    }

    private static QuizOptionDto invokeToOption(
            String wordKey,
            WordSummary summary,
            QuestionType type
    ) throws Exception {
        Method method = QuizService.class.getDeclaredMethod(
                "toOption",
                String.class,
                WordSummary.class,
                QuestionType.class
        );
        method.setAccessible(true);
        return (QuizOptionDto) method.invoke(null, wordKey, summary, type);
    }
}
