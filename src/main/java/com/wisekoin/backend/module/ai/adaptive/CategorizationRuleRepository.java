package com.wisekoin.backend.module.ai.adaptive;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    Optional<CategorizationRule> findByUserIdAndMerchantKeyAndTransactionType(
            Long userId, String merchantKey, String transactionType);

    List<CategorizationRule>
            findTop50ByUserIdAndTransactionTypeOrderByConfirmationCountDescUpdatedAtDesc(
                    Long userId, String transactionType);
}
