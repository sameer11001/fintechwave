package com.fintechwave.ledger.api.grpc;

import com.fintechwave.grpc.ledger.LedgerServiceGrpc;
import com.fintechwave.grpc.ledger.ReserveFundsRequest;
import com.fintechwave.grpc.ledger.ReserveFundsResponse;
import com.fintechwave.ledger.exception.InsufficientBalanceException;
import com.fintechwave.ledger.exception.WalletNotFoundException;
import com.fintechwave.ledger.service.ILedgerService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
@Slf4j
public class LedgerGrpcServiceImpl extends LedgerServiceGrpc.LedgerServiceImplBase {

    private final ILedgerService ledgerService;

    @Override
    public void reserveFunds(ReserveFundsRequest request, StreamObserver<ReserveFundsResponse> responseObserver) {
        log.info("Received gRPC reserve funds request for txId={}", request.getTransactionId());

        try {
            UUID transactionId = UUID.fromString(request.getTransactionId());
            UUID userId = UUID.fromString(request.getUserId());
            BigDecimal amount = BigDecimal.valueOf(request.getAmount());
            String currency = request.getCurrency();

            UUID userWalletId = ledgerService.getWalletBalance(userId).getAccountId();
            ledgerService.reserve(transactionId, userWalletId, amount, currency);

            responseObserver.onNext(ReserveFundsResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Funds reserved successfully")
                    .build());
            responseObserver.onCompleted();

        } catch (WalletNotFoundException e) {
            // The user's wallet has not been provisioned yet (e.g. KYC not yet verified).
            // Return NOT_FOUND so the client can surface a meaningful error.
            log.warn("Wallet not found during gRPC reservation for txId={}: {}", request.getTransactionId(), e.getMessage());
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Wallet not found: " + e.getMessage())
                    .asRuntimeException());

        } catch (InsufficientBalanceException e) {
            // Balance check failed — this is a business rule violation, not an infrastructure error.
            log.warn("Insufficient balance during gRPC reservation for txId={}: {}", request.getTransactionId(), e.getMessage());
            responseObserver.onError(Status.FAILED_PRECONDITION
                    .withDescription("Insufficient balance: " + e.getMessage())
                    .asRuntimeException());

        } catch (IllegalArgumentException e) {
            // Malformed UUID or currency in the gRPC request
            log.warn("Invalid gRPC reservation request for txId={}: {}", request.getTransactionId(), e.getMessage());
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Invalid request: " + e.getMessage())
                    .asRuntimeException());

        } catch (Exception e) {
            log.error("Unexpected error reserving funds via gRPC for txId={}", request.getTransactionId(), e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal ledger error — please retry")
                    .asRuntimeException());
        }
    }
}
