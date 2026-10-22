import type { PriceVerdict } from './pricing';

export type ProposalStatus =
  | 'DRAFT'
  | 'SENT'
  | 'REVISED'
  | 'WITHDRAWN'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'EXPIRED';

export type ProposalSort = 'recommended' | 'price' | 'rating';

export interface CreateProposalRequest {
  proposedPrice: number;
  dishes?: string;
  note?: string;
  /** ISO-8601 with offset, in the future and within the allowed window. */
  expiresAt: string;
  /** true sends right away (SENT), otherwise the proposal stays a DRAFT. */
  send?: boolean;
}

export interface PatchProposalRequest {
  /** The version of the proposal the chef last saw. */
  version: number;
  proposedPrice?: number;
  dishes?: string;
  note?: string;
  expiresAt?: string;
  /** WITHDRAW cannot be combined with content changes. */
  action?: 'SEND' | 'WITHDRAW';
}

/** What the chef gets back from create and update. */
export interface ProposalView {
  id: number;
  customRequestId: number;
  chefId: number;
  proposedPrice: number;
  dishes: string | null;
  note: string | null;
  expiresAt: string;
  revision: number;
  version: number;
  status: ProposalStatus;
  priceGuidance: PriceVerdict;
}

export interface ChefSummary {
  id: number;
  displayName: string;
  rating: number;
  verified: boolean;
  active: boolean;
}

/** Positive amount means over the customer's budget ceiling. */
export interface BudgetDifference {
  amount: number;
  percent: number;
  withinBudget: boolean;
}

/** One proposal as the customer (or its author chef) sees it. */
export interface ProposalCard {
  id: number;
  requestId: number;
  chef: ChefSummary | null;
  proposedPrice: number;
  budgetDifference: BudgetDifference | null;
  dishes: string | null;
  note: string | null;
  expiresAt: string;
  /** Already reflects expiry: SENT/REVISED past expiresAt arrive as EXPIRED. */
  status: ProposalStatus;
  revision: number;
  version: number;
  /** Live, not expired and the chef is still active: the only case where accept can succeed. */
  acceptable: boolean;
  bookingId: number | null;
  orderCode: string | null;
}

export interface ProposalList {
  requestId: number;
  budgetMin: number | null;
  budgetMax: number | null;
  count: number;
  items: ProposalCard[];
}

export interface AcceptResult {
  proposalId: number;
  bookingId: number;
  /** Code of the booking the customer pays next. */
  orderCode: string;
  totalAmount: number;
}
