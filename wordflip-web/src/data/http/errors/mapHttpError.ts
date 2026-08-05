import axios from "axios";
import type { AppError } from "@/data/contracts/AppError";
import type { ErrorResponseDto } from "@/data/http/auth/authDtos";

const appErrorKinds = new Set<AppError["kind"]>([
  "validation",
  "unauthorized",
  "not-found",
  "conflict",
  "unavailable",
  "unknown"
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isAppError(value: unknown): value is AppError {
  return isRecord(value)
    && typeof value.kind === "string"
    && appErrorKinds.has(value.kind as AppError["kind"])
    && typeof value.message === "string";
}

function responseBody(value: unknown): ErrorResponseDto | null {
  if (!isRecord(value) || typeof value.code !== "string" || typeof value.message !== "string") {
    return null;
  }
  return {
    code: value.code,
    message: value.message,
    details: isRecord(value.details) ? value.details : undefined
  };
}

function fieldErrorsOf(details: Record<string, unknown> | undefined): Record<string, string> {
  if (!details) return {};
  return Object.fromEntries(
    Object.entries(details).filter((entry): entry is [string, string] => typeof entry[1] === "string")
  );
}

/** 隔离 Axios 与服务端内部错误结构，只向页面暴露稳定的 AppError。 */
export function mapHttpError(error: unknown): AppError {
  if (isAppError(error)) return error;
  if (!axios.isAxiosError(error)) {
    return { kind: "unknown", message: "发生未知错误，请稍后重试" };
  }

  if (!error.response) {
    return {
      kind: "unavailable",
      message: "网络连接不可用，请稍后重试",
      retryable: true
    };
  }

  const status = error.response.status;
  const body = responseBody(error.response.data);
  const message = body?.message;
  if (status === 400 || body?.code === "VALIDATION_ERROR") {
    return {
      kind: "validation",
      message: message ?? "请求内容无效",
      fieldErrors: fieldErrorsOf(body?.details)
    };
  }
  if (status === 401) {
    return { kind: "unauthorized", message: message ?? "登录状态已失效，请重新登录" };
  }
  if (status === 404) {
    return { kind: "not-found", message: message ?? "请求的资源不存在" };
  }
  if (status === 409) {
    return { kind: "conflict", message: message ?? "请求与当前状态冲突" };
  }
  if (status >= 500) {
    return {
      kind: "unavailable",
      message: "服务暂时不可用，请稍后重试",
      retryable: true
    };
  }
  return { kind: "unknown", message: message ?? "发生未知错误，请稍后重试" };
}
