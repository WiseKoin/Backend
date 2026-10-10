package com.wisekoin.backend.module.debt;

import com.wisekoin.backend.module.common.exception.BadRequestException;
import com.wisekoin.backend.module.common.exception.ResourceNotFoundException;
import com.wisekoin.backend.module.transaction.Transaction;
import com.wisekoin.backend.module.transaction.TransactionService;
import com.wisekoin.backend.module.transaction.dto.TransactionRequest;
import com.wisekoin.backend.module.debt.dto.DebtRequest;
import com.wisekoin.backend.module.debt.dto.DebtRepaymentRequest;
import com.wisekoin.backend.module.debt.dto.DebtUpdateRequest;
import com.wisekoin.backend.module.transaction.TransactionRepository;
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
    private final TransactionService transactionService;
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

        transactionService.createTransaction(userId, txRequest);

        return getDebtById(userId, debtId);
    }

    @Transactional
    public void applyRepayment(Long debtId, BigDecimal amount) {
        adjustRepayment(debtId, amount);
    }

    @Transactional
    public void adjustRepayment(Long debtId, BigDecimal amount) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new ResourceNotFoundException("Debt not found"));

        BigDecimal newRemaining = debt.getRemainingAmount().subtract(amount);
        if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Repayment amount exceeds remaining debt");
        }
        if (newRemaining.compareTo(debt.getTotalAmount()) > 0) {
            throw new BadRequestException("Debt repayment reversal exceeds total amount");
        }

        debt.setRemainingAmount(newRemaining);
        refreshStatus(debt);
        debtRepository.save(debt);
    }

    @Transactional
    public Debt updateDebt(Long userId, Long debtId, DebtRequest request) {
        Debt debt = getDebtById(userId, debtId);
        applyUpdate(debt, request.getCounterpartName(), request.getType(), request.getTotalAmount(),
                request.getDueDate(), request.getNote());
        return debtRepository.save(debt);
    }

    @Transactional
    public Debt patchDebt(Long userId, Long debtId, DebtUpdateRequest request) {
        Debt debt = getDebtById(userId, debtId);
        String counterpartName = request.getCounterpartName() != null
                ? request.getCounterpartName() : debt.getCounterpartName();
        Debt.DebtType type = request.getType() != null ? request.getType() : debt.getType();
        BigDecimal total = request.getTotalAmount() != null ? request.getTotalAmount() : debt.getTotalAmount();
        LocalDate dueDate = request.isDueDateSet() ? request.getDueDate() : debt.getDueDate();
        String note = request.isNoteSet() ? request.getNote() : debt.getNote();
        applyUpdate(debt, counterpartName, type, total, dueDate, note);
        return debtRepository.save(debt);
    }

    @Transactional
    public void deleteDebt(Long userId, Long debtId) {
        Debt debt = getDebtById(userId, debtId);
        if (transactionRepository.existsByDebtId(debtId)) {
            throw new BadRequestException("Cannot delete a debt that is used by transactions");
        }
        debtRepository.delete(debt);
    }

    private void applyUpdate(Debt debt, String counterpartName, Debt.DebtType type,
                             BigDecimal totalAmount, LocalDate dueDate, String note) {
        if (counterpartName == null || counterpartName.isBlank()) {
            throw new BadRequestException("Counterpart name cannot be blank");
        }
        if (type == null || totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Debt type and a positive total amount are required");
        }
        if (type != debt.getType() && transactionRepository.existsByDebtId(debt.getId())) {
            throw new BadRequestException("Cannot change the type of a debt with repayment transactions");
        }

        BigDecimal paidAmount = debt.getTotalAmount().subtract(debt.getRemainingAmount());
        if (totalAmount.compareTo(paidAmount) < 0) {
            throw new BadRequestException("Total amount cannot be less than the amount already repaid");
        }
        debt.setCounterpartName(counterpartName);
        debt.setType(type);
        debt.setTotalAmount(totalAmount);
        debt.setRemainingAmount(totalAmount.subtract(paidAmount));
        debt.setDueDate(dueDate);
        debt.setNote(note);
        refreshStatus(debt);
    }

    private void refreshStatus(Debt debt) {
        if (debt.getRemainingAmount().compareTo(BigDecimal.ZERO) == 0) {
            debt.setStatus(Debt.DebtStatus.COMPLETED);
        } else if (debt.getDueDate() != null && debt.getDueDate().isBefore(LocalDate.now())) {
            debt.setStatus(Debt.DebtStatus.OVERDUE);
        } else {
            debt.setStatus(Debt.DebtStatus.PENDING);
        }
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
