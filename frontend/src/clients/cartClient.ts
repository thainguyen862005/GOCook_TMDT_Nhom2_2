import { httpRequest } from './httpClient';
export interface CartItem {
  id: number; productId: number; guestCount: number; note: string | null;
  name: string; category: string; unitPrice: number; imageUrl: string | null;
  minGuests: number; maxGuests: number; available: boolean;
}
export const cartClient = {
  list: () => httpRequest<CartItem[]>('/api/customer/cart'),
  add: (productId: number, guestCount: number, note = '') => httpRequest<{id: number; status: string}>('/api/customer/cart', {
    method: 'POST', body: JSON.stringify({productId, guestCount, note}),
  }),
  update: (itemId: number, guestCount: number, note: string) => httpRequest<{status:string}>(`/api/customer/cart/${itemId}`,{
    method: 'PATCH', body: JSON.stringify({guestCount, note}),
  }),
  remove: (itemId: number) => httpRequest<void>(`/api/customer/cart/${itemId}`, {method:'DELETE'}),
};
