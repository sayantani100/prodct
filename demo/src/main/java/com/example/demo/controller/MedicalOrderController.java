package com.example.demo.controller;

import com.example.demo.model.MedicalOrder;
import com.example.demo.repository.MedicalOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class MedicalOrderController {

    @Autowired
    private MedicalOrderRepository orderRepository;

    // Everything a reviewer needs to see before deciding: the derived
    // summary fields AND the raw transcript + which recognizer produced it,
    // so low-trust paddleocr_fallback results can be weighted accordingly.
    @GetMapping("/pending")
    public List<MedicalOrder> getPendingOrders() {
        return orderRepository.findByStatus(MedicalOrder.ReviewStatus.PENDING_REVIEW);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicalOrder> getOrder(@PathVariable Long id) {
        Optional<MedicalOrder> order = orderRepository.findById(id);
        return order.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<?> confirmOrder(@PathVariable Long id, @RequestParam(required = false) String reviewedBy) {
        Optional<MedicalOrder> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MedicalOrder order = orderOpt.get();
        order.setStatus(MedicalOrder.ReviewStatus.CONFIRMED);
        order.setReviewedBy(reviewedBy != null ? reviewedBy : "unspecified");
        order.setReviewedAt(LocalDateTime.now());
        orderRepository.save(order);

        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<?> rejectOrder(@PathVariable Long id, @RequestParam(required = false) String reviewedBy) {
        Optional<MedicalOrder> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MedicalOrder order = orderOpt.get();
        order.setStatus(MedicalOrder.ReviewStatus.REJECTED);
        order.setReviewedBy(reviewedBy != null ? reviewedBy : "unspecified");
        order.setReviewedAt(LocalDateTime.now());
        orderRepository.save(order);

        return ResponseEntity.ok(order);
    }
}
