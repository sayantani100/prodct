package com.example.demo.service;

import com.example.demo.dto.MedicalOrderResponse;
import com.example.demo.model.MedicalOrder;
import com.example.demo.repository.MedicalOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TrOcrService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String engineSharedSecret;

    // Must stay comfortably above main.py's OLLAMA_REQUEST_TIMEOUT (300s)
    // plus OCR/TrOCR processing on top of that.
    private static final Duration ENGINE_CALL_TIMEOUT = Duration.ofSeconds(600);
    private static final String NIL_VALUE = "nil";

    @Autowired
    private MedicalOrderRepository orderRepository;

    public TrOcrService(WebClient.Builder webClientBuilder,
                        @Value("${ocr.engine.url:http://localhost:8000}") String engineUrl,
                        @Value("${OCR_ENGINE_SHARED_SECRET:}") String engineSharedSecret) {
        this.webClient = webClientBuilder.baseUrl(engineUrl).build();
        this.objectMapper = new ObjectMapper();
        this.engineSharedSecret = engineSharedSecret;
    }

    public String getTextFromImage(MultipartFile file) {
        String rawJsonResponse;

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });

            WebClient.RequestBodySpec request = webClient.post()
                    .uri("/predict")
                    .contentType(MediaType.MULTIPART_FORM_DATA);

            if (engineSharedSecret != null && !engineSharedSecret.isBlank()) {
                request = (WebClient.RequestBodySpec) request.header("X-Engine-Secret", engineSharedSecret);
            }

            rawJsonResponse = request
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(ENGINE_CALL_TIMEOUT)
                    .block();

        } catch (WebClientResponseException e) {
            System.out.println("[TROCR SERVICE] OCR engine returned error status " + e.getStatusCode() + ": " + e.getMessage());
            return errorPayload("OCR engine returned an error: " + e.getStatusCode());
        } catch (WebClientRequestException e) {
            System.out.println("[TROCR SERVICE] Could not reach OCR engine: " + e.getMessage());
            return errorPayload("Could not reach OCR engine: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("[TROCR SERVICE] OCR request failed: " + e.getMessage());
            return errorPayload("OCR request failed: " + e.getMessage());
        }

        try {
            if (rawJsonResponse != null) {
                MedicalOrderResponse orderData = objectMapper.readValue(rawJsonResponse, MedicalOrderResponse.class);

                if (orderData != null && orderData.getComprehension() != null && orderData.getComprehension().isOrder_required()) {
                    MedicalOrderResponse.ComprehensionData comp = orderData.getComprehension();

                    MedicalOrder newOrder = new MedicalOrder();
                    newOrder.setDocumentType(comp.getDocument_classification());
                    newOrder.setActionPlan(comp.getOrder_action_plan());
                    newOrder.setRawTranscript(comp.getClean_transcript());
                    newOrder.setRecognitionEngine(orderData.getRecognitionEngine());
                    newOrder.setStatus(MedicalOrder.ReviewStatus.PENDING_REVIEW);

                    MedicalOrderResponse.Mediclaim mediclaim = comp.getMediclaim();
                    if (mediclaim != null && mediclaim.getPatient() != null
                            && mediclaim.getPatient().getPermanentAddress() != null
                            && mediclaim.getPatient().getPermanentAddress().getPatientName() != null) {
                        newOrder.setPatientName(mediclaim.getPatient().getPermanentAddress().getPatientName());
                    } else {
                        newOrder.setPatientName(NIL_VALUE);
                    }

                    if (mediclaim != null && mediclaim.getInsurance() != null) {
                        List<String> insuranceNames = mediclaim.getInsurance().stream()
                                .map(MedicalOrderResponse.Insurance::getInsuranceName)
                                .filter(name -> name != null && !name.isBlank() && !NIL_VALUE.equalsIgnoreCase(name))
                                .collect(Collectors.toList());
                        newOrder.setDetectedItems(insuranceNames.isEmpty() ? "[]" : insuranceNames.toString());
                    } else {
                        newOrder.setDetectedItems("[]");
                    }

                    orderRepository.save(newOrder);
                }
            }
        } catch (Exception dbException) {
            System.out.println("[DATABASE SERVICE LOGGER]: Skipping save entry - " + dbException.getMessage());
        }

        return rawJsonResponse;
    }

    private String errorPayload(String message) {
        String safeMessage = message == null ? "Unknown error" : message.replace("\"", "'");
        return "{\"error\":\"" + safeMessage + "\"}";
    }
}