"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api";

export function useCurrentUser() {
  return useQuery({
    queryKey: ["me"],
    queryFn: api.me,
    retry: false,
  });
}

export function useRepositories() {
  return useQuery({
    queryKey: ["repositories"],
    queryFn: api.listRepositories,
  });
}

export function useRepository(githubRepositoryId: number) {
  return useQuery({
    queryKey: ["repository", githubRepositoryId],
    queryFn: () => api.getRepository(githubRepositoryId),
    enabled: Number.isFinite(githubRepositoryId),
  });
}

export function useIndexStatusPolling(githubRepositoryId: number, enabled: boolean) {
  return useQuery({
    queryKey: ["index-status", githubRepositoryId],
    queryFn: () => api.getIndexStatus(githubRepositoryId),
    enabled,
    refetchInterval: (query) => (query.state.data?.status === "INDEXING" ? 2000 : false),
  });
}
