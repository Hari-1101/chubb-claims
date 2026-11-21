-- V2: sample users and policies for local development and testing.
-- User ids are fixed and readable so they can be used directly in the X-User-Id header.
--   Claimants: C001..C004      Staff: S001..S004
-- All names and emails are fictional.

INSERT INTO app_user (id, name, email, role, team) VALUES
    ('C001', 'Mei Ling Tan',   'meiling.tan@example.com',   'CLAIMANT', NULL),
    ('C002', 'Ahmad Rahman',   'ahmad.rahman@example.com',  'CLAIMANT', NULL),
    ('C003', 'Somchai Wong',   'somchai.wong@example.com',  'CLAIMANT', NULL),
    ('C004', 'Olivia Hughes',  'olivia.hughes@example.com', 'CLAIMANT', NULL),
    ('S001', 'Daniel Lim',     'daniel.lim@example.com',    'STAFF',    'Motor'),
    ('S002', 'Priya Nair',     'priya.nair@example.com',    'STAFF',    'Motor'),
    ('S003', 'Kenji Ho',       'kenji.ho@example.com',      'STAFF',    'Property'),
    ('S004', 'Sarah Wijaya',   'sarah.wijaya@example.com',  'STAFF',    'Property');

-- 8 policies across all 6 markets, mixing MOTOR and PROPERTY.
-- Coverage limits are illustrative values that are plausible in each local currency.
INSERT INTO policy (policy_number, holder_id, market, product_type, currency, coverage_limit) VALUES
    ('POL-SG-MOT-001', 'C001', 'SG', 'MOTOR',    'SGD',        150000.00),
    ('POL-SG-PRP-001', 'C001', 'SG', 'PROPERTY', 'SGD',       1500000.00),
    ('POL-MY-MOT-001', 'C002', 'MY', 'MOTOR',    'MYR',        120000.00),
    ('POL-ID-PRP-001', 'C002', 'ID', 'PROPERTY', 'IDR',    5000000000.00),
    ('POL-TH-MOT-001', 'C003', 'TH', 'MOTOR',    'THB',       1500000.00),
    ('POL-HK-PRP-001', 'C003', 'HK', 'PROPERTY', 'HKD',       8000000.00),
    ('POL-AU-MOT-001', 'C004', 'AU', 'MOTOR',    'AUD',         80000.00),
    ('POL-AU-PRP-001', 'C004', 'AU', 'PROPERTY', 'AUD',       1200000.00);
