/** Similarity is cosine content similarity, NOT probability of ordering. */
export interface RecommendedMenu {
  id: number;
  name: string;
  category: string;
  unitPrice: string;
  minGuests: number;
  maxGuests: number;
  imageUrl: string | null;
  sellerName: string;
  matchPercent: number;
  reason: string;
  basis: 'PURCHASE' | 'CART' | 'SIMILAR';
}
