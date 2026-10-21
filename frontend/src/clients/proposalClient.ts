import { ApiError, httpRequest } from './httpClient';
import type {
  AcceptResult,
  CreateProposalRequest,
  PatchProposalRequest,
  ProposalCard,
  ProposalList,
  ProposalSort,
  ProposalView,
} from '../types/proposal';

export const proposalClient = {
  create: (requestId: number, request: CreateProposalRequest) =>
    httpRequest<ProposalView>(`/api/custom-requests/${requestId}/proposals`, {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  update: (requestId: number, proposalId: number, request: PatchProposalRequest) =>
    httpRequest<ProposalView>(`/api/custom-requests/${requestId}/proposals/${proposalId}`, {
      method: 'PATCH',
      body: JSON.stringify(request),
    }),

  list: (requestId: number, sort: ProposalSort = 'recommended', signal?: AbortSignal) =>
    httpRequest<ProposalList>(
      `/api/custom-requests/${requestId}/proposals?${new URLSearchParams({ sort })}`,
      { signal },
    ),

  get: (proposalId: number, signal?: AbortSignal) =>
    httpRequest<ProposalCard>(`/api/proposals/${proposalId}`, { signal }),

  accept: (proposalId: number) =>
    httpRequest<AcceptResult>(`/api/proposals/${proposalId}/accept`, { method: 'POST' }),
};

const MESSAGES: Record<string, string> = {
  '1100': 'Voucher chưa được hỗ trợ.',
  '1101': 'Không tìm thấy yêu cầu này.',
  '1102': 'Không tìm thấy báo giá.',
  '1103': 'Bạn không nằm trong danh sách đầu bếp được ghép cho yêu cầu này.',
  '1104': 'Yêu cầu này không còn nhận báo giá.',
  '1105': 'Giá báo phải lớn hơn 0.',
  '1106': 'Hạn báo giá phải ở tương lai và trong giới hạn cho phép.',
  '1107': 'Bạn đã có một báo giá đang hoạt động cho yêu cầu này.',
  '1108': 'Báo giá vừa được thay đổi. Hãy tải lại rồi thử lại.',
  '1109': 'Báo giá ở trạng thái hiện tại không thể thực hiện thao tác này.',
  '1110': 'Báo giá đã hết hạn.',
  '1111': 'Bạn không có quyền với báo giá này.',
  '1112': 'Đầu bếp này hiện không còn hoạt động.',
  '1113': 'Yêu cầu này đã chấp nhận một báo giá khác.',
  '1114': 'Khung giờ này không còn trống. Vui lòng chọn báo giá hoặc thời gian khác.',
  '1115': 'Tính năng này chưa sẵn sàng. Vui lòng thử lại sau.',
  '1116': 'Dữ liệu chưa hợp lệ. Kiểm tra lại các trường bắt buộc.',
  '1117': 'Không thể lưu báo giá lúc này. Vui lòng thử lại.',
};

export function proposalError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.';
    if (error.status === 403 && !error.code) return 'Tài khoản này không có quyền thực hiện thao tác này.';
    if (error.code && MESSAGES[error.code]) return MESSAGES[error.code];
  }
  return error instanceof Error ? error.message : 'Không thể kết nối hệ thống. Vui lòng thử lại.';
}
