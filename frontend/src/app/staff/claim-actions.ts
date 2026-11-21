import { ClaimStatus, ClaimSummary, CLOSED_STATUSES } from '../core/models/api.models';

export type StaffAction = 'assign' | 'requestInformation' | 'changeReserve' | 'approve' | 'reject' | 'settle';

/**
 * Which staff actions to SHOW for a claim. This mirrors the backend rules (ClaimStatus.allowedNextStatuses() and
 * "only the assigned officer can act") so we don't offer buttons that would just fail. The backend stays the
 * authority: if this list is ever out of date, the backend still rejects the action with a 409/403.
 */
const ACTIONS_BY_STATUS: Record<ClaimStatus, StaffAction[]> = {
  SUBMITTED: [], // only "assign" (below): a SUBMITTED claim has no officer yet
  UNDER_REVIEW: ['requestInformation', 'changeReserve', 'approve', 'reject'],
  INFO_REQUESTED: ['changeReserve'], // waiting for the claimant
  APPROVED: ['changeReserve', 'settle'],
  SETTLED: [],
  REJECTED: [],
};

export function availableActions(
  claim: Pick<ClaimSummary, 'status' | 'assignedOfficerId'>,
  userId: string,
): StaffAction[] {
  if (claim.status === 'SUBMITTED' && !claim.assignedOfficerId) {
    return ['assign'];
  }
  if (claim.assignedOfficerId !== userId) {
    return [];
  }
  return ACTIONS_BY_STATUS[claim.status];
}

export function isOpen(status: ClaimStatus): boolean {
  return !CLOSED_STATUSES.includes(status);
}
