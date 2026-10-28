export type ChefOrderStatus = 'PENDING' | 'CONFIRMED' | 'PREPARING' | 'COOKING' |
  'AWAITING_ACCEPTANCE' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';
export const chefStatusLabels: Record<ChefOrderStatus, string> = {
  PENDING:'Chờ nhận', CONFIRMED:'Đã nhận', PREPARING:'Chuẩn bị nguyên liệu',
  COOKING:'Đang phục vụ', AWAITING_ACCEPTANCE:'Chờ khách nghiệm thu',
  COMPLETED:'Hoàn thành', REJECTED:'Đã từ chối', CANCELLED:'Đã hủy',
};
export interface ChefOrder {
  id:number; version:number; orderCode:string; customerName:string;
  productName:string; serviceType:'MENU'|'BUDGET'; customerPhone:string|null;
  serviceAddress:string|null; customerNote:string|null; guestCount:number;
  scheduledAt:string; durationMinutes:number; totalAmount:number;
  status:ChefOrderStatus; escrowStatus:string|null; rejectedReason:string|null;
  createdAt:number|null; chefCompletedAt:string|null;
}
export interface ChefOrderPage {
  content:ChefOrder[]; page:number; size:number; totalElements:number; totalPages:number;
}
export interface ChefOrderSummary {
  pending:number; active:number; awaitingAcceptance:number; completed:number;
  today:number; recent:ChefOrder[];
}
export interface ChefHistory {
  previousStatus:string|null; newStatus:ChefOrderStatus; note:string|null; changedAt:string;
}
export interface ChefReceipt {
  id:number; filename:string; mimeType:string; note:string; fileUrl:string;
}
export interface ChefOrderDetail {
  order:ChefOrder; history:ChefHistory[]; receipts:ChefReceipt[];
}
