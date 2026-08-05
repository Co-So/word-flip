/** OpenAPI AuthResponse 的 HTTP 层镜像，不向页面组件暴露。 */
export interface AuthResponseDto {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: {
    id: number;
    email: string | null;
    phone: string | null;
  };
}

export interface ErrorResponseDto {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}
