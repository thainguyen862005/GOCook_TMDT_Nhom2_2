import type { WorkflowThread } from '../clients/workflowClient';

export type ThreadFilter = 'all' | 'REQUEST' | 'ORDER';

/** Search is accent-insensitive while the data remains owned by the backend. */
export function normalizeThreadQuery(value: string): string {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'D').toLocaleLowerCase('vi-VN').trim();
}

export function sortAndFilterThreads(threads: readonly WorkflowThread[], query: string, filter: ThreadFilter): WorkflowThread[] {
  const search = normalizeThreadQuery(query);
  return threads
    .filter(thread => filter === 'all' || thread.kind === filter)
    .filter(thread => !search || [thread.peerName, thread.title, String(thread.scopeId)]
      .some(value => normalizeThreadQuery(value || '').includes(search)))
    .slice()
    .sort((a, b) => {
      const dateA = a.lastMessageAt ? Date.parse(a.lastMessageAt) : 0;
      const dateB = b.lastMessageAt ? Date.parse(b.lastMessageAt) : 0;
      return (Number.isFinite(dateB) ? dateB : 0) - (Number.isFinite(dateA) ? dateA : 0)
        || b.scopeId - a.scopeId;
    });
}

export function conversationPath(thread: WorkflowThread, base: string): string {
  const params = new URLSearchParams();
  if (thread.kind === 'ORDER') params.set('orderId', String(thread.scopeId));
  else {
    params.set('requestId', String(thread.scopeId));
    params.set('peerId', String(thread.peerId));
  }
  return `${base}?${params}`;
}
