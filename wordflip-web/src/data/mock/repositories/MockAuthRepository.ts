import type { AppError } from "@/data/contracts/AppError";
import type { DemoStateStore } from "@/data/mock/DemoStateStore";
import type { AuthRepository, AuthSession, RegisterInput, SignInInput } from "@/domain/auth";

const demoCredentials = { account: "demo@wordflip.local", password: "wordflip-demo" };

function validation(message: string): AppError {
  return { kind: "validation", message, fieldErrors: {} };
}

function demoSession(displayName: string): AuthSession {
  return { userId: "demo-user", displayName, authenticated: true };
}

/** 演示认证只在当前浏览器状态中写入会话，绝不模拟生产账户、密码哈希或远端传输。 */
export class MockAuthRepository implements AuthRepository {
  private readonly registeredAccounts = new Map<string, { password: string }>();

  constructor(private readonly store: DemoStateStore) {}

  getSession(): Promise<AuthSession | null> {
    return Promise.resolve(this.store.read().auth.session);
  }

  signIn(input: SignInInput): Promise<AuthSession> {
    const account = input.account.trim().toLowerCase();
    const registered = this.registeredAccounts.get(account);
    const isDemoAccount = account === demoCredentials.account && input.password === demoCredentials.password;
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
    const account = input.account.trim().toLowerCase();
    const isEmail = /^\S+@\S+\.\S+$/.test(account);
    const isPhone = /^\+[1-9]\d{1,14}$/.test(account);
    if ((!isEmail && !isPhone) || input.password.length < 8) {
      return Promise.reject(validation("请填写有效的邮箱或手机号，以及至少 8 位密码"));
    }
    this.registeredAccounts.set(account, { password: input.password });
    const session = demoSession(account);
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
