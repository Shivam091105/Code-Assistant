"use client";

import ReactMarkdown from "react-markdown";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { oneDark } from "react-syntax-highlighter/dist/esm/styles/prism";

export default function MessageBubble({
  role,
  content,
  sources,
  streaming,
}: {
  role: "USER" | "ASSISTANT";
  content: string;
  sources: string[];
  streaming?: boolean;
}) {
  const isUser = role === "USER";

  return (
    <div className={`flex ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[85%] rounded-lg border px-4 py-3 text-sm leading-relaxed ${
          isUser
            ? "border-accent2/30 bg-accent2/10 text-slate-100"
            : "border-line bg-panel text-slate-200"
        }`}
      >
        {!isUser && (
          <div className="mb-1.5 font-mono text-[11px] uppercase tracking-wide text-accent">
            assistant
          </div>
        )}

        {content ? (
          <div className="prose prose-invert prose-sm max-w-none prose-p:leading-relaxed prose-pre:bg-transparent prose-pre:p-0">
            <ReactMarkdown
              components={{
                code({ className, children, ...props }) {
                  const match = /language-(\w+)/.exec(className || "");
                  return match ? (
                    <SyntaxHighlighter
                      style={oneDark as any}
                      language={match[1]}
                      PreTag="div"
                      customStyle={{ borderRadius: 6, fontSize: 12.5, margin: "8px 0" }}
                    >
                      {String(children).replace(/\n$/, "")}
                    </SyntaxHighlighter>
                  ) : (
                    <code className="rounded bg-line px-1.5 py-0.5 text-[12.5px]" {...props}>
                      {children}
                    </code>
                  );
                },
              }}
            >
              {content}
            </ReactMarkdown>
          </div>
        ) : streaming ? (
          <TypingDots />
        ) : null}

        {streaming && content && <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-accent align-text-bottom" />}

        {!streaming && sources.length > 0 && (
          <div className="mt-3 border-t border-line pt-2">
            <div className="text-[11px] uppercase tracking-wide text-muted">Sources</div>
            <ul className="mt-1 space-y-0.5">
              {sources.map((s) => (
                <li key={s} className="font-mono text-[11.5px] text-accent2">
                  {s}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function TypingDots() {
  return (
    <div className="flex items-center gap-1 py-1">
      <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted [animation-delay:-0.3s]" />
      <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted [animation-delay:-0.15s]" />
      <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted" />
    </div>
  );
}
