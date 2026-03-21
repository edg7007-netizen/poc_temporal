package com.poc.temporal.lending.domain.enums

enum class InterestAccrualMethod {
    /** Interest is calculated and posted daily based on outstanding principal */
    DAILY,

    /** A fixed interest amount is charged upfront when the loan is disbursed */
    FIXED_UPFRONT
}
