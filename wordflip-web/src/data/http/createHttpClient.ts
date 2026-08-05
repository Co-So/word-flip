import axios, {
  AxiosHeaders,
  type AxiosInstance,
  type InternalAxiosRequestConfig
} from "axios";
import type { AuthSessionManager } from "@/data/http/auth/AuthSessionManager";
import { mapHttpError } from "@/data/http/errors/mapHttpError";

interface RetriedRequestConfig extends InternalAxiosRequestConfig {
  _wordflipRetried?: boolean;
}

function createBaseClient(baseURL: string): AxiosInstance {
  return axios.create({
    baseURL,
    headers: { "Content-Type": "application/json" },
    timeout: 10_000
  });
}

export function createPublicHttpClient(baseURL: string): AxiosInstance {
  const client = createBaseClient(baseURL);
  client.interceptors.response.use(
    (response) => response,
    (error: unknown) => Promise.reject(mapHttpError(error))
  );
  return client;
}

export function createAuthenticatedHttpClient({
  baseURL,
  sessions
}: {
  baseURL: string;
  sessions: AuthSessionManager;
}): AxiosInstance {
  const client = createBaseClient(baseURL);

  client.interceptors.request.use((config) => {
    const accessToken = sessions.getAccessToken();
    if (accessToken) {
      config.headers.set("Authorization", `Bearer ${accessToken}`);
    }
    return config;
  });

  client.interceptors.response.use(
    (response) => response,
    async (error: unknown) => {
      if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
        return Promise.reject(mapHttpError(error));
      }

      const request = error.config as RetriedRequestConfig;
      if (request._wordflipRetried === true) {
        sessions.clearSession();
        return Promise.reject(mapHttpError(error));
      }

      request._wordflipRetried = true;
      try {
        const accessToken = await sessions.refreshAccessToken();
        request.headers = AxiosHeaders.from(request.headers);
        request.headers.set("Authorization", `Bearer ${accessToken}`);
        return client.request(request);
      } catch (refreshError) {
        sessions.clearSession();
        return Promise.reject(mapHttpError(refreshError));
      }
    }
  );
  return client;
}
