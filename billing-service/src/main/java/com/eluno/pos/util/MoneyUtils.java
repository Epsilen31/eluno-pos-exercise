package com.eluno.pos.util;

import com.eluno.pos.exception.BillingValidationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/** Exact conversions and rounding. Application amounts are always integer paise. */
public final class MoneyUtils {
    private MoneyUtils() {
        // Utility class: callers use the static methods directly.
    }

    /** Shopify's "104.98" becomes 10498 paise without using floating-point arithmetic. */
    public static long fromDecimalString(String decimalAmount) {
        return new BigDecimal(decimalAmount).movePointRight(2).longValueExact();
    }

    /** 10498 paise becomes Shopify's decimal string "104.98". */
    public static String toDecimalString(long amountPaise) {
        return BigDecimal.valueOf(amountPaise, 2).toPlainString();
    }

    /** Calculate amount times numerator divided by denominator, rounding half-up to one paisa. */
    public static long multiplyAndDivideWithRounding(
            long amount, long numerator, long denominator) {
        if (amount < 0 || numerator < 0 || denominator <= 0) {
            throw new BillingValidationException("Invalid money ratio");
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * Split a discount in proportion to eligible line prices. Leftover paise go to the largest
     * fractional shares, with original line order breaking ties. Splitting 2 over [1,1,1] gives
     * [1,1,0].
     */
    public static long[] allocateProportionally(long discountPaise, long[] linePricesPaise) {
        long eligibleTotalPaise = 0;
        for (long linePricePaise : linePricesPaise) {
            if (linePricePaise < 0) {
                throw new BillingValidationException("Negative allocation weight");
            }
            eligibleTotalPaise = Math.addExact(eligibleTotalPaise, linePricePaise);
        }
        if (discountPaise < 0 || discountPaise > eligibleTotalPaise) {
            throw new BillingValidationException("Discount exceeds eligible inclusive total");
        }

        long[] lineDiscountsPaise = new long[linePricesPaise.length];
        if (eligibleTotalPaise == 0) {
            return lineDiscountsPaise;
        }

        BigInteger[] fractionalRemainders = new BigInteger[linePricesPaise.length];
        long allocatedPaise = 0;
        // Assign whole-paisa shares first, retaining fractional remainders for the next step.
        for (int lineIndex = 0; lineIndex < linePricesPaise.length; lineIndex++) {
            BigInteger weightedDiscount =
                    BigInteger.valueOf(discountPaise)
                            .multiply(BigInteger.valueOf(linePricesPaise[lineIndex]));
            BigInteger[] division =
                    weightedDiscount.divideAndRemainder(BigInteger.valueOf(eligibleTotalPaise));
            lineDiscountsPaise[lineIndex] = division[0].longValueExact();
            fractionalRemainders[lineIndex] = division[1];
            allocatedPaise += lineDiscountsPaise[lineIndex];
        }

        while (allocatedPaise < discountPaise) {
            int largestRemainderIndex = 0;
            for (int lineIndex = 1; lineIndex < fractionalRemainders.length; lineIndex++) {
                if (fractionalRemainders[lineIndex].compareTo(
                                fractionalRemainders[largestRemainderIndex])
                        > 0) {
                    largestRemainderIndex = lineIndex;
                }
            }
            lineDiscountsPaise[largestRemainderIndex]++;
            fractionalRemainders[largestRemainderIndex] = BigInteger.valueOf(-1);
            allocatedPaise++;
        }
        return lineDiscountsPaise;
    }
}
