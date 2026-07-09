package com.wordflip.core.network.media

/**
 * 将后端返回的相对媒体路径解析为绝对 URL，供 Coil 加载。
 * <p>
 * 例：apiBase=`http://127.0.0.1:8080/api/v1/` + path=`/api/v1/media/...`
 * → `http://127.0.0.1:8080/api/v1/media/...`
 */
object MediaUrlResolver {

    fun resolve(apiBaseUrl: String, pathOrUrl: String?): String? {
        if (pathOrUrl.isNullOrBlank()) return null
        val raw = pathOrUrl.trim()
        if (raw.startsWith("http://") ||
            raw.startsWith("https://") ||
            raw.startsWith("content:") ||
            raw.startsWith("file:")
        ) {
            return raw
        }
        val origin = originOf(apiBaseUrl)
        return if (raw.startsWith("/")) {
            origin + raw
        } else {
            apiBaseUrl.trimEnd('/') + "/" + raw
        }
    }

    /** `http://host:port/api/v1/` → `http://host:port` */
    fun originOf(apiBaseUrl: String): String {
        val trimmed = apiBaseUrl.trimEnd('/')
        val apiIdx = trimmed.indexOf("/api/")
        return if (apiIdx > 0) trimmed.substring(0, apiIdx) else trimmed
    }
}
