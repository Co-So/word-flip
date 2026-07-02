package com.wordflip.core.ui.component

import com.wordflip.core.model.study.MasteryLevel

/** 掌握度枚举映射，供分组详情只读 Chip 使用 */
fun MasteryLevel.toChipLevel(): MasteryChipLevel {
    return when (this) {
        MasteryLevel.UNLEARNED -> MasteryChipLevel.UNLEARNED
        MasteryLevel.FUZZY -> MasteryChipLevel.FUZZY
        MasteryLevel.UNKNOWN -> MasteryChipLevel.UNKNOWN
    }
}
