import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from "axios";
import { mapHttpError } from "@/data/http/errors/mapHttpError";

function requestConfig(): InternalAxiosRequestConfig {
  return { headers: new AxiosHeaders() } as InternalAxiosRequestConfig;
}

function responseError(status: number, data: unknown): AxiosError {
  const config = requestConfig();
  const response: AxiosResponse = {
    data,
    status,
    statusText: String(status),
    headers: new AxiosHeaders(),
    config
  };
  return new AxiosError("request failed", AxiosError.ERR_BAD_RESPONSE, config, undefined, response);
}

test("400 验证响应只提取字符串字段错误", () => {
  expect(mapHttpError(responseError(400, {
    code: "VALIDATION_ERROR",
    message: "Request validation failed",
    details: { password: "密码至少 8 位", ignored: { internal: true } }
  }))).toEqual({
    kind: "validation",
    message: "Request validation failed",
    fieldErrors: { password: "密码至少 8 位" }
  });
});

test.each([
  [401, "账号或密码错误", { kind: "unauthorized", message: "账号或密码错误" }],
  [404, "资源不存在", { kind: "not-found", message: "资源不存在" }],
  [409, "账号已存在", { kind: "conflict", message: "账号已存在" }]
])("HTTP %i 映射为稳定的应用错误", (status, message, expected) => {
  expect(mapHttpError(responseError(status, { code: "ERROR", message }))).toEqual(expected);
});

test("HTTP 5xx 不向页面泄露内部消息并允许重试", () => {
  expect(mapHttpError(responseError(500, {
    code: "INTERNAL_ERROR",
    message: "database password leaked"
  }))).toEqual({
    kind: "unavailable",
    message: "服务暂时不可用，请稍后重试",
    retryable: true
  });
});

test("没有响应的 Axios 网络错误映射为可重试错误", () => {
  const error = new AxiosError("Network Error", AxiosError.ERR_NETWORK, requestConfig());

  expect(mapHttpError(error)).toEqual({
    kind: "unavailable",
    message: "网络连接不可用，请稍后重试",
    retryable: true
  });
});

test("已经是 AppError 的对象保持原样", () => {
  const error = { kind: "conflict" as const, message: "账号已存在" };

  expect(mapHttpError(error)).toBe(error);
});

test("无法识别的异常映射为 unknown", () => {
  expect(mapHttpError(new Error("unexpected"))).toEqual({
    kind: "unknown",
    message: "发生未知错误，请稍后重试"
  });
});
