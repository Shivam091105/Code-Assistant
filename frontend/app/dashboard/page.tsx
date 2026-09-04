"use client";

import { useCurrentUser, useRepositories } from "@/lib/hooks";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import TopBar from "@/components/TopBar";
import RepoCard from "./RepoCard";

export default function DashboardPage() {
  const { data: user, isLoading: userLoading, isError } = useCurrentUser();
  const router = useRouter();

  useEffect(() => {
    if (!userLoading && (isError || !user)) {
      router.replace("/login");
    }
  }, [userLoading, isError, user, router]);

  const { data: repos, isLoading: reposLoading, refetch } = useRepositories();

  if (userLoading || !user) {
    return <CenteredNote text="Loading…" />;
  }

  return (
    <main className="min-h-screen bg-ink">
      <TopBar user={user} />

      <div className="mx-auto max-w-5xl px-6 py-10">
        <div className="mb-8 flex items-end justify-between">
          <div>
            <h1 className="text-2xl font-semibold text-slate-50">Your repositories</h1>
            <p className="mt-1 text-sm text-muted">
              Index a repository, then open chat to ask questions about it.
            </p>
          </div>
          <button
            onClick={() => refetch()}
            className="rounded border border-line px-3 py-2 text-xs text-muted transition hover:border-accent/50 hover:text-slate-100"
          >
            Refresh
          </button>
        </div>

        {reposLoading && <CenteredNote text="Fetching repositories from GitHub…" />}

        {repos && repos.length === 0 && (
          <div className="rounded-lg border border-line bg-panel p-8 text-center text-sm text-muted">
            No repositories found on your GitHub account.
          </div>
        )}

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          {repos?.map((repo) => (
            <RepoCard key={repo.githubRepositoryId} repo={repo} />
          ))}
        </div>
      </div>
    </main>
  );
}

function CenteredNote({ text }: { text: string }) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center font-mono text-sm text-muted">
      {text}
    </div>
  );
}
