package com.wordflip.dto.word;

import com.wordflip.domain.HeatDisplayMode;
import com.wordflip.domain.MasteryLevel;
import com.wordflip.domain.Skill;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 综合热力聚合：未测 skill 不参与木桶 min。
 */
class WordProgressSnapshotTest {

    @Test
    void combined_ignoresUntestedSkill() {
        MasterySnapshot dictation = new MasterySnapshot(
                MasteryLevel.unlearned, true, 1, null, 12.0, 1, Skill.dictation);
        MasterySnapshot choice = MasterySnapshot.unlearnedDefault(Skill.choice);

        WordProgressSnapshot snap = WordProgressSnapshot.of(dictation, choice, HeatDisplayMode.combined);

        assertThat(snap.displayHeatLevel()).isEqualTo(1);
        assertThat(snap.displayStability()).isEqualTo(12.0);
    }

    @Test
    void combined_usesMinWhenBothHaveHistory() {
        MasterySnapshot dictation = new MasterySnapshot(
                MasteryLevel.unlearned, true, 2, null, 40.0, 2, Skill.dictation);
        MasterySnapshot choice = new MasterySnapshot(
                MasteryLevel.unlearned, true, 1, null, 12.0, 1, Skill.choice);

        WordProgressSnapshot snap = WordProgressSnapshot.of(dictation, choice, HeatDisplayMode.combined);

        assertThat(snap.displayHeatLevel()).isEqualTo(1);
        assertThat(snap.displayStability()).isEqualTo(12.0);
    }

    @Test
    void combined_bothUntested_staysZero() {
        WordProgressSnapshot snap = WordProgressSnapshot.empty(HeatDisplayMode.combined);
        assertThat(snap.displayHeatLevel()).isEqualTo(0);
        assertThat(snap.displayStability()).isEqualTo(0.0);
    }
}
