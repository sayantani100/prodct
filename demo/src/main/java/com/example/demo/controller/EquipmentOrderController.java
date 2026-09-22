package com.example.demo.controller;

import com.example.demo.model.EquipmentOrder;
import com.example.demo.repository.EquipmentOrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/equipment-orders")
public class EquipmentOrderController {

    @Autowired
    private EquipmentOrderRepository equipmentOrderRepository;

    public static class CreateOrderRequest {
        public String equipmentType; // "CPAP", "BIPAP", or "DME"
        public String type;
        public String featureFeature;
        public String adjustFeature;
        public String range;
        public String company;
        public String cost;
        public String pressureSetting;
    }

    public static class PressureChangeRequest {
        public Long relatedOrderId;
        public String type;
        public String featureFeature;
        public String adjustFeature;
        public String range;
        public String company;
        public String cost;
        public String pressureSetting;
    }

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest request, Authentication authentication) {
        EquipmentOrder.EquipmentType equipmentType;
        try {
            equipmentType = EquipmentOrder.EquipmentType.valueOf(request.equipmentType.toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid equipmentType. Must be CPAP, BIPAP, or DME.");
        }

        EquipmentOrder order = new EquipmentOrder();
        order.setEquipmentType(equipmentType);
        order.setType(request.type);
        order.setFeatureFeature(request.featureFeature);
        order.setAdjustFeature(request.adjustFeature);
        order.setRange(request.range);
        order.setCompany(request.company);
        order.setCost(request.cost);
        order.setPressureSetting(request.pressureSetting);
        order.setStatus(EquipmentOrder.ReviewStatus.PENDING_REVIEW);
        order.setCreatedBy(authentication != null ? authentication.getName() : "unknown");

        equipmentOrderRepository.save(order);
        return ResponseEntity.ok(order);
    }

    // Updates the pressure setting (and any other supplied fields) on an
    // EXISTING CPAP/BiPAP order, per the mockup's "(order already there)"
    // note -- this does not create a new order, it modifies one in place
    // and puts it back into PENDING_REVIEW so the change gets reviewed too.
    @PostMapping("/pressure-change")
    public ResponseEntity<?> changePressure(@RequestBody PressureChangeRequest request, Authentication authentication) {
        if (request.relatedOrderId == null) {
            return ResponseEntity.badRequest().body("relatedOrderId is required to apply a pressure change.");
        }

        Optional<EquipmentOrder> existingOpt = equipmentOrderRepository.findById(request.relatedOrderId);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EquipmentOrder existing = existingOpt.get();
        if (existing.getEquipmentType() != EquipmentOrder.EquipmentType.CPAP
                && existing.getEquipmentType() != EquipmentOrder.EquipmentType.BIPAP) {
            return ResponseEntity.badRequest().body("Pressure changes only apply to CPAP or BiPAP orders.");
        }

        if (request.type != null) existing.setType(request.type);
        if (request.featureFeature != null) existing.setFeatureFeature(request.featureFeature);
        if (request.adjustFeature != null) existing.setAdjustFeature(request.adjustFeature);
        if (request.range != null) existing.setRange(request.range);
        if (request.company != null) existing.setCompany(request.company);
        if (request.cost != null) existing.setCost(request.cost);
        if (request.pressureSetting != null) existing.setPressureSetting(request.pressureSetting);

        // Changing a live pressure setting needs re-review, same as a new order.
        existing.setStatus(EquipmentOrder.ReviewStatus.PENDING_REVIEW);
        existing.setReviewedBy(null);
        existing.setReviewedAt(null);

        equipmentOrderRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    public static class StatusUpdateRequest {
        public String supervision;
        public String followUp;
        public String reviewNotes;
        public String status; // "PENDING_REVIEW", "CONFIRMED", or "REJECTED"
    }

    @GetMapping
    public List<EquipmentOrder> getAllOrders() {
        return equipmentOrderRepository.findAll();
    }

    @GetMapping("/pending")
    public List<EquipmentOrder> getPendingOrders() {
        return equipmentOrderRepository.findByStatus(EquipmentOrder.ReviewStatus.PENDING_REVIEW);
    }

    // Updates the status-tracking fields shown on the "Status of the product"
    // page: supervision, checked-by (reviewedBy, set automatically to the
    // logged-in user), follow-up notes, review notes, and the order status
    // itself. Separate from /create and /pressure-change since this is
    // purely administrative tracking, not a change to the equipment order's
    // clinical details.
    @PostMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request, Authentication authentication) {
        Optional<EquipmentOrder> existingOpt = equipmentOrderRepository.findById(id);
        if (existingOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EquipmentOrder existing = existingOpt.get();
        if (request.supervision != null) existing.setSupervision(request.supervision);
        if (request.followUp != null) existing.setFollowUp(request.followUp);
        if (request.reviewNotes != null) existing.setReviewNotes(request.reviewNotes);

        if (request.status != null) {
            try {
                existing.setStatus(EquipmentOrder.ReviewStatus.valueOf(request.status.toUpperCase()));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Invalid status. Must be PENDING_REVIEW, CONFIRMED, or REJECTED.");
            }
        }

        existing.setReviewedBy(authentication != null ? authentication.getName() : "unknown");
        existing.setReviewedAt(java.time.LocalDateTime.now());

        equipmentOrderRepository.save(existing);
        return ResponseEntity.ok(existing);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EquipmentOrder> getOrder(@PathVariable Long id) {
        return equipmentOrderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}