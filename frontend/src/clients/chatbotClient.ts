import { httpRequest } from './httpClient';

export type ChatIntent = string;
export interface ChatSlots {
  guests: number | null;
  budget: number | null;
  perPerson: boolean;
  minUnitPrice?: number | null;
  areaCode: string | null;
  keyword: string | null;
}
export interface ChatContext { lastIntent: string; slots: ChatSlots; }
export interface ChatMenu {
  id: number; name: string; category: string; unitPrice: string;
  minGuests: number; maxGuests: number; imageUrl: string | null;
  sellerName: string; reason: string;
}
export interface ChatChef {
  id: number; displayName: string; serviceArea: string;
  fromPrice: string; rating: number; specialty: string;
}
export interface ChatReply {
  intent: ChatIntent; confidence: number; message: string;
  menus: ChatMenu[]; chefs: ChatChef[];
  helpUrl: string | null; context: ChatContext;
}
export const chatbotClient = {
  send: (message: string, context: ChatContext | null, personalized: boolean) =>
    httpRequest<ChatReply>(personalized ? '/api/customer/chatbot/message' : '/api/public/chatbot/message', {
      method: 'POST', body: JSON.stringify({ message, context }),
    }),
};
