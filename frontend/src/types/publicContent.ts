export interface PublicPage<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ChefSummary {
  id: number;
  displayName: string;
  bio: string | null;
  serviceArea: string | null;
  rating: number | null;
  avatarUrl: string | null;
  menuCount: number;
  fromPrice: number | null;
  specialties: string | null;
}
export interface ChefMenu {
  id: number;
  name: string;
  category: string;
  unitPrice: number;
  imageUrl: string | null;
  minGuests: number;
  maxGuests: number;
}
export interface ChefDetail extends ChefSummary { menus: ChefMenu[]; }
export interface Offer {
  code: string;
  description: string | null;
  discountType: 'PERCENT' | 'FIXED';
  discountValue: number;
  minOrderAmount: number;
  maxDiscount: number | null;
  endsAt: string;
}
export interface Faq { id: number; question: string; answer: string; category: string; }
export interface EditableFaq extends Faq { displayOrder: number; active: boolean; }
export interface CookingRequestInput {
  desiredDishes: string;
  cuisine: string;
  adults: number;
  children: number;
  avoid: string;
  spice: string;
  mealDate: string;
  mealTime: string;
  address: string;
  equipment: boolean;
  budgetMin: number;
  budgetMax: number;
  preferredChefId?: number;
  preferredMenuId?: number;
  serviceArea?: string;
}
