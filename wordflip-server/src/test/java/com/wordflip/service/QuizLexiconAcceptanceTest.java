package com.wordflip.service;

import com.wordflip.domain.QuestionType;
import com.wordflip.dto.quiz.QuizOptionDto;
import com.wordflip.dto.word.ExampleDto;
import com.wordflip.dto.word.SenseDto;
import com.wordflip.dto.word.WordSummary;
import com.wordflip.util.WordSenseNormalizer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P-LEX-T01 自动化验收：dict primary 出题无脏选项；reject 不出题；多义 senses 保留。
 */
class QuizLexiconAcceptanceTest {

    /** 模拟 ECDICT 风格的 50 个合格词（primary ok） */
    private static final List<WordSummary> DICT_POOL = buildDictPool(50);

    @Test
    void fiftyDictPrimaries_choiceLabelsAreCleanChinese() throws Exception {
        for (WordSummary summary : DICT_POOL) {
            assertThat(WordSenseNormalizer.isQuizEligible(summary))
                    .as(summary.wordKey())
                    .isTrue();
            assertThat(WordSenseNormalizer.hasDictPrimaryOk(summary)).isTrue();
            // dict 路径不再二次清洗
            assertThat(WordSenseNormalizer.normalizeSummary(summary)).isSameAs(summary);

            QuizOptionDto enCn = invokeToOption(summary.wordKey(), summary, QuestionType.choice_en_cn);
            assertThat(enCn.label())
                    .as(summary.wordKey() + " en_cn")
                    .isNotEqualTo("（无释义）")
                    .doesNotContain("(n.)", "(v.)", "(adj.)", "(adv.)", "(prep.)");
            assertThat(WordSenseNormalizer.hasHan(enCn.label())).isTrue();
            // 不得把英文词头当中文选项
            assertThat(enCn.label().equalsIgnoreCase(summary.wordKey())).isFalse();

            QuizOptionDto cnEn = invokeToOption(summary.wordKey(), summary, QuestionType.choice_cn_en);
            assertThat(cnEn.label()).isEqualTo(summary.en());
        }
    }

    @Test
    void rejectPrimary_notQuizEligible() {
        SenseDto reject = new SenseDto(1L, "n.", "坏数据", true, "reject", 0, List.of());
        WordSummary summary = new WordSummary("junk", "junk", "坏数据", "n.", null, List.of(reject));
        assertThat(WordSenseNormalizer.hasDictPrimaryOk(summary)).isFalse();
        assertThat(WordSenseNormalizer.isQuizEligible(summary)).isFalse();
    }

    @Test
    void multiSense_preservesExamplesPerSense() {
        SenseDto s1 = new SenseDto(
                1L, "v.", "实施；执行", true, "ok", 0,
                List.of(new ExampleDto("Implement the policy.", "实施该政策。", 0))
        );
        SenseDto s2 = new SenseDto(
                2L, "n.", "工具；器具", false, "ok", 1,
                List.of(new ExampleDto("farming implements", "农具", 0))
        );
        WordSummary summary = new WordSummary(
                "implement", "implement", "实施；执行", "v.", null, List.of(s1, s2));

        assertThat(summary.senses()).hasSize(2);
        assertThat(summary.senses().get(0).examples().getFirst().en()).contains("policy");
        assertThat(summary.senses().get(1).examples().getFirst().cn()).isEqualTo("农具");
        // 顶层 cn 仍为 primary，卡片背面只用它
        assertThat(summary.cn()).isEqualTo("实施；执行");
        assertThat(WordSenseNormalizer.displayCn(summary)).isEqualTo("实施；执行");
    }

    /** 虚词黄金样例：测验展示义须为学习向（非 prep「除了」/conj「但是,不过」挂到 only） */
    @Test
    void functionWordPrimaries_areLearningSenses() {
        WordSummary only = summary("only", "adv.", "只有, 仅仅, 只能");
        WordSummary but = summary("but", "conj.", "但是");
        WordSummary just = summary("just", "adv.", "刚刚, 正好, 仅仅");
        WordSummary go = summary("go", "vi.", "去, 走");

        assertThat(WordSenseNormalizer.isQuizEligible(only)).isTrue();
        assertThat(only.cn()).contains("只有").doesNotContain("不过");
        assertThat(but.cn()).isEqualTo("但是");
        assertThat(just.cn()).contains("刚刚");
        assertThat(go.cn()).contains("去");
    }

    private static WordSummary summary(String key, String pos, String cn) {
        return new WordSummary(
                key, key, cn, pos, null,
                List.of(new SenseDto(1L, pos, cn, true, "ok", 0, List.of()))
        );
    }

    @Test
    void wordnetPrimary_quizEligibleViaEnGloss() {
        SenseDto primary = new SenseDto(
                1L, "v.", null, "change location; move", true, "ok", 0, List.of());
        WordSummary summary = new WordSummary(
                "go", "go", null, "v.", null, "change location; move", List.of(primary));
        assertThat(WordSenseNormalizer.hasDictPrimaryOk(summary)).isTrue();
        assertThat(WordSenseNormalizer.isQuizEligible(summary)).isTrue();
        assertThat(WordSenseNormalizer.displayPrompt(summary)).contains("change location");
    }

    @Test
    void legacyDirtyCn_stillCleanedForQuiz() throws Exception {
        WordSummary dirty = new WordSummary("time", "time", "时间 (n.)", "n.", null);
        WordSummary cleaned = WordSenseNormalizer.normalizeSummary(dirty);
        assertThat(cleaned.cn()).isEqualTo("时间");
        QuizOptionDto option = invokeToOption("time", cleaned, QuestionType.choice_en_cn);
        assertThat(option.label()).isEqualTo("时间");
    }

    private static List<WordSummary> buildDictPool(int n) {
        String[][] samples = {
                {"be", "be", "是, 表示, 在", "v."},
                {"have", "have", "有, 吃, 喝", "v."},
                {"to", "to", "到, 向, 给", "prep."},
                {"on", "on", "在…上", "prep."},
                {"a", "a", "一个", "art."},
                {"time", "time", "时间", "n."},
                {"make", "make", "做, 制造", "v."},
                {"good", "good", "好的", "adj."},
                {"go", "go", "去, 走", "v."},
                {"see", "see", "看见", "v."},
        };
        List<WordSummary> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String[] s = samples[i % samples.length];
            String key = i < samples.length ? s[0] : s[0] + i;
            String en = i < samples.length ? s[1] : s[1] + i;
            SenseDto primary = new SenseDto(null, s[3], s[2], true, "ok", 0, List.of());
            list.add(new WordSummary(key, en, s[2], s[3], null, List.of(primary)));
        }
        return list;
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
