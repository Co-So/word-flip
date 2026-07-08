package com.wordflip.core.network.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.wordflip.core.model.book.BookSource
import com.wordflip.core.model.settings.ThemeMode
import com.wordflip.core.model.settings.parseThemeMode
import com.wordflip.core.model.settings.storageValue

/**
 * Gson 小写 enum 适配（P0-A1B）：服务端 JSON 使用 builtin/imported、system/light/dark。
 */
object WordFlipGson {

    fun create(): Gson = GsonBuilder()
        .registerTypeAdapter(BookSource::class.java, bookSourceAdapter())
        .registerTypeAdapter(ThemeMode::class.java, themeModeAdapter())
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
}
