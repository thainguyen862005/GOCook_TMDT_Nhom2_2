export const ORDER_LABEL: Record<string, string> = {
  PENDING: 'Chờ thanh toán / Chef nhận',
  CONFIRMED: 'Đã xác nhận',
  PREPARING: 'Đang chuẩn bị',
  COOKING: 'Đang nấu',
  AWAITING_ACCEPTANCE: 'Chờ nghiệm thu',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  REJECTED: 'Đã từ chối',
};

export const PRODUCT_LABEL: Record<string, string> = {
  PUBLISHED: 'Đang hiển thị',
  DRAFT: 'Bản nháp',
  HIDDEN: 'Tạm ẩn',
};

export const ROLE_LABEL: Record<string, string> = {
  USER: 'Khách hàng',
  INSTRUCTOR: 'Người bán',
  ADMIN: 'Quản trị viên',
  COMPANY_ADMIN: 'Quản trị công ty',
};
