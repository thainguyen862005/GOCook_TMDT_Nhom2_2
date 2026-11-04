import {httpRequest} from './httpClient';
export interface Paged<T> {content:T[];page:number;size:number;totalElements:number;totalPages:number;}
export interface MenuQuestion { id:number; productId?:number; menuName?:string; question:string;
  answer:string|null; status?:'PENDING'|'ANSWERED'|'HIDDEN'; createdAt:string; answeredAt?:string; chefName?:string; }
export interface ContentReport {id:number;reporterId?:number;targetType:string;targetId:number;orderId?:number;
  reason:string;status:string;resolution?:string|null;createdAt:string;}
export const engagementClient={
  publicQuestions:(productId:number,page=0)=>httpRequest<Paged<MenuQuestion>>(`/api/public/menus/${productId}/questions?page=${page}&size=10`),
  ask:(productId:number,question:string)=>httpRequest<{id:number;status:string}>(`/api/customer/menus/${productId}/questions`,{
    method:'POST',body:JSON.stringify({question}),
  }),
  myQuestions:()=>httpRequest<MenuQuestion[]>('/api/customer/menu-questions'),
  chefQuestions:(page:number,status:string)=>httpRequest<Paged<MenuQuestion>>(`/api/seller/menu-questions?${new URLSearchParams({page:String(page),status})}`),
  answer:(id:number,answer:string)=>httpRequest<{status:string}>(`/api/seller/menu-questions/${id}/answer`,{method:'PATCH',body:JSON.stringify({answer})}),
  adminQuestions:(page:number,status:string)=>httpRequest<Paged<MenuQuestion>>(`/api/admin/menu-questions?${new URLSearchParams({page:String(page),status})}`),
  moderateQuestion:(id:number,hidden:boolean)=>httpRequest<{status:string}>(`/api/admin/menu-questions/${id}/visibility?hidden=${hidden}`,{method:'PATCH'}),
  report:(targetType:'CHEF'|'CUSTOMER'|'MENU'|'QUESTION',targetId:number,reason:string)=>httpRequest<{id:number;status:string}>('/api/reports/content',{
    method:'POST',body:JSON.stringify({targetType,targetId,reason}),
  }),
  myReports:()=>httpRequest<ContentReport[]>('/api/reports/content/mine'),
  adminReports:(page:number,status:string)=>httpRequest<Paged<ContentReport>>(`/api/admin/content-reports?${new URLSearchParams({page:String(page),status})}`),
  decideReport:(id:number,status:string,resolution:string)=>httpRequest<{status:string}>(`/api/admin/content-reports/${id}`,{
    method:'PATCH',body:JSON.stringify({status,resolution}),
  }),
};
