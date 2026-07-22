package com.wordflip.feature.study

/** 学习页三种纯展示模式；不改变卡片顺序与业务状态。 */
enum class StudyViewMode(
    val storageValue: String,
    val label: String,
) {
    FOCUS("focus", "专注卡组"),
    GRID("grid", "卡片墙"),
    HYBRID("hybrid", "焦点与缩略轨道"),
    ;

    companion object {
        /** 未保存或保存值失效时使用已确认的混合模式。 */
        fun fromStorage(raw: String?): StudyViewMode =
            entries.firstOrNull { it.storageValue == raw } ?: HYBRID
    }
}
