export interface AuthSession {
  userId: string;
  displayName: string;
  authenticated: boolean;
}

export interface SignInInput {
  email: string;
  password: string;
}

export interface RegisterInput extends SignInInput {
  displayName: string;
}

export interface AuthRepository {
  getSession(): Promise<AuthSession | null>;
  signIn(input: SignInInput): Promise<AuthSession>;
  register(input: RegisterInput): Promise<AuthSession>;
  signOut(): Promise<{ signedOut: true }>;
}
