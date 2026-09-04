"use client";

import { api } from "@/lib/api";
import { useCurrentUser } from "@/lib/hooks";
import { useRouter } from "next/navigation";
import { useEffect } from "react";

export default function LoginPage() {
  const { data: user, isLoading } = useCurrentUser();
  const router = useRouter();

  useEffect(() => {
    if (user) {
      router.replace("/dashboard");
    }
  }, [user, router]);

  return (
    <main className="relative min-h-screen overflow-hidden bg-ink">
      <div
        className="pointer-events-none absolute inset-0 opacity-[0.07]"
        style={{
          backgroundImage:
            "linear-gradient(#5eead4 1px, transparent 1px), linear-gradient(90deg, #5eead4 1px, transparent 1px)",
          backgroundSize: "42px 42px",
        }}
      />

      <div className="relative flex min-h-screen flex-col items-center justify-center px-6">
        <div className="mb-10 flex items-center gap-3 font-mono text-sm text-muted">
          <span className="h-2 w-2 rounded-full bg-accent" />
          repo &gt; index &gt; ask
        </div>

        <h1 className="max-w-xl text-center text-4xl font-semibold leading-tight text-slate-50 sm:text-5xl">
          Understand your repositories
          <span className="text-accent">.</span>
          <br />
          Ask, don&apos;t grep.
        </h1>

        <p className="mt-5 max-w-md text-center text-base text-muted">
          Sign in with GitHub, pick a repository, and ask questions answered
          directly from its actual source&nbsp;code&mdash;with the files and
          lines cited.
        </p>

        <button
          onClick={() => (window.location.href = api.loginUrl())}
          disabled={isLoading}
          className="mt-10 flex items-center gap-3 rounded-md border border-line bg-panel px-6 py-3 font-medium text-slate-100 transition hover:border-accent/60 hover:bg-panel/80 disabled:opacity-50"
        >
          <GitHubMark />
          Continue with GitHub
        </button>

        <div className="mt-14 grid w-full max-w-2xl grid-cols-1 gap-px overflow-hidden rounded-lg border border-line bg-line sm:grid-cols-3">
          <Step n="01" title="Connect" desc="Authorize with your GitHub account." />
          <Step n="02" title="Index" desc="Your code is chunked and embedded." />
          <Step n="03" title="Ask" desc="Get streamed answers with sources." />
        </div>
      </div>
    </main>
  );
}

function Step({ n, title, desc }: { n: string; title: string; desc: string }) {
  return (
    <div className="bg-ink px-5 py-5">
      <div className="font-mono text-xs text-accent2">{n}</div>
      <div className="mt-2 text-sm font-medium text-slate-100">{title}</div>
      <div className="mt-1 text-xs text-muted">{desc}</div>
    </div>
  );
}

function GitHubMark() {
  return (
    <svg width="20" height="20" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
      <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38
        0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13
        -.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66
        .07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15
        -.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27
        .68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12
        .51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48
        0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
    </svg>
  );
}
