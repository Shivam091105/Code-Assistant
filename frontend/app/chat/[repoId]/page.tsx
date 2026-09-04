"use client";

import { useCurrentUser, useRepository } from "@/lib/hooks";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import TopBar from "@/components/TopBar";
import StatusBadge from "@/components/StatusBadge";
import { api, ChatMessageItem } from "@/lib/api";
import { streamChatAnswer } from "@/lib/chatStream";
import MessageBubble from "./MessageBubble";

interface DisplayMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  sources: string[];
  streaming?: boolean;
}

export default function ChatPage({ params }: { params: { repoId: string } }) {
  const githubRepositoryId = Number(params.repoId);
  const router = useRouter();

  const { data: user, isLoading: userLoading, isError: userError } = useCurrentUser();
  const { data: repository } = useRepository(githubRepositoryId);

  const [messages, setMessages] = useState<DisplayMessage[]>([]);
  const [question, setQuestion] = useState("");
  const [sending, setSending] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!userLoading && (userError || !user)) {
      router.replace("/login");
    }
  }, [userLoading, userError, user, router]);

  useEffect(() => {
    if (!Number.isFinite(githubRepositoryId)) return;
    api.getMessages(githubRepositoryId).then((history: ChatMessageItem[]) => {
      setMessages(
        history.map((m) => ({
          id: String(m.id),
          role: m.role,
          content: m.content,
          sources: m.sources,
        }))
      );
    });
  }, [githubRepositoryId]);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [messages]);

  async function handleSend() {
    const trimmed = question.trim();
    if (!trimmed || sending) return;

    setLoadError(null);
    setQuestion("");
    setSending(true);

    const userMsgId = `local-user-${Date.now()}`;
    const assistantMsgId = `local-assistant-${Date.now()}`;

    setMessages((prev) => [
      ...prev,
      { id: userMsgId, role: "USER", content: trimmed, sources: [] },
      { id: assistantMsgId, role: "ASSISTANT", content: "", sources: [], streaming: true },
    ]);

    try {
      await streamChatAnswer(githubRepositoryId, trimmed, (event) => {
        if (event.type === "token" && event.token) {
          setMessages((prev) =>
            prev.map((m) => (m.id === assistantMsgId ? { ...m, content: m.content + event.token } : m))
          );
        } else if (event.type === "done") {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantMsgId ? { ...m, sources: event.sources || [], streaming: false } : m
            )
          );
        } else if (event.type === "error") {
          setLoadError("Something went wrong generating a response. Please try again.");
          setMessages((prev) => prev.filter((m) => m.id !== assistantMsgId));
        }
      });
    } catch {
      setLoadError("Lost connection while streaming the response.");
    } finally {
      setSending(false);
    }
  }

  if (userLoading || !user) {
    return <div className="flex min-h-screen items-center justify-center font-mono text-sm text-muted">Loading…</div>;
  }

  return (
    <main className="flex h-screen flex-col bg-ink">
      <TopBar user={user} />

      <div className="flex items-center justify-between border-b border-line px-6 py-3">
        <button
          onClick={() => router.push("/dashboard")}
          className="text-xs text-muted transition hover:text-slate-100"
        >
          ← Dashboard
        </button>
        {repository && (
          <div className="flex items-center gap-3">
            <span className="font-mono text-sm text-slate-100">{repository.fullName}</span>
            <StatusBadge status={repository.indexingStatus} />
          </div>
        )}
        <div className="w-16" />
      </div>

      <div ref={scrollRef} className="scrollbar-thin flex-1 overflow-y-auto px-6 py-6">
        <div className="mx-auto max-w-3xl space-y-6">
          {messages.length === 0 && (
            <div className="rounded-lg border border-line bg-panel p-6 text-sm text-muted">
              Ask something about this repository, e.g. &quot;Where is authentication configured?&quot;
              or &quot;What does the indexing pipeline do?&quot;
            </div>
          )}
          {messages.map((m) => (
            <MessageBubble key={m.id} role={m.role} content={m.content} sources={m.sources} streaming={m.streaming} />
          ))}
          {loadError && (
            <div className="rounded-md border border-rose-400/40 bg-rose-950/30 px-4 py-3 text-sm text-rose-300">
              {loadError}
            </div>
          )}
        </div>
      </div>

      <div className="border-t border-line px-6 py-4">
        <div className="mx-auto flex max-w-3xl items-end gap-3">
          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
            }}
            placeholder="Ask a question about this repository…"
            rows={1}
            className="max-h-40 flex-1 resize-none rounded-md border border-line bg-panel px-4 py-3 text-sm text-slate-100 outline-none placeholder:text-muted focus:border-accent/50"
          />
          <button
            onClick={handleSend}
            disabled={sending || !question.trim()}
            className="rounded-md bg-accent px-5 py-3 text-sm font-medium text-ink transition hover:bg-accent/90 disabled:cursor-not-allowed disabled:bg-line disabled:text-muted"
          >
            {sending ? "…" : "Send"}
          </button>
        </div>
      </div>
    </main>
  );
}
