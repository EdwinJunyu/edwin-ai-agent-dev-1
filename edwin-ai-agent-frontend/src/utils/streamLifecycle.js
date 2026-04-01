// Shared SSE helpers keep termination handling deterministic across chat pages.
export const STREAM_DONE_MARKER = '[DONE]';

export const parseStreamPayload = (raw, fallbackTitle = 'AI Response') => {
  const normalized = raw ?? '';

  if (!normalized) {
    return { type: 'empty' };
  }

  if (normalized === STREAM_DONE_MARKER) {
    return { type: 'done' };
  }

  try {
    const parsed = JSON.parse(normalized);
    const payloadList = Array.isArray(parsed) ? parsed : [parsed];
    const structuredItems = payloadList
      .filter(
        (item) =>
          item &&
          typeof item === 'object' &&
          typeof item.kind === 'string' &&
          typeof item.content === 'string',
      )
      .map((item) => ({
        kind: item.kind,
        title: item.title || fallbackTitle,
        content: item.content,
        step: item.step ?? null,
      }));

    if (structuredItems.length > 0) {
      return { type: 'structured', items: structuredItems };
    }
  } catch {
    // Structured payloads are optional. Plain text continues below.
  }

  return { type: 'plain', text: normalized };
};

// Terminal structured payloads should be rendered immediately so a ready final answer does not wait behind the typing queue.
export const shouldRenderStructuredPayloadImmediately = (payload) => {
  if (!payload || payload.type !== 'structured') {
    return false;
  }

  return payload.items.length > 1 || payload.items.some((item) => item.kind === 'final' || item.kind === 'error');
};

export const isActiveStreamEvent = (activeStreamKey, eventStreamKey) =>
  Boolean(activeStreamKey) && activeStreamKey === eventStreamKey;

export const getTypingOperation = ({ text, cursor, batchSize, terminated }) => {
  const chars = Array.from(text ?? '');

  if (cursor >= chars.length) {
    return {
      chunk: '',
      nextCursor: cursor,
      shouldDelay: false,
      completed: true,
    };
  }

  if (terminated) {
    return {
      chunk: chars.slice(cursor).join(''),
      nextCursor: chars.length,
      shouldDelay: false,
      completed: true,
    };
  }

  const safeBatchSize = Math.max(batchSize, 1);
  const nextCursor = Math.min(cursor + safeBatchSize, chars.length);

  return {
    chunk: chars.slice(cursor, nextCursor).join(''),
    nextCursor,
    shouldDelay: nextCursor < chars.length,
    completed: nextCursor >= chars.length,
  };
};
