
import { httpRequest } from './httpClient';
import type { OrderEvidence } from '../components/common/OrderEvidenceGallery';
import type {
  CustomerOrder,
  CustomerOrdersResponse,
} from '../types/customerOrder';

export const customerOrderClient = {
  cancel:(id:number, reason:string)=>httpRequest<{status:string;refundStatus:string}>(`/api/customer/orders/${id}/cancel`,{method:'POST',body:JSON.stringify({reason})}),
  evidence: (id: number, signal?: AbortSignal) =>
    httpRequest<OrderEvidence[]>(`/api/customer/orders/${id}/evidence`, { signal }),
  accept: (id:number) => httpRequest<{status:string}>(`/api/customer/orders/${id}/accept`,{method:"POST"}),
  list(
    params: { status?: string; page?: number; size?: number },
    signal?: AbortSignal,
  ): Promise<CustomerOrdersResponse> {
    const search = new URLSearchParams();

    if (params.status) {
      search.set('status', params.status);
    }

    if (params.page !== undefined) {
      search.set('page', String(params.page));
    }

    if (params.size !== undefined) {
      search.set('size', String(params.size));
    }

    const query = search.toString();

    return httpRequest<CustomerOrdersResponse>(
      `/api/customer/orders${query ? `?${query}` : ''}`,
      { signal },
    );
  },

  detail(
    id: number,
    signal?: AbortSignal,
  ): Promise<CustomerOrder> {
    return httpRequest<CustomerOrder>(
      `/api/customer/orders/${id}`,
      { signal },
    );
  },
};
