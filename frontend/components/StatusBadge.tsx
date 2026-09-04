import { IndexingStatus } from "@/lib/api";

const STYLES: Record<IndexingStatus, string> = {
  NOT_INDEXED: "text-muted border-line",
  INDEXING: "text-accent2 border-accent2/40",
  COMPLETED: "text-accent border-accent/40",
  FAILED: "text-rose-400 border-rose-400/40",
};

const LABELS: Record<IndexingStatus, string> = {
  NOT_INDEXED: "Not indexed",
  INDEXING: "Indexing…",
  COMPLETED: "Indexed ✓",
  FAILED: "Failed",
};

export default function StatusBadge({ status }: { status: IndexingStatus }) {
  return (
    <span className={`inline-flex items-center rounded-full border px-2.5 py-1 font-mono text-[11px] ${STYLES[status]}`}>
      {LABELS[status]}
    </span>
  );
}
