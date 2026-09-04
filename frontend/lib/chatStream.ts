import { API_BASE_URL } from "./api";

export interface StreamEvent {
  type: "token" | "done" | "error";
  token?: string;
  sources?: string[];
}

/**
 * Consumes the backend's SSE chat endpoint using fetch's streaming body
 * reader (EventSource can't send a POST body, and this is a POST that
 * carries the question). Each SSE frame is "data: <json>\n\n"; we split on
 * blank lines and parse the JSON payload from each frame.
 */
export async function streamChatAnswer(
  githubRepositoryId: number,
  question: string,
  onEvent: (event: StreamEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/repositories/${githubRepositoryId}/chat`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ question }),
    signal,
  });

  if (!response.ok || !response.body) {
    onEvent({ type: "error" });
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() || "";

    for (const frame of frames) {
      const dataLine = frame.split("\n").find((line) => line.startsWith("data:"));
      if (!dataLine) continue;
      const jsonText = dataLine.slice("data:".length).trim();
      if (!jsonText) continue;
      try {
        const event: StreamEvent = JSON.parse(jsonText);
        onEvent(event);
      } catch {
        // ignore malformed frames rather than breaking the whole stream
      }
    }
  }
}
