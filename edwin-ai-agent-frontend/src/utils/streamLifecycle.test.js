import assert from 'node:assert/strict';
import test from 'node:test';

import {
  STREAM_DONE_MARKER,
  getTypingOperation,
  isActiveStreamEvent,
  parseStreamPayload,
  shouldRenderStructuredPayloadImmediately,
} from './streamLifecycle.js';

test('parseStreamPayload marks the done frame as terminal', () => {
  const payload = parseStreamPayload(STREAM_DONE_MARKER);

  assert.deepEqual(payload, { type: 'done' });
});

test('parseStreamPayload keeps structured bubbles intact', () => {
  const payload = parseStreamPayload(
    JSON.stringify([
      {
        kind: 'final',
        title: 'Final Reply',
        content: 'done',
        step: 4,
      },
    ]),
    'Fallback',
  );

  assert.equal(payload.type, 'structured');
  assert.deepEqual(payload.items, [
    {
      kind: 'final',
      title: 'Final Reply',
      content: 'done',
      step: 4,
    },
  ]);
});

test('shouldRenderStructuredPayloadImmediately fast-forwards final structured payloads', () => {
  const payload = parseStreamPayload(
    JSON.stringify([
      {
        kind: 'thought',
        title: 'Thought',
        content: 'done thinking',
        step: 2,
      },
      {
        kind: 'final',
        title: 'Final',
        content: 'ready to answer',
        step: 2,
      },
    ]),
  );

  assert.equal(shouldRenderStructuredPayloadImmediately(payload), true);
});

test('shouldRenderStructuredPayloadImmediately keeps single non-terminal bubbles typed', () => {
  const payload = parseStreamPayload(
    JSON.stringify({
      kind: 'thought',
      title: 'Thought',
      content: 'still working',
      step: 1,
    }),
  );

  assert.equal(shouldRenderStructuredPayloadImmediately(payload), false);
});

test('parseStreamPayload falls back to plain text when JSON is not a bubble payload', () => {
  const payload = parseStreamPayload('plain text');

  assert.deepEqual(payload, {
    type: 'plain',
    text: 'plain text',
  });
});

test('isActiveStreamEvent ignores stale stream ids', () => {
  assert.equal(isActiveStreamEvent('stream-2', 'stream-1'), false);
  assert.equal(isActiveStreamEvent('stream-2', 'stream-2'), true);
});

test('getTypingOperation fast-forwards the remaining text after termination', () => {
  const operation = getTypingOperation({
    text: 'abcdef',
    cursor: 2,
    batchSize: 3,
    terminated: true,
  });

  assert.deepEqual(operation, {
    chunk: 'cdef',
    nextCursor: 6,
    shouldDelay: false,
    completed: true,
  });
});

test('getTypingOperation preserves batched typing before termination', () => {
  const operation = getTypingOperation({
    text: 'abcdef',
    cursor: 1,
    batchSize: 3,
    terminated: false,
  });

  assert.deepEqual(operation, {
    chunk: 'bcd',
    nextCursor: 4,
    shouldDelay: true,
    completed: false,
  });
});
