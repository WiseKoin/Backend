package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionRepository;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.debt.dto.DebtRequest;
import com.wisekoin.backend.module.debt.dto.DebtRepaymentRequest;
import com.wisekoin.backend.module.user.User;
import com.wisekoin.backend.module.user.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DebtService {

    private final DebtRepository debtRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public Debt createDebt(Long userId, DebtRequest request) {
        Debt debt = new Debt();
        User userProxy = userRepository.getReferenceById(userId);

        debt.setUser(userProxy);
        debt.setCounterpartName(request.getCounterpartName());
        debt.setType(request.getType());
        debt.setTotalAmount(request.getTotalAmount());
        debt.setRemainingAmount(request.getTotalAmount());
        debt.setDueDate(request.getDueDate());
        debt.setNote(request.getNote());
        debt.setStatus(Debt.DebtStatus.PENDING);

        if (request.getDueDate() != null && request.getDueDate().isBefore(LocalDate.now())) {
            debt.setStatus(Debt.DebtStatus.OVERDUE);
        }

        return debtRepository.save(debt);
    }

    public List<Debt> getDebts(Long userId, Debt.DebtStatus status) {
        List<Debt> debts;
        if (status != null) {
            debts = debtRepository.findAllByUserIdAndStatusInOrderByDueDateAsc(userId, List.of(status));
        } else {
            debts = debtRepository.findAllByUserIdAndStatusInOrderByDueDateAsc(userId,
                    List.of(Debt.DebtStatus.PENDING, Debt.DebtStatus.OVERDUE));
        }
        refreshOverdue(debts);
        return debts;
    }

    public Debt getDebtById(Long userId, Long debtId) {
        return debtRepository.findById(debtId)
                .filter(d -> d.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Debt not found or access denied"));
    }

    @Transactional
    public Debt repay(Long userId, Long debtId, DebtRepaymentRequest request) {
        Debt debt = getDebtById(userId, debtId);

        if (debt.getStatus() == Debt.DebtStatus.COMPLETED) {
            throw new BadRequestException("Debt is already completed");
        }

        TransactionRequest txRequest = new TransactionRequest();
        txRequest.setWalletId(request.getWalletId());
        txRequest.setCategoryId(request.getCategoryId());
        txRequest.setAmount(request.getAmount());
        txRequest.setType(debt.getType() == Debt.DebtType.LEND
                ? Transaction.TransactionType.INCOME
                : Transaction.TransactionType.EXPENSE);
        txRequest.setNote(request.getNote() != null
                ? request.getNote()
                : "Repayment - " + debt.getCounterpartName());
        txRequest.setDebtId(debtId);

        Transaction transaction = new Transaction();
        User userProxy = userRepository.getReferenceById(userId);
        transaction.setUser(userProxy);
        transaction.setAmount(txRequest.getAmount());
        transaction.setType(txRequest.getType());
        transaction.setNote(txRequest.getNote());
        transaction.setDebt(debtRepository.getReferenceById(debtId));
        transaction.setTransactionDate(java.time.LocalDateTime.now());

        transactionRepository.save(transaction);

        return getDebtById(userId, debtId);
    }

    @Transactional
    public void applyRepayment(Long debtId, BigDecimal amount) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new ResourceNotFoundException("Debt not found"));

        if (debt.getStatus() == Debt.DebtStatus.COMPLETED) {
            throw new BadRequestException("Debt is already completed");
        }
        if (amount.compareTo(debt.getRemainingAmount()) > 0) {
            throw new BadRequestException("Repayment amount exceeds remaining debt");
        }

        debt.setRemainingAmount(debt.getRemainingAmount().subtract(amount));
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(Debt.DebtStatus.COMPLETED);
        }
        debtRepository.save(debt);
    }

    public void refreshOverdue(List<Debt> debts) {
        for (Debt debt : debts) {
            if (debt.getStatus() == Debt.DebtStatus.PENDING
                    && debt.getDueDate() != null
                    && debt.getDueDate().isBefore(LocalDate.now())) {
                debt.setStatus(Debt.DebtStatus.OVERDUE);
                debtRepository.save(debt);
            }
        }
    }

}
