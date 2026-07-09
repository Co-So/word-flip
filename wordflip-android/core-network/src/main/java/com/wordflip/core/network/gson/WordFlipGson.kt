package com.wordflip.core.network.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.model.book.GroupStrategy
import com.wordflip.core.model.book.parseGroupStrategy
import com.wordflip.core.model.book.storageValue
import com.wordflip.core.model.group.GroupSource
import com.wordflip.core.model.group.GroupStatus
import com.wordflip.core.model.media.StainMode
import com.wordflip.core.model.media.StainType
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.parseThemeMode
import com.wordflip.core.model.settings.storageValue
import com.wordflip.core.model.study.MasteryLevel
import com.wordflip.core.model.today.StudyReason

/**
 * Gson 小写 enum 适配（P0-A1B）：服务端 JSON 使用 builtin/imported、auto/custom 等 snake_case。
 */
object WordFlipGson {

    fun create(): Gson = GsonBuilder()
        .registerTypeAdapter(BookSource::class.java, bookSourceAdapter())
        .registerTypeAdapter(GroupSource::class.java, groupSourceAdapter())
        .registerTypeAdapter(GroupStatus::class.java, groupStatusAdapter())
        .registerTypeAdapter(MasteryLevel::class.java, masteryLevelAdapter())
        .registerTypeAdapter(StudyReason::class.java, studyReasonAdapter())
        .registerTypeAdapter(ThemeMode::class.java, themeModeAdapter())
        .registerTypeAdapter(GroupStrategy::class.java, groupStrategyAdapter())
        .registerTypeAdapter(StainType::class.java, stainTypeAdapter())
        .registerTypeAdapter(StainMode::class.java, stainModeAdapter())
        .create()

    private fun bookSourceAdapter(): TypeAdapter<BookSource> = object : TypeAdapter<BookSource>() {
        override fun write(out: JsonWriter, value: BookSource?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    BookSource.BUILTIN -> "builtin"
                    BookSource.IMPORTED -> "imported"
                },
            )
        }

        override fun read(reader: JsonReader): BookSource {
            return when (reader.nextString().lowercase()) {
                "imported" -> BookSource.IMPORTED
                else -> BookSource.BUILTIN
            }
        }
    }

    private fun groupSourceAdapter(): TypeAdapter<GroupSource> = object : TypeAdapter<GroupSource>() {
        override fun write(out: JsonWriter, value: GroupSource?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    GroupSource.AUTO -> "auto"
                    GroupSource.CUSTOM -> "custom"
                },
            )
        }

        override fun read(reader: JsonReader): GroupSource {
            return when (reader.nextString().lowercase()) {
                "custom" -> GroupSource.CUSTOM
                else -> GroupSource.AUTO
            }
        }
    }

    private fun groupStatusAdapter(): TypeAdapter<GroupStatus> = object : TypeAdapter<GroupStatus>() {
        override fun write(out: JsonWriter, value: GroupStatus?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    GroupStatus.NOT_STARTED -> "not_started"
                    GroupStatus.LEARNING -> "learning"
                    GroupStatus.COMPLETED -> "completed"
                },
            )
        }

        override fun read(reader: JsonReader): GroupStatus {
            return when (reader.nextString().lowercase()) {
                "learning" -> GroupStatus.LEARNING
                "completed" -> GroupStatus.COMPLETED
                else -> GroupStatus.NOT_STARTED
            }
        }
    }

    private fun masteryLevelAdapter(): TypeAdapter<MasteryLevel> = object : TypeAdapter<MasteryLevel>() {
        override fun write(out: JsonWriter, value: MasteryLevel?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    MasteryLevel.UNLEARNED -> "unlearned"
                    MasteryLevel.FUZZY -> "fuzzy"
                    MasteryLevel.UNKNOWN -> "unknown"
                },
            )
        }

        override fun read(reader: JsonReader): MasteryLevel {
            return when (reader.nextString().lowercase()) {
                "fuzzy" -> MasteryLevel.FUZZY
                "unknown" -> MasteryLevel.UNKNOWN
                else -> MasteryLevel.UNLEARNED
            }
        }
    }

    private fun studyReasonAdapter(): TypeAdapter<StudyReason> = object : TypeAdapter<StudyReason>() {
        override fun write(out: JsonWriter, value: StudyReason?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    StudyReason.NEW_WORDS -> "new_words"
                    StudyReason.DUE_REVIEW -> "due_review"
                    StudyReason.MIXED -> "mixed"
                },
            )
        }

        override fun read(reader: JsonReader): StudyReason {
            return when (reader.nextString().lowercase()) {
                "due_review" -> StudyReason.DUE_REVIEW
                "mixed" -> StudyReason.MIXED
                else -> StudyReason.NEW_WORDS
            }
        }
    }

    private fun themeModeAdapter(): TypeAdapter<ThemeMode> = object : TypeAdapter<ThemeMode>() {
        override fun write(out: JsonWriter, value: ThemeMode?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(value.storageValue())
        }

        override fun read(reader: JsonReader): ThemeMode = parseThemeMode(reader.nextString())
    }

    private fun groupStrategyAdapter(): TypeAdapter<GroupStrategy> = object : TypeAdapter<GroupStrategy>() {
        override fun write(out: JsonWriter, value: GroupStrategy?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(value.storageValue())
        }

        override fun read(reader: JsonReader): GroupStrategy = parseGroupStrategy(reader.nextString())
    }

    /** openapi：coffee / ink / highlight / crayon / random-line */
    private fun stainTypeAdapter(): TypeAdapter<StainType> = object : TypeAdapter<StainType>() {
        override fun write(out: JsonWriter, value: StainType?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    StainType.COFFEE -> "coffee"
                    StainType.INK -> "ink"
                    StainType.HIGHLIGHT -> "highlight"
                    StainType.CRAYON -> "crayon"
                    StainType.RANDOM_LINE -> "random-line"
                },
            )
        }

        override fun read(reader: JsonReader): StainType {
            return when (reader.nextString().lowercase()) {
                "ink" -> StainType.INK
                "highlight" -> StainType.HIGHLIGHT
                "crayon" -> StainType.CRAYON
                "random-line" -> StainType.RANDOM_LINE
                else -> StainType.COFFEE
            }
        }
    }

    private fun stainModeAdapter(): TypeAdapter<StainMode> = object : TypeAdapter<StainMode>() {
        override fun write(out: JsonWriter, value: StainMode?) {
            if (value == null) {
                out.nullValue()
                return
            }
            out.value(
                when (value) {
                    StainMode.RANDOM -> "random"
                    StainMode.SINGLE -> "single"
                    StainMode.MULTI -> "multi"
                },
            )
        }

        override fun read(reader: JsonReader): StainMode {
            return when (reader.nextString().lowercase()) {
                "single" -> StainMode.SINGLE
                "multi" -> StainMode.MULTI
                else -> StainMode.RANDOM
            }
        }
    }
}
