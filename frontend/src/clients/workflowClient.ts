import { httpRequest } from './httpClient';

export interface WorkflowRequest {
  id: number; requestCode: string; customerId: number; customerName: string;
  desiredDishes: string; cuisine: string; serviceArea?: string; adults: number; children: number;
  avoid: string | null; mealDate: string; mealTime: string; address: string;
  budgetMin: number; budgetMax: number; status: string;
  acceptedOrderId: number | null; acceptedOrderCode?: string | null; offerCount?: number;
  myOrderId?: number | null; myOrderCode?: string | null; myEscrowStatus?: string | null;
  acceptedOrderStatus?: string | null; acceptedEscrowStatus?: string | null;
  matches?: Array<{ chefId: number; chefName: string; serviceArea: string; rating: number; matchScore: number; matchReason: string }>;
  offers?: Array<{ id: number; chefId: number; chefName: string; price: number; dishes: string; note: string; status: string }>;
}
export interface WorkflowThread {
  kind: 'REQUEST' | 'ORDER'; scopeId: number; peerId: number; title: string; peerName: string; lastMessageAt: string | null;
}
export interface WorkflowMessage {
  id: number; senderId: number; recipientId: number; senderName: string; body: string; createdAt: string;
}
export const workflowClient = {
  threads: () => httpRequest<WorkflowThread[]>('/api/workflow/threads'),
  mine: () => httpRequest<WorkflowRequest[]>('/api/customer/cooking-requests'),
  detail: (id: number) => httpRequest<WorkflowRequest>(`/api/customer/cooking-requests/${id}`),
  chefRequests: () => httpRequest<(WorkflowRequest & { myPrice?: number; myOfferStatus?: string })[]>('/api/chef/cooking-requests'),
  offer: (id: number, body: {price: number; dishes: string; note?: string}) =>
    httpRequest(`/api/chef/cooking-requests/${id}/offers`, {method:'POST',body:JSON.stringify(body)}),
  accept: (id: number, offerId: number) =>
    httpRequest<{orderId: number; orderCode: string}>(`/api/customer/cooking-requests/${id}/offers/${offerId}/accept`, {method:'POST'}),
  match: (id: number, chefId: number) =>
    httpRequest(`/api/admin/cooking-requests/${id}/match`, {method:'POST',body:JSON.stringify({chefId})}),
  messages: (id: number, peerId: number) =>
    httpRequest<WorkflowMessage[]>(`/api/workflow/requests/${id}/messages?peerId=${peerId}`),
  send: (id: number, recipientId: number, body: string) =>
    httpRequest(`/api/workflow/requests/${id}/messages`, {method:'POST',body:JSON.stringify({recipientId,body})}),
  orderMessages: (orderId: number) => httpRequest<WorkflowMessage[]>(`/api/workflow/orders/${orderId}/messages`),
  sendOrderMessage: (orderId: number, body: string) => httpRequest(`/api/workflow/orders/${orderId}/messages`, {method:'POST',body:JSON.stringify({body})}),
  chefProfile: () => httpRequest<{displayName:string;bio:string;serviceArea:string;phone:string}>('/api/chef/profile'),
  updateChefProfile: (data:{displayName:string;bio:string;serviceArea:string;phone:string}) =>
    httpRequest('/api/chef/profile',{method:'PUT',body:JSON.stringify(data)}),
};
