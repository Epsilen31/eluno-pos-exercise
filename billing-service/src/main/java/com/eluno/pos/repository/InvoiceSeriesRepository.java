package com.eluno.pos.repository;

import com.eluno.pos.entity.InvoiceSeries;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceSeriesRepository extends JpaRepository<InvoiceSeries, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select series from InvoiceSeries series where series.prefix = :prefix")
    Optional<InvoiceSeries> findByPrefixForUpdate(@Param("prefix") String prefix);
}
