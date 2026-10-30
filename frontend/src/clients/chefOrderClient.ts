import { httpRequest } from './httpClient';
import type { ChefOrder, ChefOrderDetail, ChefOrderPage, ChefOrderStatus, ChefOrderSummary, ChefReceipt } from '../types/chefOrder';
const path='/api/seller/orders';
export const chefOrderClient={
  list:(params:{status?:string;q?:string;serviceType?:string;page?:number;size?:number;from?:string;to?:string},signal?:AbortSignal) => {
    const q=new URLSearchParams();
    Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!=='')q.set(key,String(value));});
    return httpRequest<ChefOrderPage>(`${path}?${q}`,{signal});
  },
  summary:(signal?:AbortSignal)=>httpRequest<ChefOrderSummary>(`${path}/summary`,{signal}),
  get:(id:number,signal?:AbortSignal)=>httpRequest<ChefOrderDetail>(`${path}/${id}`,{signal}),
  transition:(order:ChefOrder,targetStatus:ChefOrderStatus,reason='')=>httpRequest<ChefOrderDetail>(`${path}/${order.id}/transitions`,{
    method:'POST',body:JSON.stringify({expectedStatus:order.status,targetStatus,version:order.version,reason}),
  }),
  uploadReceipt:(id:number,file:File,note:string,evidenceType:'INGREDIENT_RECEIPT'|'FINISHED_MEAL')=>{
    const body=new FormData();body.append('file',file);body.append('note',note);body.append('evidenceType',evidenceType);
    return httpRequest<ChefReceipt>(`${path}/${id}/receipts`,{method:'POST',body});
  },
  removeReceipt:(id:number,receiptId:number)=>httpRequest<void>(`${path}/${id}/receipts/${receiptId}`,{method:'DELETE'}),
};
