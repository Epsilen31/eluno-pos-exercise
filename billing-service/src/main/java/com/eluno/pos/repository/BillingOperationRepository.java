package com.eluno.pos.repository;

import com.eluno.pos.entity.BillingOperation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingOperationRepository extends JpaRepository<BillingOperation, String> {
    List<BillingOperation> findAllByOrderByUpdatedAtAsc();

    @Query(
            "select count(operation) from BillingOperation operation "
                    + "where operation.invoiceNo like :prefix "
                    + "and operation.state not in ('COMPLETED', 'CANCELLED')")
    long countUnresolvedByInvoicePrefix(@Param("prefix") String prefix);
}
