import type { WorkflowRequest } from '../clients/workflowClient';

type Row = WorkflowRequest & {myPrice?: number; myOfferStatus?: string};
export type ChefRequestFilter = 'all' | 'pending' | 'quoted' | 'accepted' | 'closed';

// Pure function keeps filtering testable. Source records stay untouched and always remain chef-scoped by the API.
export function filterChefRequests<T extends Row>(rows: T[], filters: {query: string; status: ChefRequestFilter; area: string}): T[] {
  const search = filters.query.trim().toLocaleLowerCase('vi-VN');
  return rows.filter(row => {
    if (filters.area && (row.serviceArea || '').split('|',1)[0] !== filters.area) return false;
    const matchesStatus = filters.status === 'all'
      || (filters.status === 'pending' && row.status !== 'CLOSED' && !row.myPrice)
      || (filters.status === 'quoted' && row.status !== 'CLOSED' && !!row.myPrice)
      || (filters.status === 'accepted' && row.myOfferStatus === 'ACCEPTED')
      || (filters.status === 'closed' && row.status === 'CLOSED');
    if (!matchesStatus) return false;
    if (!search) return true;
    return [row.requestCode, row.customerName, row.desiredDishes, row.cuisine, row.address]
      .some(text => String(text ?? '').toLocaleLowerCase('vi-VN').includes(search));
  });
}
