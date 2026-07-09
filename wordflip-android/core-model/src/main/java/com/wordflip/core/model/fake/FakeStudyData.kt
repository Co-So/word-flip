package com.wordflip.core.model.fake

import com.wordflip.core.model.group.GroupWordItem
import com.wordflip.core.model.study.MasteryLevel
import com.wordflip.core.model.study.MasterySnapshot
import com.wordflip.core.model.study.StudyGroupInfo
import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordDetail
import com.wordflip.core.model.media.StainGenerator
import com.wordflip.core.model.study.WordProgressSnapshot
import com.wordflip.core.model.study.WordStainPayload
import com.wordflip.core.model.study.WordSummary

/**
 * 学习页 Mock 数据；groupId=3 与 FakeTodayData 推荐分组一致，共 20 词。
 */
object FakeStudyData {

    private val rawWords = listOf(
        word("abandon", "abandon", "放弃；遗弃", "v.", "/əˈbændən/", "放弃", "He abandoned the plan.", "a- + bandon 控制"),
        word("benefit", "benefit", "利益；好处", "n.", "/ˈbenɪfɪt/", "益处", "Exercise benefits your health.", "bene- 好 + fit"),
        word("candidate", "candidate", "候选人", "n.", "/ˈkændɪdeɪt/", "候选者", "She is a strong candidate.", "candid 白 + -ate"),
        word("deliver", "deliver", "递送；发表", "v.", "/dɪˈlɪvər/", "交付", "Deliver the package today.", "de- + liver 释放"),
        word("efficient", "efficient", "高效的", "adj.", "/ɪˈfɪʃnt/", "有效率的", "An efficient workflow saves time.", "ef- + fic 做"),
        word("feature", "feature", "特征；特色", "n.", "/ˈfiːtʃər/", "特点", "This app has a new feature.", "fact 做 + -ure"),
        word("generate", "generate", "产生；生成", "v.", "/ˈdʒenəreɪt/", "生成", "Wind generates electricity.", "gen 生 + -ate"),
        word("highlight", "highlight", "强调；亮点", "v.", "/ˈhaɪlaɪt/", "突出", "Highlight the key points.", "high + light"),
        word("implement", "implement", "实施；工具", "v.", "/ˈɪmplɪment/", "执行", "Implement the new policy.", "im- + ple 满"),
        word("justify", "justify", "证明…正当", "v.", "/ˈdʒʌstɪfaɪ/", "辩解", "Can you justify your choice?", "just + -ify"),
        word("maintain", "maintain", "维持；保养", "v.", "/meɪnˈteɪn/", "保持", "Maintain a healthy diet.", "main + tain 持有"),
        word("negotiate", "negotiate", "谈判；协商", "v.", "/nɪˈɡoʊʃieɪt/", "洽谈", "They negotiated a deal.", "neg 否 + ot"),
        word("observe", "observe", "观察；遵守", "v.", "/əbˈzɜːrv/", "观测", "Observe the experiment carefully.", "ob- + serve 保持"),
        word("priority", "priority", "优先；优先事项", "n.", "/praɪˈɔːrəti/", "首要", "Safety is our top priority.", "prior 前 + -ity"),
        word("qualify", "qualify", "使合格；限定", "v.", "/ˈkwɑːlɪfaɪ/", "取得资格", "She qualified for the finals.", "qual 质 + -ify"),
        word("relevant", "relevant", "相关的", "adj.", "/ˈreləvənt/", "有关", "Provide relevant examples.", "re- + lev 举"),
        word("strategy", "strategy", "策略；战略", "n.", "/ˈstrætədʒi/", "方略", "Develop a learning strategy.", "strat 层 + -egy"),
        word("transfer", "transfer", "转移；转让", "v.", "/trænsˈfɜːr/", "转移", "Transfer the files to cloud.", "trans- + fer 带"),
        word("unique", "unique", "独特的", "adj.", "/juˈniːk/", "唯一", "Each word has a unique key.", "uni 一 + -que"),
        word("volunteer", "volunteer", "志愿者；自愿", "n.", "/ˌvɑːlənˈtɪr/", "义工", "She works as a volunteer.", "volunt 意愿 + -eer"),
    )

    fun forGroup(groupId: Int): StudyGroupPayload? {
        if (groupId != 3) {
            return StudyGroupPayload(
                group = StudyGroupInfo(id = groupId, name = "第${groupId}组"),
                words = rawWords.take(10),
            )
        }
        return StudyGroupPayload(
            group = StudyGroupInfo(id = 3, name = "第3组", source = "auto"),
            words = rawWords,
        )
    }

    /**
     * 分组详情列表 Mock：混合掌握度仅供只读展示，不写本地态（掌握度仅测验写入）。
     */
    fun wordsForGroupDetail(groupId: Int): List<GroupWordItem> {
        val payload = forGroup(groupId) ?: return emptyList()
        return payload.words.mapIndexed { index, card ->
            val mastery = detailMasteryFor(groupId, index)
            GroupWordItem(
                summary = WordSummary(
                    wordKey = card.wordKey,
                    en = card.en,
                    cn = card.cn,
                    pos = card.pos,
                    ph = card.ph,
                ),
                mastery = mastery,
                progress = fakeProgressFor(mastery, index),
            )
        }
    }

    /** Mock 双轨进度：choice 略弱于 dictation，展示热力取较低档 */
    private fun fakeProgressFor(dictation: MasterySnapshot, index: Int): WordProgressSnapshot {
        val choice = when (index % 3) {
            0 -> dictation.copy(skill = "choice")
            1 -> MasterySnapshot(
                level = MasteryLevel.FUZZY,
                hasQuizHistory = true,
                stage = 1,
                stability = 15.0,
                heatLevel = 1,
                skill = "choice",
            )
            else -> MasterySnapshot(
                level = MasteryLevel.UNLEARNED,
                hasQuizHistory = false,
                skill = "choice",
            )
        }
        val displayHeat = minOf(dictation.heatLevel, choice.heatLevel)
        val displayStability = minOf(dictation.stability, choice.stability)
        return WordProgressSnapshot(
            dictation = dictation.copy(skill = "dictation"),
            choice = choice,
            displayHeatLevel = displayHeat,
            displayStability = displayStability,
            heatDisplayMode = "combined",
        )
    }

    /** 按组与序号注入展示用热力 + 三态分布 */
    private fun detailMasteryFor(groupId: Int, index: Int): MasterySnapshot {
        return when (groupId) {
            1 -> when (index % 5) {
                0, 1 -> MasterySnapshot(
                    MasteryLevel.UNLEARNED, hasQuizHistory = true, stage = 3,
                    stability = 45.0, heatLevel = 2,
                )
                2 -> MasterySnapshot(
                    MasteryLevel.FUZZY, hasQuizHistory = true, stage = 1,
                    stability = 18.0, heatLevel = 1,
                )
                else -> MasterySnapshot(
                    MasteryLevel.UNLEARNED, hasQuizHistory = true, stage = 5,
                    stability = 82.0, heatLevel = 4,
                )
            }
            2 -> when (index % 4) {
                0 -> MasterySnapshot(
                    MasteryLevel.UNLEARNED, hasQuizHistory = true, stage = 2,
                    stability = 35.0, heatLevel = 2,
                )
                1 -> MasterySnapshot(
                    MasteryLevel.FUZZY, hasQuizHistory = true, stage = 1,
                    stability = 12.0, heatLevel = 1,
                )
                2 -> MasterySnapshot(
                    MasteryLevel.UNKNOWN, hasQuizHistory = true, stage = 0,
                    stability = 5.0, heatLevel = 0,
                )
                else -> MasterySnapshot(MasteryLevel.UNLEARNED, hasQuizHistory = false)
            }
            5, 6 -> when (index % 3) {
                0 -> MasterySnapshot(
                    MasteryLevel.UNLEARNED, hasQuizHistory = true, stage = 2,
                    stability = 28.0, heatLevel = 1,
                )
                1 -> MasterySnapshot(
                    MasteryLevel.FUZZY, hasQuizHistory = true, stage = 1,
                    stability = 15.0, heatLevel = 1,
                )
                else -> MasterySnapshot(
                    MasteryLevel.UNKNOWN, hasQuizHistory = true, stage = 0,
                    stability = 3.0, heatLevel = 0,
                )
            }
            else -> MasterySnapshot(MasteryLevel.UNLEARNED, hasQuizHistory = false)
        }
    }

    private fun word(
        wordKey: String,
        en: String,
        cn: String,
        pos: String,
        ph: String,
        meaning: String,
        example: String,
        etymology: String,
    ): WordCard {
        val seed = StainGenerator.stableSeed(wordKey)
        val stainConfig = StainGenerator.generate(wordKey, overrideSeed = seed)
        return WordCard(
            wordKey = wordKey,
            en = en,
            cn = cn,
            pos = pos,
            ph = ph,
            mastery = MasterySnapshot(
                level = MasteryLevel.UNLEARNED,
                hasQuizHistory = false,
            ),
            detail = WordDetail(
                meaning = meaning,
                examples = listOf(example, "暂无更多例句"),
                etymology = etymology,
            ),
            stain = WordStainPayload(hidden = false, seed = seed, config = stainConfig),
        )
    }
}
