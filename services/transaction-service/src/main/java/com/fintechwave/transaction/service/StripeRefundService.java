package com.fintechwave.transaction.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeRefundService {

    public void processRefund(String paymentIntentId) {
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .build();
            Refund refund = Refund.create(params);
            log.info("Successfully processed Stripe refund for paymentIntentId={}, refundId={}", paymentIntentId, refund.getId());
        } catch (StripeException e) {
            log.error("Failed to process Stripe refund for paymentIntentId={}, code={}", paymentIntentId, e.getCode(), e);
            throw new RuntimeException("Failed to refund via Stripe API", e);
        }
    }
}
