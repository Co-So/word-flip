package com.wordflip.util;

import com.wordflip.dto.word.SenseDto;
import com.wordflip.dto.word.WordSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 词条释义清洗与测验可用性（REQ-QUIZ-11 题干/选项语言分离）。
 */
class WordSenseNormalizerTest {

    @Test
    void cleanDisplayCn_stripsTrailingPos() {
        assertThat(WordSenseNormalizer.cleanDisplayCn("突然地 (adv.)")).isEqualTo("突然地");
        assertThat(WordSenseNormalizer.cleanDisplayCn("吸收（液体、气体等） (v.)")).isEqualTo("吸收（液体、气体等）");
    }

    @Test
    void cleanDisplayCn_stripsEnglishPhraseHead() {
        assertThat(WordSenseNormalizer.cleanDisplayCn("favour (prep.)；为收款人 (of赞成，以.)"))
                .isEqualTo("为收款人");
        assertThat(WordSenseNormalizer.cleanDisplayCn("without (v.)；的情况下勉强对付 (sth在缺少.)"))
                .contains("情况下");
    }

    @Test
    void cleanDisplayCn_stripsLeadingPosAbbr() {
        assertThat(WordSenseNormalizer.cleanDisplayCn("n. (n.)；估计，估价 (n.)；评价，估量 (v.)"))
                .startsWith("估计");
        assertThat(WordSenseNormalizer.cleanDisplayCn("vi大声哀号，恸哭；呼啸，尖啸 (v.)"))
                .startsWith("大声哀号");
    }

    @Test
    void isQuizEligible_requiresHanCn() {
        WordSummary ok = new WordSummary("time", "time", "时间 (n.)", "n.", null);
        assertThat(WordSenseNormalizer.isQuizEligible(ok)).isTrue();

        WordSummary empty = new WordSummary("time", "time", "", null, null);
        assertThat(WordSenseNormalizer.isQuizEligible(empty)).isFalse();

        WordSummary debris = new WordSummary("in", "in", "favour (prep.)；为收款人 (of赞成，以.)", "prep.", null);
        // 虚词 + 英文头碎片：即使洗出汉字也不宜出题
        assertThat(WordSenseNormalizer.isQuizEligible(debris)).isFalse();
    }

    @Test
    void posFamily_groupsVariants() {
        assertThat(WordSenseNormalizer.posFamily("n.")).isEqualTo("n");
        assertThat(WordSenseNormalizer.posFamily("n&vt.")).isEqualTo("v");
        assertThat(WordSenseNormalizer.posFamily("adj.")).isEqualTo("adj");
        assertThat(WordSenseNormalizer.posFamily("adv.")).isEqualTo("adv");
    }

    @Test
    void normalizeSummary_rewritesCn() {
        WordSummary raw = new WordSummary("abruptly", "abruptly", "突然地 (adv.)", "adv.", "/x/");
        WordSummary cleaned = WordSenseNormalizer.normalizeSummary(raw);
        assertThat(cleaned.cn()).isEqualTo("突然地");
        assertThat(cleaned.pos()).isEqualTo("adv.");
    }

    @Test
    void normalizeSummary_preservesSenses() {
        var sense = new SenseDto(1L, "v.", "是, 表示, 在", true, "ok", 0, List.of());
        WordSummary raw = new WordSummary("be", "be", "是, 表示, 在 (v.)", "v.", null, List.of(sense));
        // dict primary ok：不再二次清洗顶层 cn（即使带 (v.) 尾巴也不动）
        WordSummary cleaned = WordSenseNormalizer.normalizeSummary(raw);
        assertThat(cleaned).isSameAs(raw);
        assertThat(cleaned.senses()).hasSize(1);
        assertThat(cleaned.senses().getFirst().id()).isEqualTo(1L);
    }

    @Test
    void displayCn_dictSkipsRegex_legacyCleans() {
        var sense = new SenseDto(1L, "n.", "时间", true, "ok", 0, List.of());
        WordSummary dict = new WordSummary("time", "time", "时间", "n.", null, List.of(sense));
        assertThat(WordSenseNormalizer.displayCn(dict)).isEqualTo("时间");
        assertThat(WordSenseNormalizer.hasDictPrimaryOk(dict)).isTrue();

        WordSummary legacy = new WordSummary("time", "time", "时间 (n.)", "n.", null);
        assertThat(WordSenseNormalizer.displayCn(legacy)).isEqualTo("时间");
        assertThat(WordSenseNormalizer.hasDictPrimaryOk(legacy)).isFalse();
    }

    @Test
    void isQuizEligible_withSenses_requiresPrimaryOk() {
        var ok = new SenseDto(1L, "n.", "时间", true, "ok", 0, List.of());
        assertThat(WordSenseNormalizer.isQuizEligible(
                new WordSummary("time", "time", "时间", "n.", null, List.of(ok)))).isTrue();

        var reject = new SenseDto(2L, "n.", "坏", true, "reject", 0, List.of());
        assertThat(WordSenseNormalizer.isQuizEligible(
                new WordSummary("junk", "junk", "坏", "n.", null, List.of(reject)))).isFalse();
    }
}
