package com.example.demo.controller;

import com.example.demo.service.TrOcrService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final TrOcrService ocrService;

    public OcrController(TrOcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/process")
    public ResponseEntity<String> processImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Uploaded file is empty");
        }

        try {
            String result = ocrService.getTextFromImage(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // If the Python service fails, this returns the error message to your curl window
            return ResponseEntity.internalServerError().body("OCR Processing Failed: " + e.getMessage());
        }
    }
}
