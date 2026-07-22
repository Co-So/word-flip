package com.wordflip.core.model.fake

import com.wordflip.core.model.group.GroupWordItem
import com.wordflip.core.model.media.StainGenerator
import com.wordflip.core.model.study.CardProgress
import com.wordflip.core.model.study.Example
import com.wordflip.core.model.study.FsrsMemory
import com.wordflip.core.model.study.Sense
import com.wordflip.core.model.study.StudyGroupInfo
import com.wordflip.core.model.study.StudyGroupPayload
import com.wordflip.core.model.study.WordCard
import com.wordflip.core.model.study.WordDetail
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
        word("implement", "implement", "实施；工具", "v.", "/ˈɪmplɪment/", "执行", "Implement the new policy.", "im- + ple 满",
            senses = listOf(
                Sense(
                    pos = "v.",
                    cn = "实施；执行",
                    primary = true,
                    sortOrder = 0,
                    examples = listOf(
                        Example(en = "Implement the new policy.", cn = "实施新政策。"),
                    ),
                ),
                Sense(
                    pos = "n.",
                    cn = "工具；器具",
                    primary = false,
                    sortOrder = 1,
                    examples = listOf(
                        Example(en = "farming implements", cn = "农具"),
                    ),
                ),
            ),
        ),
        word("justify", "justify", "证明…正当", "v.", "/ˈdʒʌstɪfaɪ/", "辩解", "Can you justify your choice?", "just + -ify"),
        word("maintain", "maintain", "维持；保养", "v.", "/meɪnˈteɪn/", "保持", "Maintain a healthy diet.", "main + tain 持有"),
        word("negotiate", "negotiate", "谈判；协商", "v.", "/nɪˈɡoʊʃieɪt/", "洽谈", "They negotiated a deal.", "neg 否 + ot"),
        word("observe", "observe", "观察；遵守", "v.", "/əbˈzɜːrv/", "观测", "Observe the experiment carefully.", "ob- + serve 保持",
            senses = listOf(
                Sense(
                    pos = "v.",
                    cn = "观察；观测",
                    primary = true,
                    sortOrder = 0,
                    examples = listOf(
                        Example(en = "Observe the experiment carefully.", cn = "仔细观察实验。"),
                    ),
                ),
                Sense(
                    pos = "v.",
                    cn = "遵守；奉行",
                    primary = false,
                    sortOrder = 1,
                    examples = listOf(
                        Example(en = "Observe the speed limit.", cn = "遵守限速。"),
                    ),
                ),
            ),
        ),
        word("priority", "priority", "优先；优先事项", "n.", "/praɪˈɔːrəti/", "首要", "Safety is our top priority.", "prior 前 + -ity"),
        word("qualify", "qualify", "使合格；限定", "v.", "/ˈkwɑːlɪfaɪ/", "取得资格", "She qualified for the finals.", "qual 质 + -ify"),
        word("relevant", "relevant", "相关的", "adj.", "/ˈreləvənt/", "有关", "Provide relevant examples.", "re- + lev 举"),
        word("strategy", "strategy", "策略；战略", "n.", "/ˈstrætədʒi/", "方略", "Develop a learning strategy.", "strat 层 + -egy"),
        word("transfer", "transfer", "转移；转让", "v.", "/trænsˈfɜːr/", "转移", "Transfer the files to cloud.", "trans- + fer 带",
            senses = listOf(
                Sense(
                    pos = "v.",
                    cn = "转移；调动",
                    primary = true,
                    sortOrder = 0,
                    examples = listOf(
                        Example(en = "Transfer the files to cloud.", cn = "把文件转到云端。"),
                    ),
                ),
                Sense(
                    pos = "n.",
                    cn = "转移；换乘",
                    primary = false,
                    sortOrder = 1,
                    examples = listOf(
                        Example(en = "a bus transfer", cn = "公交换乘"),
                    ),
                ),
            ),
        ),
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

    /** 分组详情 Mock 仅携带服务端形状的 FSRS 展示数据。 */
    fun wordsForGroupDetail(groupId: Int): List<GroupWordItem> {
        val payload = forGroup(groupId) ?: return emptyList()
        return payload.words.mapIndexed { index, card ->
            GroupWordItem(
                summary = WordSummary(
                    wordKey = card.wordKey,
                    en = card.en,
                    cn = card.cn,
                    pos = card.pos,
                    ph = card.ph,
                    senses = card.senses,
                ),
                progress = card.progress ?: fakeProgress(index),
            )
        }
    }

    private fun fakeProgress(index: Int): CardProgress {
        val memory = FsrsMemory("new", "2026-07-17T00:00:00Z", 0.0, 0.0, 0, 0)
        return CardProgress(memory, memory, index % 5)
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
        senses: List<Sense> = emptyList(),
    ): WordCard {
        val seed = StainGenerator.stableSeed(wordKey)
        val stainConfig = StainGenerator.generate(wordKey, overrideSeed = seed)
        val resolvedSenses = senses.ifEmpty {
            listOf(
                Sense(
                    pos = pos,
                    cn = cn,
                    primary = true,
                    sortOrder = 0,
                    examples = listOf(Example(en = example)),
                ),
            )
        }
        return WordCard(
            wordKey = wordKey,
            en = en,
            cn = cn,
            pos = pos,
            ph = ph,
            senses = resolvedSenses,
            detail = WordDetail(
                meaning = meaning,
                examples = listOf(example, "暂无更多例句"),
                etymology = etymology,
            ),
            stain = WordStainPayload(hidden = false, seed = seed, config = stainConfig),
        )
    }
}
