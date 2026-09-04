const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    credentials: "include", // send the session cookie set by Spring Security's OAuth2 login
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    },
  });

  if (!res.ok) {
    let message = `Request failed with status ${res.status}`;
    try {
      const body = await res.json();
      message = body.message || message;
    } catch {
      // response wasn't JSON; keep the default message
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export interface CurrentUser {
  id: number;
  username: string;
  email: string | null;
  avatarUrl: string | null;
}

export type IndexingStatus = "NOT_INDEXED" | "INDEXING" | "COMPLETED" | "FAILED";

export interface GitHubRepoSummary {
  githubRepositoryId: number;
  name: string;
  fullName: string;
  owner: string;
  description: string | null;
  language: string | null;
  defaultBranch: string;
  url: string;
  tracked: boolean;
  indexingStatus: IndexingStatus;
}

export interface RepositoryDetail {
  id: number;
  name: string;
  fullName: string;
  owner: string;
  defaultBranch: string;
  description: string | null;
  language: string | null;
  url: string;
  indexingStatus: IndexingStatus;
  indexingError: string | null;
  updatedAt: string;
}

export interface ChatMessageItem {
  id: number;
  role: "USER" | "ASSISTANT";
  content: string;
  sources: string[];
  createdAt: string;
}

export const api = {
  me: () => request<CurrentUser>("/api/auth/me"),

  loginUrl: () => `${API_BASE_URL}/oauth2/authorization/github`,

  logout: async () => {
    await fetch(`${API_BASE_URL}/api/auth/logout`, { method: "POST", credentials: "include" });
  },

  listRepositories: () => request<GitHubRepoSummary[]>("/api/repositories"),

  getRepository: (githubRepositoryId: number) =>
    request<RepositoryDetail>(`/api/repositories/${githubRepositoryId}`),

  indexRepository: (githubRepositoryId: number) =>
    request<RepositoryDetail>(`/api/repositories/${githubRepositoryId}/index`, { method: "POST" }),

  getIndexStatus: (githubRepositoryId: number) =>
    request<{ status: IndexingStatus; error: string }>(`/api/repositories/${githubRepositoryId}/index-status`),

  getMessages: (githubRepositoryId: number) =>
    request<ChatMessageItem[]>(`/api/repositories/${githubRepositoryId}/messages`),
};

export { API_BASE_URL };
