package com.wisekoin.backend.module.transaction.mapper;

import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.dto.TransactionResponse;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {
    public TransactionResponse toResponse(Transaction transaction) {
        TransactionResponse.TransactionResponseBuilder builder =
                TransactionResponse.builder()
                        .id(transaction.getId())
                        .amount(transaction.getAmount())
                        .type(transaction.getType())
                        .note(transaction.getNote())
                        .transactionDate(transaction.getTransactionDate())
                        .wallet(
                                TransactionResponse.WalletInfo.builder()
                                        .id(transaction.getWallet().getId())
                                        .name(transaction.getWallet().getName())
                                        .build())
                        .category(
                                TransactionResponse.CategoryInfo.builder()
                                        .id(transaction.getCategory().getId())
                                        .name(transaction.getCategory().getName())
                                        .icon(transaction.getCategory().getIcon())
                                        .build());

        if (transaction.getDebt() != null) {
            builder.debt(
                    TransactionResponse.DebtInfo.builder()
                            .id(transaction.getDebt().getId())
                            .counterpartName(transaction.getDebt().getCounterpartName())
                            .build());
        }

        if (transaction.getDestinationWallet() != null) {
            builder.destinationWallet(
                    TransactionResponse.WalletInfo.builder()
                            .id(transaction.getDestinationWallet().getId())
                            .name(transaction.getDestinationWallet().getName())
                            .build());
        }

        if (transaction.getGoal() != null) {
            builder.goal(
                    TransactionResponse.GoalInfo.builder()
                            .id(transaction.getGoal().getId())
                            .name(transaction.getGoal().getName())
                            .build());
        }

        return builder.build();
    }
}
