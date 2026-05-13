package com.ihh.wpBot.repository;

import com.ihh.wpBot.model.DeliveryRecord;
import com.ihh.wpBot.model.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeliveryRecordRepository extends JpaRepository<DeliveryRecord, Long> {

    Optional<DeliveryRecord> findByWaMessageId(String waMessageId);

    boolean existsByWaMessageId(String waMessageId);

    List<DeliveryRecord> findByPhoneNumberOrderBySentAtDesc(String phoneNumber);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<DeliveryRecord> findFirstByPhoneNumberOrderBySentAtDesc(String phoneNumber);

    Page<DeliveryRecord> findByStatusOrderBySentAtDesc(DeliveryStatus status, Pageable pageable);

    Page<DeliveryRecord> findAllByOrderByContactNameAsc(Pageable pageable);

    long countByStatus(DeliveryStatus status);

    @Query("SELECT d FROM DeliveryRecord d WHERE d.phoneNumber IN :phones AND d.sentAt = " +
            "(SELECT MAX(d2.sentAt) FROM DeliveryRecord d2 WHERE d2.phoneNumber = d.phoneNumber)")
    List<DeliveryRecord> findLatestByPhoneNumbers(List<String> phones);
}
