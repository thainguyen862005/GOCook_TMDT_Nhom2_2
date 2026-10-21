export interface FixedPreviewRequest {
  productId: number;
  guestCount: number;
  /** Not supported yet: any value (even empty) is rejected with error 1100. Leave it out. */
  voucherCode?: string;
}

export interface FixedMenuPreview {
  productId: number;
  productName: string;
  unitPrice: number;
  guestCount: number;
  subtotal: number;
  discount: number;
  total: number;
  currency: 'VND';
  /** ISO-8601 with offset. After this moment the shown price must be fetched again. */
  priceExpiresAt: string;
}

export type GuidanceStatus = 'OK' | 'INSUFFICIENT_DATA';

/** CHEF: median of the chef's own menus in the cuisine. MARKET: median of all published menus. */
export type GuidanceBasis = 'CHEF' | 'MARKET';

export type GuidanceBudgetPosition = 'WITHIN' | 'BELOW_RANGE' | 'ABOVE_RANGE' | 'NA';

/** How a proposed price compares with the suggested range (information only). */
export type PriceVerdict = 'IN_RANGE' | 'BELOW' | 'ABOVE' | 'NO_GUIDANCE';

export interface GuidanceFactor {
  code: 'DISH_COUNT' | 'DIETARY' | 'ALLERGY' | 'DURATION' | 'AREA';
  label: string;
  /** Signed, e.g. 8 means +8%. */
  impactPercent: number;
  explanation: string;
}

/** Chef-only price hint for a custom request. The chef decides the final price. */
export interface PriceGuidance {
  requestId: number;
  status: GuidanceStatus;
  /** NOT_ENOUGH_MENUS, NO_CUISINE or INVALID_GUEST_COUNT when status is INSUFFICIENT_DATA. */
  reason: string | null;
  currency: 'VND';
  guestCount: number;
  basis: GuidanceBasis | null;
  sampleSize: number;
  baselinePerGuest: number | null;
  suggestedMin: number | null;
  suggestedMax: number | null;
  factors: GuidanceFactor[];
  budgetPosition: GuidanceBudgetPosition;
  warning: string | null;
}
