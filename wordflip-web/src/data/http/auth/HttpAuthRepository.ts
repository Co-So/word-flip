import type { AxiosInstance } from "axios";
import { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import type { AuthResponseDto } from "@/data/http/auth/authDtos";
import { createHttpRuntime } from "@/data/http/createHttpRuntime";
import type {
  AuthRepository,
  AuthSession,
  RegisterInput,
  SignInInput
} from "@/domain/auth";

/** 严格 E.164 格式：+ 后跟 1~15 位数字，首位非零。 */
const e164Pattern = /^\+[1-9]\d{1,14}$/;

/** 宽松判断：是否看起来像手机号（支持+开头、00开头、或纯数字≥7位）。 */
function looksLikePhone(raw: string): boolean {
  const trimmed = raw.trim();
  if (!trimmed) return false;
  if (trimmed.startsWith("+")) return e164Pattern.test(trimmed);
  const digitsOnly = trimmed.replace(/[\s\-()]/g, "");
  if (/^\d+$/.test(digitsOnly) && digitsOnly.length >= 7 && digitsOnly.length <= 15) {
    return true;
  }
  return false;
}

/** 把用户输入的手机号规范化为 E.164：
 *  - 大陆 11 位 1 开头自动补 +86
 *  - 00 开头替换为 +
 *  - 已经是 E.164 的原样保留
 *  - 其它纯数字暂按 +86 补（用户可在后续流程修正）
 */
function normalizePhone(raw: string): string {
  const trimmed = raw.trim().replace(/[\s\-()]/g, "");
  if (e164Pattern.test(trimmed)) return trimmed;
  if (trimmed.startsWith("00")) return "+" + trimmed.slice(2);
  if (/^1\d{10}$/.test(trimmed)) return "+86" + trimmed;
  if (/^\d{7,15}$/.test(trimmed)) return "+86" + trimmed;
  return trimmed;
}

/** 判断账号字段：宽松看像手机号就按手机处理，否则按邮箱并小写。 */
function classifyAccount(raw: string): { type: "phone" | "email"; value: string } {
  const trimmed = raw.trim();
  if (looksLikePhone(trimmed)) {
    return { type: "phone", value: normalizePhone(trimmed) };
  }
  return { type: "email", value: trimmed.toLowerCase() };
}

/** 严格按 OpenAPI Auth 契约发送请求，并把 HTTP DTO 转换为页面会话。 */
export class HttpAuthRepository implements AuthRepository {
  constructor(
    private readonly publicClient: AxiosInstance,
    private readonly authenticatedClient: AxiosInstance,
    private readonly sessions: AuthSessionManager
  ) {}

  getSession(): Promise<AuthSession | null> {
    return this.sessions.getSession();
  }

  async signIn(input: SignInInput): Promise<AuthSession> {
    // 登录使用 account 字段；邮箱小写、手机 E.164 统一规范化
    const account = classifyAccount(input.account);
    const response = await this.publicClient.post<AuthResponseDto>("/auth/login", {
      account: account.value,
      password: input.password
    });
    return this.sessions.startSession(response.data);
  }

  async register(input: RegisterInput): Promise<AuthSession> {
    const account = classifyAccount(input.account);
    const body = account.type === "phone"
      ? { phone: account.value, password: input.password }
      : { email: account.value, password: input.password };
    const response = await this.publicClient.post<AuthResponseDto>("/auth/register", body);
    return this.sessions.startSession(response.data);
  }

  async signOut(): Promise<{ signedOut: true }> {
    const refreshToken = this.sessions.getRefreshToken();
    try {
      if (refreshToken) {
        await this.authenticatedClient.post("/auth/logout", { refreshToken });
      }
    } catch {
      // 远端不可用不能阻止当前浏览器退出；服务端令牌仍会按有效期失效。
    } finally {
      this.sessions.clearSession();
    }
    return { signedOut: true };
  }
}

export function createHttpAuthRepository({
  baseURL,
  storage,
  now
}: {
  baseURL: string;
  storage: Storage | null;
  now?: () => number;
}): HttpAuthRepository {
  const runtime = createHttpRuntime({ baseURL, storage, now });
  return new HttpAuthRepository(
    runtime.publicClient,
    runtime.authenticatedClient,
    runtime.sessions
  );
}
