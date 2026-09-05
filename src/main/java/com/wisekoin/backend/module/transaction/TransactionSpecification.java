package com.wisekoin.backend.module.transaction;

import com.wisekoin.backend.module.transaction.dto.TransactionFilterRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class TransactionSpecification {

    public static Specification<Transaction> getFilterSpec(Long userId, TransactionFilterRequest filter) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("user").get("id"), userId));

            if (filter.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("transactionDate"),
                        filter.getStartDate().atStartOfDay() // From 00:00:00
                ));
            }
            if (filter.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("transactionDate"),
                        filter.getEndDate().atTime(LocalTime.MAX) // To 23:59:59
                ));
            }
            if (filter.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), filter.getCategoryId()));
            }
            if (filter.getType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), filter.getType()));
            }
            if (filter.getDebtId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("debt").get("id"), filter.getDebtId()));
            }
            if (filter.getGoalId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("goal").get("id"), filter.getGoalId()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

}
