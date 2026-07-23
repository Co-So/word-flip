export type AppError =
  | { kind: "validation"; message: string; fieldErrors: Record<string, string> }
  | { kind: "unauthorized"; message: string }
  | { kind: "not-found"; message: string }
  | { kind: "conflict"; message: string }
  | { kind: "unavailable"; message: string; retryable: true }
  | { kind: "unknown"; message: string };
