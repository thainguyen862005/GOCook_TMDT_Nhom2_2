import { httpRequest } from './httpClient';
import type { ChefDetail, ChefSummary, CookingRequestInput, EditableFaq, Faq, Offer, PublicPage } from '../types/publicContent';

export interface ChefSearch { q?: string; area?: string; category?: string; sort?: string; page?: number; size?: number; }
export const publicContentClient = {
  chefs: (filter: ChefSearch, signal?: AbortSignal) => {
    const params = new URLSearchParams();
    Object.entries(filter).forEach(([name, value]) => {
      if (value !== undefined && value !== '') params.set(name, String(value));
    });
    return httpRequest<PublicPage<ChefSummary>>(`/api/public/chefs?${params}`, { signal });
  },
  chef: (id: number, signal?: AbortSignal) =>
    httpRequest<ChefDetail>(`/api/public/chefs/${id}`, { signal }),
  areas: (signal?: AbortSignal) => httpRequest<string[]>('/api/public/chefs/areas', { signal }),
  offers: (signal?: AbortSignal) => httpRequest<Offer[]>('/api/public/offers', { signal }),
  faqs: (signal?: AbortSignal) => httpRequest<Faq[]>('/api/public/faqs', { signal }),
  ticket: (input: { subject: string; message: string }) =>
    httpRequest<{ ticketCode: string; status: string }>('/api/customer/support-tickets', {
      method: 'POST', body: JSON.stringify(input),
    }),
  request: (input: CookingRequestInput, image?: File | null) => {
    const form = new FormData();
    form.append('request', new Blob([JSON.stringify(input)], { type: 'application/json' }));
    if (image) form.append('referenceImage', image);
    return httpRequest<{ requestCode: string; status: string }>('/api/customer/cooking-requests', {
      method: 'POST', body: form,
    });
  },
  adminFaqs: () => httpRequest<EditableFaq[]>('/api/admin/faqs'),
  createFaq: (input: Omit<EditableFaq, 'id'>) =>
    httpRequest<{ id: number }>('/api/admin/faqs', { method: 'POST', body: JSON.stringify(input) }),
  updateFaq: (id: number, input: Omit<EditableFaq, 'id'>) =>
    httpRequest<{ message: string }>(`/api/admin/faqs/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
  adminRequests: (page: number) => httpRequest<Array<Record<string, unknown>>>(`/api/admin/cooking-requests?page=${page}`),
};
