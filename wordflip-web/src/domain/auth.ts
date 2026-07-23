export interface AuthSession {
  userId: string;
  displayName: string;
  authenticated: boolean;
}

export interface AuthRepository {
  getSession(): Promise<AuthSession | null>;
  signOut(): Promise<{ signedOut: true }>;
}
