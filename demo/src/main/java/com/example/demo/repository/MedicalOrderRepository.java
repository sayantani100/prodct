package com.example.demo.repository;

import com.example.demo.model.MedicalOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicalOrderRepository extends JpaRepository<MedicalOrder, Long> {

    // Spring Data derives this query automatically from the method name --
    // no implementation needed. Used by MedicalOrderController to list
    // orders awaiting human review.
    List<MedicalOrder> findByStatus(MedicalOrder.ReviewStatus status);
}