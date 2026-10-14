package com.wisekoin.backend.module.user.otp;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select challenge from OtpChallenge challenge "
                    + "where challenge.user.id = :userId and challenge.purpose = :purpose")
    Optional<OtpChallenge> findForUpdate(
            @Param("userId") Long userId, @Param("purpose") OtpPurpose purpose);

    @Modifying
    @Query("delete from OtpChallenge challenge where challenge.expiresAt < :cutoff")
    void deleteExpired(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from OtpChallenge challenge where challenge.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
