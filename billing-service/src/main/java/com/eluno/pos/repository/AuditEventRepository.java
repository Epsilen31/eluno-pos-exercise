package com.eluno.pos.repository;

import com.eluno.pos.entity.AuditEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByReferenceOrderByIdAsc(String reference);
}
