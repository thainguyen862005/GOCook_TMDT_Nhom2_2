export type NotificationCategory = 'ACCOUNT'|'BOOKING'|'PAYMENT'|'CHEF'|'SUPPORT'|'REQUEST'|'SYSTEM'|'PROMOTION';
export interface AppNotification {
  id: number;
  type: NotificationCategory;
  title: string;
  body: string;
  actionPath: string;
  read: boolean;
  createdAt: string;
  readAt: string | null;
}
export interface NotificationPage {
  content: AppNotification[];
  page: number;
  size: number;
  totalElements: number;
}
