package com.huawei.ascend.examples.a2a.scenarios;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fixed-rate annuity calculator exposed to the loan-calculator agent as a
 * {@code file:Class#method} tool. All arithmetic is {@link BigDecimal} — the
 * monthly payment of the standard annuity formula
 * {@code P * r * (1+r)^n / ((1+r)^n - 1)} is rounded once, to cents, half-up;
 * the totals are derived exactly from that rounded payment.
 */
public final class LoanAmortizationCalculator {

    /** Bounds the non-terminating division annualRatePercent/1200; ample for currency. */
    private static final MathContext RATE_PRECISION = MathContext.DECIMAL128;

    private LoanAmortizationCalculator() {
    }

    public static Map<String, Object> calculate(Map<String, Object> inputs) {
        BigDecimal principal = decimal(inputs, "principal");
        BigDecimal annualRatePercent = decimal(inputs, "annualRatePercent");
        int termMonths = integer(inputs, "termMonths");
        if (principal.signum() <= 0) {
            throw new IllegalArgumentException("principal must be positive, got: " + principal);
        }
        if (annualRatePercent.signum() < 0) {
            throw new IllegalArgumentException("annualRatePercent must not be negative, got: " + annualRatePercent);
        }
        if (termMonths <= 0) {
            throw new IllegalArgumentException("termMonths must be positive, got: " + termMonths);
        }
        BigDecimal monthlyPayment = monthlyPayment(principal, annualRatePercent, termMonths);
        BigDecimal totalPayment = monthlyPayment.multiply(BigDecimal.valueOf(termMonths))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalInterest = totalPayment.subtract(principal).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthlyPayment", monthlyPayment.toPlainString());
        result.put("totalPayment", totalPayment.toPlainString());
        result.put("totalInterest", totalInterest.toPlainString());
        result.put("termMonths", termMonths);
        return result;
    }

    private static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRatePercent, int termMonths) {
        BigDecimal monthlyRate = annualRatePercent.divide(BigDecimal.valueOf(1200), RATE_PRECISION);
        if (monthlyRate.signum() == 0) {
            // Interest-free: the annuity formula degenerates (division by zero).
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(termMonths);
        return principal.multiply(monthlyRate).multiply(factor)
                .divide(factor.subtract(BigDecimal.ONE), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        try {
            // Via String.valueOf so a double like 4.8 arrives as the literal "4.8",
            // not its binary-float expansion.
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be a number, got: " + value, error);
        }
    }

    private static int integer(Map<String, Object> inputs, String key) {
        Object value = inputs.get(key);
        if (value instanceof Number number && number.longValue() == number.doubleValue()) {
            return Math.toIntExact(number.longValue());
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " must be an integer, got: " + value, error);
        }
    }
}
