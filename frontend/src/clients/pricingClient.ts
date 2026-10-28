import { httpRequest } from './httpClient';
import type { FixedMenuPreview, FixedPreviewRequest, PriceGuidance } from '../types/pricing';

export const pricingClient = {
  fixedPreview: (request: FixedPreviewRequest) =>
    httpRequest<FixedMenuPreview>('/api/pricing/fixed-preview', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  /** Chef only; the chef must be matched to the request. Answers 501 until the custom request module plugs in ProposalRequestPort. */
  priceGuidance: (requestId: number, signal?: AbortSignal) =>
    httpRequest<PriceGuidance>(`/api/custom-requests/${requestId}/price-guidance`, { signal }),
};
