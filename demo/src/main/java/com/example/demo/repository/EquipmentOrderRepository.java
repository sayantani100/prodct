package com.example.demo.repository;

import com.example.demo.model.EquipmentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EquipmentOrderRepository extends JpaRepository<EquipmentOrder, Long> {
    List<EquipmentOrder> findByStatus(EquipmentOrder.ReviewStatus status);
}