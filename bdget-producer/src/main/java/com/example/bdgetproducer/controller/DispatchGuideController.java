package com.example.bdgetproducer.controller;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bdgetproducer.dto.DispatchGuideRequestDto;
import com.example.bdgetproducer.dto.DispatchGuideResponseDto;
import com.example.bdgetproducer.dto.DispatchGuideUpdateDto;
import com.example.bdgetproducer.service.DispatchGuideService;

@RestController
@RequestMapping("/api/guias")
@CrossOrigin(origins = "*")
public class DispatchGuideController {

    @Autowired
    private DispatchGuideService dispatchGuideService;

    @PostMapping
    public ResponseEntity<DispatchGuideResponseDto> createGuide(
            @Validated @RequestBody DispatchGuideRequestDto request) {
        DispatchGuideResponseDto response = dispatchGuideService.createGuide(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/s3")
    public ResponseEntity<Map<String, String>> uploadGuideToS3(@PathVariable Long id) {
        DispatchGuideResponseDto guide = dispatchGuideService.uploadGuideToS3(id);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Guia subida correctamente a S3");
        response.put("s3Key", guide.getS3Key());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadGuide(
            @PathVariable Long id,
            @RequestParam String transportista) {
        byte[] content = dispatchGuideService.downloadGuide(id, transportista);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"guia" + id + ".pdf\"")
                .body(content);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DispatchGuideResponseDto> updateGuide(
            @PathVariable Long id,
            @RequestParam String transportista,
            @RequestBody DispatchGuideUpdateDto request) {
        return ResponseEntity.ok(dispatchGuideService.updateGuide(id, request, transportista));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteGuide(
            @PathVariable Long id,
            @RequestParam String transportista) {
        dispatchGuideService.deleteGuide(id, transportista);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Guia eliminada correctamente");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/historial")
    public ResponseEntity<List<DispatchGuideResponseDto>> getGuideHistory(
            @RequestParam(required = false) String transportista,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(dispatchGuideService.getGuideHistory(transportista, fecha));
    }
}
