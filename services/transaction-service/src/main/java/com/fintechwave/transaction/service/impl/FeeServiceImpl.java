package com.fintechwave.transaction.service.impl;

import com.fintechwave.transaction.domain.enums.TransactionType;
import com.fintechwave.transaction.service.IFeeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Default fee calculation — flat percentage per transaction type.
 * Values are placeholders; replace with config-driven rules via Config Server.
 *
 * Fee schedule (Phase 2 defaults):
 *   P2P:      0.5%  (min $0.10)
 *   CASH_IN:  0%    (Stripe processing cost absorbed)
 *   CASH_OUT: 1%    (covers Stripe Instant Payout fee)
 *   BILL_PAY: 0%    (covered by aggregator margin)
 */
@Service
@Slf4j
public class FeeServiceImpl implements IFeeService {

    private static final BigDecimal P2P_FEE_RATE    = new BigDecimal("0.005"); // 0.5%
    private static final BigDecimal CASHOUT_FEE_RATE = new BigDecimal("0.010"); // 1.0%
    private static final BigDecimal P2P_MIN_FEE     = new BigDecimal("0.10");

    @Override
    public BigDecimal calculateFee(TransactionType type, BigDecimal amount, String currency) {
        BigDecimal fee = switch (type) {
            case P2P -> {
                BigDecimal calculated = amount.multiply(P2P_FEE_RATE).setScale(4, RoundingMode.HALF_UP);
                yield calculated.max(P2P_MIN_FEE);
            }
            case CASH_OUT -> amount.multiply(CASHOUT_FEE_RATE).setScale(4, RoundingMode.HALF_UP);
            case CASH_IN, BILL_PAY -> BigDecimal.ZERO;
        };

        log.debug("Fee calculated: type={} amount={} fee={} currency={}", type, amount, fee, currency);
        return fee;
    }
}
