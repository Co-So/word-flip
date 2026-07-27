import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { AuthRepository, AuthSession, RegisterInput, SignInInput } from "@/domain/auth";

const demoCredentials = { email: "demo@wordflip.local", password: "wordflip-demo" };

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

function demoSession(displayName: string): AuthSession {
  return { userId: "demo-user", displayName, authenticated: true };
}

/** 演示认证只在当前浏览器状态中写入会话，绝不模拟生产账户、密码哈希或远端传输。 */
export class MockAuthRepository implements AuthRepository {
  private readonly registeredAccounts = new Map<string, { displayName: string; password: string }>();

  constructor(private readonly store: DemoStateStore) {}

  getSession(): Promise<AuthSession | null> {
    return Promise.resolve(this.store.read().auth.session);
  }

  signIn(input: SignInInput): Promise<AuthSession> {
    const email = input.email.trim().toLowerCase();
    const registered = this.registeredAccounts.get(email);
    const isDemoAccount = email === demoCredentials.email && input.password === demoCredentials.password;
    const isRegisteredAccount = registered?.password === input.password;
    if (!isDemoAccount && !isRegisteredAccount) {
      return Promise.reject(validation("邮箱或密码不正确"));
    }

    const session = demoSession(registered?.displayName ?? "演示用户");
    this.store.update((draft) => {
      draft.auth.session = session;
      // 登录后的 Plan Gate 必须由 activePlanId 决定，不能假设演示账户已经配置词书。
      draft.books.activePlanId = null;
    });
    return Promise.resolve(session);
  }

  register(input: RegisterInput): Promise<AuthSession> {
    const email = input.email.trim().toLowerCase();
    if (!input.displayName.trim() || !/^\S+@\S+\.\S+$/.test(email) || input.password.length < 8) {
      return Promise.reject(validation("请填写有效的昵称、邮箱和至少 8 位密码"));
    }
    this.registeredAccounts.set(email, { displayName: input.displayName.trim(), password: input.password });
    const session = demoSession(input.displayName.trim());
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
