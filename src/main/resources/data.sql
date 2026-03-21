-- Seed two representative loan products

-- 1. Term Loan: 12-month installment loan, daily interest accrual, 18% APR, single withdrawal
INSERT INTO loan_products (
    name, product_type, interest_accrual_method, annual_interest_rate, flat_interest_rate,
    number_of_payment_cycles, allows_multiple_withdrawals, max_loan_amount, min_loan_amount,
    cooldown_period_days, late_fee_rate, active
) VALUES (
    'Personal Term Loan', 'TERM_LOAN', 'DAILY', 0.180000, NULL,
    12, FALSE, 50000.00, 500.00,
    30, 0.050000, TRUE
);

-- 2. Bullet Loan: 3-month single-payment loan, 8% flat upfront interest, single withdrawal
INSERT INTO loan_products (
    name, product_type, interest_accrual_method, annual_interest_rate, flat_interest_rate,
    number_of_payment_cycles, allows_multiple_withdrawals, max_loan_amount, min_loan_amount,
    cooldown_period_days, late_fee_rate, active
) VALUES (
    'Short-Term Bullet Loan', 'BULLET_LOAN', 'FIXED_UPFRONT', 0.320000, 0.080000,
    3, FALSE, 10000.00, 100.00,
    15, 0.050000, TRUE
);
