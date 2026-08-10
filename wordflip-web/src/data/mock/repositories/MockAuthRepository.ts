import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { AuthRepository, AuthSession, RegisterInput, SignInInput } from "@/domain/auth";

const demoCredentials = { account: "demo@wordflip.local", password: "wordflip-demo" };

const e164Pattern = /^\+[1-9]\d{1,14}$/;

function validation(message: string, fieldErrors?: Record<string, string>): AppError {
  return { kind: "validation", message, fieldErrors: fieldErrors ?? {} };
}

function conflict(message: string): AppError {
  return { kind: "conflict", message };
}

function demoSession(displayName: string): AuthSession {
  return { userId: "demo-user", displayName, authenticated: true };
}

/** 宽松判断：看起来像手机号。 */
function looksLikePhone(raw: string): boolean {
  const trimmed = raw.trim();
  if (!trimmed) return false;
  if (trimmed.startsWith("+")) return e164Pattern.test(trimmed);
  const digitsOnly = trimmed.replace(/[\s\-()]/g, "");
  return /^\d+$/.test(digitsOnly) && digitsOnly.length >= 7 && digitsOnly.length <= 15;
}

/** 把用户输入的手机号规范化为 E.164。 */
function normalizePhone(raw: string): string {
  const trimmed = raw.trim().replace(/[\s\-()]/g, "");
  if (e164Pattern.test(trimmed)) return trimmed;
  if (trimmed.startsWith("00")) return "+" + trimmed.slice(2);
  if (/^1\d{10}$/.test(trimmed)) return "+86" + trimmed;
  if (/^\d{7,15}$/.test(trimmed)) return "+86" + trimmed;
  return trimmed;
}

/** 规范化账号：手机号转 E.164，邮箱转小写。 */
function normalizeAccount(raw: string): string {
  const trimmed = raw.trim();
  if (looksLikePhone(trimmed)) return normalizePhone(trimmed);
  return trimmed.toLowerCase();
}

/** 演示认证只在当前浏览器状态中写入会话，绝不模拟生产账户、密码哈希或远端传输。 */
export class MockAuthRepository implements AuthRepository {
  private readonly registeredAccounts = new Map<string, { password: string }>();

  constructor(private readonly store: DemoStateStore) {}

  getSession(): Promise<AuthSession | null> {
    return Promise.resolve(this.store.read().auth.session);
  }

  signIn(input: SignInInput): Promise<AuthSession> {
    const account = normalizeAccount(input.account);
    const registered = this.registeredAccounts.get(account);
    const isDemoAccount = account === normalizeAccount(demoCredentials.account) && input.password === demoCredentials.password;
    const isRegisteredAccount = registered?.password === input.password;
    if (!isDemoAccount && !isRegisteredAccount) {
      return Promise.reject(validation("账号或密码错误"));
    }

    const session = demoSession(registered ? account : "演示用户");
    this.store.update((draft) => {
      draft.auth.session = session;
      // 登录只恢复本地会话，当前计划及其历史必须由已保存的状态决定。
    });
    return Promise.resolve(session);
  }

  register(input: RegisterInput): Promise<AuthSession> {
    const rawAccount = input.account.trim();
    const normalized = normalizeAccount(rawAccount);
    const isEmail = /^\S+@\S+\.\S+$/.test(normalized);
    const isPhone = looksLikePhone(rawAccount);

    // 格式校验：必须是合法邮箱或可识别手机号，且密码≥8位
    if ((!isEmail && !isPhone) || input.password.length < 8) {
      const errors: Record<string, string> = {};
      if (!isEmail && !isPhone) errors.account = "请填写有效的邮箱或手机号";
      if (input.password.length < 8) errors.password = "密码至少 8 位";
      return Promise.reject(validation("请填写有效的邮箱或手机号，以及至少 8 位密码", errors));
    }

    // 重复账号检查：邮箱/手机号已注册则返回冲突错误
    if (this.registeredAccounts.has(normalized)) {
      const hint = isPhone ? "该手机号已注册，请直接登录或使用其他号码" : "该邮箱已注册，请直接登录或使用其他邮箱";
      return Promise.reject(conflict(hint));
    }

    this.registeredAccounts.set(normalized, { password: input.password });
    const session = demoSession(normalized);
    this.store.update((draft) => {
      draft.auth.session = session;
      draft.books.activePlanId = null;
    });
    return Promise.resolve(session);
  }

  signOut(): Promise<{ signedOut: true }> {
    this.store.update((draft) => {
      draft.auth.session = null;
    });
    return Promise.resolve({ signedOut: true });
  }
}
