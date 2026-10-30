import { httpRequest } from './httpClient';
export interface OrderReview {id:number;rating:number;body:string;createdAt:string;customerName?:string}
export const reviewClient = {
  own:(orderId:number)=>httpRequest<OrderReview[]>(`/api/customer/orders/${orderId}/review`),
  publish:(orderId:number,input:{rating:number;body:string})=>httpRequest<{id:number;status:string}>(`/api/customer/orders/${orderId}/review`,{method:'POST',body:JSON.stringify(input)}),
  byChef:(chefId:number)=>httpRequest<OrderReview[]>(`/api/public/chefs/${chefId}/reviews`),
};
