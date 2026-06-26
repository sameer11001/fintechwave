package com.fintechwave.transaction.api.grpc;

import com.fintechwave.core.exception.BusinessRuleException;
import com.fintechwave.core.exception.ResourceNotFoundException;
import com.fintechwave.core.exception.ServiceUnavailableException;
import com.fintechwave.grpc.ledger.LedgerServiceGrpc;
import com.fintechwave.grpc.ledger.ReserveFundsRequest;
import com.fintechwave.grpc.ledger.ReserveFundsResponse;
import com.fintechwave.transaction.exception.InvalidTransactionStateException;
import com.fintechwave.transaction.exception.PaymentGatewayException;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@Slf4j
public class LedgerGrpcClient {

    @GrpcClient("ledger-service")
    private LedgerServiceGrpc.LedgerServiceBlockingStub ledgerServiceStub;

    @Retryable(
        retryFor = { ServiceUnavailableException.class, StatusRuntimeException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void reserveFundsSync(UUID transactionId, UUID userId, BigDecimal amount, String currency) {
        log.info("Calling LedgerService gRPC to reserve funds: txId={} amount={}", transactionId, amount);

        ReserveFundsRequest request = ReserveFundsRequest.newBuilder()
                .setTransactionId(transactionId.toString())
                .setUserId(userId.toString())
                .setAmount(amount.doubleValue())
                .setCurrency(currency)
                .build();

        try {
            ReserveFundsResponse response = ledgerServiceStub.reserveFunds(request);
            if (!response.getSuccess()) {
                log.warn("Ledger reservation failed via gRPC: txId={} reason={}", transactionId, response.getMessage());
                // This is a business-level rejection (e.g. insufficient funds, wallet not found)
                throw new InvalidTransactionStateException(
                        "Insufficient funds or ledger error: " + response.getMessage());
            }
            log.info("Ledger reservation succeeded: txId={}", transactionId);
        } catch (InvalidTransactionStateException e) {
            throw e; // re-throw domain exceptions as-is
        } catch (StatusRuntimeException e) {
            // Translate gRPC status codes to domain exceptions.
            // gRPC code → what the ledger-service means → correct domain exception:
            switch (e.getStatus().getCode()) {

                // FAILED_PRECONDITION = InsufficientBalanceException from ledger-service.
                // This is a business rule violation, not an infrastructure failure.
                case FAILED_PRECONDITION -> {
                    log.warn("Insufficient balance reported by ledger-service for txId={}: {}",
                            transactionId, e.getStatus().getDescription());
                    throw new BusinessRuleException("INSUFFICIENT_BALANCE",
                            e.getStatus().getDescription() != null
                                    ? e.getStatus().getDescription()
                                    : "Insufficient balance to complete this transaction");
                }

                // NOT_FOUND = WalletNotFoundException — user account not provisioned yet.
                case NOT_FOUND -> {
                    log.warn("Wallet not found in ledger for txId={}: {}",
                            transactionId, e.getStatus().getDescription());
                    throw new ResourceNotFoundException(
                            e.getStatus().getDescription() != null
                                    ? e.getStatus().getDescription()
                                    : "Wallet not found. Please ensure your account is fully provisioned.");
                }

                // INVALID_ARGUMENT = malformed UUID or currency sent to ledger-service.
                case INVALID_ARGUMENT -> {
                    log.warn("Invalid argument in ledger gRPC call for txId={}: {}",
                            transactionId, e.getStatus().getDescription());
                    throw new BusinessRuleException("INVALID_LEDGER_REQUEST",
                            "Invalid request to ledger service: " + e.getStatus().getDescription());
                }

                // UNAVAILABLE / INTERNAL = ledger-service is down or has an internal error.
                case UNAVAILABLE, INTERNAL -> {
                    log.error("Ledger service is unavailable (gRPC {}): txId={}",
                            e.getStatus().getCode(), transactionId, e);
                    throw new ServiceUnavailableException("LEDGER_UNAVAILABLE",
                            "Ledger service is temporarily unavailable. Please try again.", e);
                }

                // DEADLINE_EXCEEDED = timeout waiting for ledger-service.
                case DEADLINE_EXCEEDED -> {
                    log.error("Ledger gRPC call timed out for txId={}", transactionId, e);
                    throw new ServiceUnavailableException("LEDGER_TIMEOUT",
                            "Ledger service request timed out. Please try again.", e);
                }

                // UNAUTHENTICATED / PERMISSION_DENIED = inter-service auth misconfiguration.
                case UNAUTHENTICATED, PERMISSION_DENIED -> {
                    log.error("gRPC auth/permission error calling ledger-service for txId={}: {}",
                            transactionId, e.getStatus().getCode(), e);
                    throw new ServiceUnavailableException("LEDGER_AUTH_ERROR",
                            "Internal service authentication error. Please contact support.", e);
                }

                // Everything else is truly unexpected — surface as 503.
                default -> {
                    log.error("Unexpected gRPC status from ledger-service for txId={}: {}",
                            transactionId, e.getStatus(), e);
                    throw new ServiceUnavailableException("LEDGER_ERROR",
                            "An unexpected error occurred communicating with the ledger service.", e);
                }
            }
        } catch (Exception e) {
            log.error("Unexpected error calling LedgerService gRPC for txId={}", transactionId, e);
            throw new PaymentGatewayException("Failed to connect to ledger service: " + e.getMessage(), e);
        }
    }

    @Recover
    public void fallbackReserveFundsSync(Exception e, UUID transactionId, UUID userId, BigDecimal amount, String currency) {
        log.error("ALL RETRIES FAILED for Ledger gRPC call. txId={}. Executing fallback.", transactionId, e);
        throw new PaymentGatewayException("Ledger service unavailable after retries. Transaction queued for review.", e);
    }
}
