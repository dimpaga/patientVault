package com.example.patientvault.patient.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VisitReasonRepository extends JpaRepository<VisitReasonEntity, String> {

    List<VisitReasonEntity> findByActiveTrueOrderBySortOrderAscCodeAsc();
}
