"use client";

import { api, CurrentUser } from "@/lib/api";
import { useRouter } from "next/navigation";

export default function TopBar({ user }: { user: CurrentUser }) {
  const router = useRouter();

  return (
    <header className="flex items-center justify-between border-b border-line px-6 py-4">
      <div className="flex items-center gap-2 font-mono text-sm text-slate-100">
        <span className="h-2 w-2 rounded-full bg-accent" />
        github-ai-code-assistant
      </div>
      <div className="flex items-center gap-3">
        {user.avatarUrl && (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={user.avatarUrl} alt={user.username} className="h-7 w-7 rounded-full border border-line" />
        )}
        <span className="text-sm text-muted">{user.username}</span>
        <button
          onClick={async () => {
            await api.logout();
            router.replace("/login");
          }}
          className="rounded border border-line px-3 py-1.5 text-xs text-muted transition hover:border-accent/50 hover:text-slate-100"
        >
          Sign out
        </button>
      </div>
    </header>
  );
}
