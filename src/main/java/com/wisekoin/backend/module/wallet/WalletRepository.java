package com.wisekoin.backend.module.wallet;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findAllByUserId(Long userId);

    @Modifying
    @Query("delete from Wallet wallet where wallet.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
