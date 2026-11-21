// TypeScript shapes of the backend DTOs (see http://localhost:8080/v3/api-docs).
// Money arrives as JSON numbers; timestamps as ISO-8601 strings; dates as 'YYYY-MM-DD'.

export type Role = 'CLAIMANT' | 'STAFF';
export type Market = 'SG' | 'MY' | 'HK' | 'TH' | 'ID' | 'AU';
export type ProductType = 'MOTOR' | 'PROPERTY';
export type ClaimStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'INFO_REQUESTED' | 'APPROVED' | 'SETTLED' | 'REJECTED';
export type AssignmentFilter = 'me' | 'unassigned' | 'any';

export const MARKETS: Market[] = ['SG', 'MY', 'HK', 'TH', 'ID', 'AU'];
export const PRODUCT_TYPES: ProductType[] = ['MOTOR', 'PROPERTY'];
export const CLAIM_STATUSES: ClaimStatus[] = ['SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'APPROVED', 'SETTLED', 'REJECTED'];
export const CLOSED_STATUSES: ClaimStatus[] = ['SETTLED', 'REJECTED'];

export interface Policy {
  policyNumber: string;
  market: Market;
  productType: ProductType;
  currency: string;
  coverageLimit: number;
}

export interface ClaimSummary {
  claimNumber: string;
  policyNumber: string;
  type: ProductType;
  market: Market;
  currency: string;
  incidentDate: string;
  estimatedAmount: number;
  /** Staff only: the backend omits it for claimants. */
  reserveAmount?: number;
  status: ClaimStatus;
  assignedOfficerId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface StatusHistoryEntry {
  fromStatus: ClaimStatus | null;
  toStatus: ClaimStatus;
  changedBy: string;
  reason: string | null;
  changedAt: string;
}

export interface InformationRequest {
  id: number;
  question: string;
  response: string | null;
  requestedBy: string;
  requestedAt: string;
  respondedAt: string | null;
}

export interface ClaimDetail extends ClaimSummary {
  description: string;
  statusHistory: StatusHistoryEntry[];
  informationRequests: InformationRequest[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SubmitClaimRequest {
  policyNumber: string;
  incidentDate: string;
  description: string;
  estimatedAmount: number;
}

export interface QueueFilters {
  status: ClaimStatus | '';
  market: Market | '';
  type: ProductType | '';
  assigned: AssignmentFilter;
}

export interface ClaimNotification {
  id: number;
  claimNumber: string;
  message: string;
  createdAt: string;
}

export interface TypeExposure {
  type: ProductType;
  openClaims: number;
  totalReserve: number;
}

export interface MarketExposure {
  market: Market;
  currency: string;
  openClaims: number;
  totalReserve: number;
  byType: TypeExposure[];
}

export interface ExposureReport {
  lastUpdated: string | null;
  exposures: MarketExposure[];
}

export interface OfficerWorkload {
  officerId: string;
  name: string;
  team: string;
  openClaims: number;
  openClaimsByStatus: Partial<Record<ClaimStatus, number>>;
  oldestOpenClaimSubmittedAt: string | null;
  oldestOpenClaimAgeDays: number | null;
}

export interface WorkloadReport {
  lastUpdated: string | null;
  team: string | null;
  officers: OfficerWorkload[];
  unassignedOpenClaims: number;
  teamPerformance: {
    periodDays: number;
    claimsDecided: number;
    averageHoursToDecision: number | null;
  };
}

/** RFC 7807 error body returned by the backend's GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errors?: { field: string; message: string }[];
}
