"use client";

import { api, GitHubRepoSummary } from "@/lib/api";
import { useIndexStatusPolling } from "@/lib/hooks";
import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import StatusBadge from "@/components/StatusBadge";

export default function RepoCard({ repo }: { repo: GitHubRepoSummary }) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [triggering, setTriggering] = useState(false);

  const { data: liveStatus } = useIndexStatusPolling(
    repo.githubRepositoryId,
    repo.tracked && repo.indexingStatus === "INDEXING"
  );

  const status = liveStatus?.status ?? repo.indexingStatus;

  async function handleIndex() {
    setTriggering(true);
    try {
      await api.indexRepository(repo.githubRepositoryId);
      queryClient.invalidateQueries({ queryKey: ["repositories"] });
      queryClient.invalidateQueries({ queryKey: ["index-status", repo.githubRepositoryId] });
    } finally {
      setTriggering(false);
    }
  }

  const canChat = status === "COMPLETED";
  const canIndex = status !== "INDEXING";

  return (
    <div className="flex flex-col justify-between rounded-lg border border-line bg-panel p-5">
      <div>
        <div className="flex items-start justify-between gap-3">
          <div>
            <div className="font-mono text-sm text-slate-100">{repo.name}</div>
            <div className="text-xs text-muted">{repo.owner}</div>
          </div>
          <StatusBadge status={status} />
        </div>

        {repo.description && (
          <p className="mt-3 line-clamp-2 text-sm text-muted">{repo.description}</p>
        )}

        {repo.language && (
          <div className="mt-3 inline-flex items-center gap-1.5 text-xs text-muted">
            <span className="h-2 w-2 rounded-full bg-accent2" />
            {repo.language}
          </div>
        )}
      </div>

      <div className="mt-5 flex gap-2">
        <button
          onClick={handleIndex}
          disabled={!canIndex || triggering}
          className="flex-1 rounded border border-line px-3 py-2 text-xs text-slate-100 transition hover:border-accent/50 disabled:cursor-not-allowed disabled:opacity-40"
        >
          {status === "NOT_INDEXED" && "Index repository"}
          {status === "INDEXING" && "Indexing…"}
          {status === "COMPLETED" && "Re-index"}
          {status === "FAILED" && "Retry indexing"}
        </button>
        <button
          onClick={() => router.push(`/chat/${repo.githubRepositoryId}`)}
          disabled={!canChat}
          className="flex-1 rounded bg-accent px-3 py-2 text-xs font-medium text-ink transition hover:bg-accent/90 disabled:cursor-not-allowed disabled:bg-line disabled:text-muted"
        >
          Open chat
        </button>
      </div>
    </div>
  );
}
