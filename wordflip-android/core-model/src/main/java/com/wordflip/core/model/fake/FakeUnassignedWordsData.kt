package com.wordflip.core.model.fake

import com.wordflip.core.model.group.UnassignedWordsResponse
import com.wordflip.core.model.study.WordSummary

/**
 * 未入组词池 Mock；与 FakeBooksData.saveSettings / FakeGroupsData.createCustomGroup 联动。
 * 词池为已勾选词书合并去重后的子集（Mock 固定词表模拟）。
 */
object FakeUnassignedWordsData {

    /** Mock 初始已入组词数，与 FakeBooksData 汇总估算对齐 */
    private const val INITIAL_ASSIGNED_COUNT = 80

    private val allWords = buildPool().toMutableList()

    private val assignedKeys = mutableSetOf<String>()

    init {
        // 模拟已有分组占用的词
        allWords.take(INITIAL_ASSIGNED_COUNT.coerceAtMost(allWords.size))
            .forEach { assignedKeys.add(it.wordKey) }
    }

    /** 拉取未入组词列表（Mock 等价 GET /words/unassigned?all=true） */
    fun unassigned(): UnassignedWordsResponse {
        val words = allWords.filter { it.wordKey !in assignedKeys }
        return UnassignedWordsResponse(words = words, totalElements = words.size.toLong())
    }

    fun unassignedCount(): Int = allWords.count { it.wordKey !in assignedKeys }

    /** 保存设置后模拟增量入组：从未入组池头部标记 delta 个词 */
    fun markAssignedForDelta(deltaWords: Int) {
        if (deltaWords <= 0) return
        allWords
            .filter { it.wordKey !in assignedKeys }
            .take(deltaWords)
            .forEach { assignedKeys.add(it.wordKey) }
    }

    /** 自定义分组保存后标记已选词入组（REQ-CG-5） */
    fun markAssigned(wordKeys: Collection<String>) {
        wordKeys.forEach { key ->
            if (allWords.any { it.wordKey == key }) {
                assignedKeys.add(key)
            }
        }
    }

    /** 导入词书后扩展词池（新词默认未入组） */
    fun appendImportedWords(words: List<WordSummary>) {
        val existing = allWords.map { it.wordKey }.toMutableSet()
        words.forEach { word ->
            if (existing.add(word.wordKey)) {
                allWords.add(word)
            }
        }
    }

    private fun buildPool(): List<WordSummary> = listOf(
        word("abandon", "放弃；遗弃"),
        word("benefit", "利益；好处"),
        word("candidate", "候选人"),
        word("deliver", "递送；发表"),
        word("efficient", "高效的"),
        word("feature", "特征；特色"),
        word("generate", "产生；生成"),
        word("highlight", "强调；亮点"),
        word("implement", "实施；工具"),
        word("justify", "证明…正当"),
        word("maintain", "维持；保养"),
        word("negotiate", "谈判；协商"),
        word("observe", "观察；遵守"),
        word("priority", "优先；优先事项"),
        word("qualify", "使合格；限定"),
        word("relevant", "相关的"),
        word("strategy", "策略；战略"),
        word("transfer", "转移；转让"),
        word("unique", "独特的"),
        word("volunteer", "志愿者；自愿"),
        word("welfare", "福利；幸福"),
        word("yield", "产生；屈服"),
        word("zealous", "热情的"),
        word("abstract", "抽象的；摘要"),
        word("boundary", "边界；界限"),
        word("capacity", "容量；能力"),
        word("diverse", "多样的"),
        word("enhance", "增强；提高"),
        word("foundation", "基础；基金会"),
        word("genuine", "真正的；真诚的"),
        word("harmony", "和谐；协调"),
        word("integrity", "正直；完整"),
        word("journey", "旅程；旅行"),
        word("knowledge", "知识；学问"),
        word("landscape", "风景；景观"),
        word("momentum", "势头；动力"),
        word("network", "网络；关系网"),
        word("objective", "目标；客观的"),
        word("perspective", "观点；透视"),
        word("quality", "质量；品质"),
        word("resource", "资源；财力"),
        word("structure", "结构；构造"),
        word("tradition", "传统；惯例"),
        word("ultimate", "最终的；终极的"),
        word("variable", "变量；可变的"),
        word("wisdom", "智慧；学问"),
        word("xenial", "好客的"),
        word("youthful", "年轻的"),
        word("zealot", "狂热者"),
        word("amplify", "放大；增强"),
    )

    private fun word(en: String, cn: String): WordSummary {
        val key = en.trim().lowercase()
        return WordSummary(wordKey = key, en = en, cn = cn)
    }
}
