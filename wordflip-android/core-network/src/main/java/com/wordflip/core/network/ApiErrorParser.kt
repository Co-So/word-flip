package com.wordflip.core.network

import com.google.gson.Gson
import com.wordflip.core.model.ErrorResponse
import retrofit2.HttpException
import java.io.IOException

/**
 * 将 Retrofit/网络异常转为用户可读文案；优先解析 openapi ErrorResponse.message。
 */
class ApiErrorParser(
    private val gson: Gson,
) {

    fun parseMessage(throwable: Throwable): String {
        if (throwable is HttpException) {
            parseErrorBody(throwable)?.let { return it }
            return when (throwable.code()) {
                401 -> "登录已失效，请重新登录"
                403 -> "无权访问，请重新登录"
                409 -> "账号已存在"
                400 -> "请求参数有误"
                else -> "请求失败（${throwable.code()}）"
            }
        }
        if (throwable is IOException) {
            return "网络连接失败，请确认后端已启动且地址正确"
        }
        return throwable.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    }

    private fun parseErrorBody(exception: HttpException): String? {
        val body = exception.response()?.errorBody()?.string() ?: return null
        return try {
            gson.fromJson(body, ErrorResponse::class.java).message.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
