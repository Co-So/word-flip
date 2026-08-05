export interface AuthSession {
  userId: string;
  displayName: string;
  authenticated: boolean;
}

export interface SignInInput {
  account: string;
  password: string;
}

export type RegisterInput = SignInInput;

export interface AuthRepository {
  getSession(): Promise<AuthSession | null>;
  signIn(input: SignInInput): Promise<AuthSession>;
  register(input: RegisterInput): Promise<AuthSession>;
  signOut(): Promise<{ signedOut: true }>;
}
