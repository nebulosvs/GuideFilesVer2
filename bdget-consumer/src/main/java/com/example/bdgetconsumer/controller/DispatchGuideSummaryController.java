package com.example.bdgetconsumer.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bdgetconsumer.dto.ConsumeMessageResponseDto;
import com.example.bdgetconsumer.service.RabbitMQConsumerService;

@RestController
@RequestMapping("/api/mensajes")
public class DispatchGuideSummaryController {

    private final RabbitMQConsumerService consumerService;

    public DispatchGuideSummaryController(
            RabbitMQConsumerService consumerService) {

        this.consumerService = consumerService;
    }

    @PostMapping
    public ResponseEntity<ConsumeMessageResponseDto> consumirMensaje() {

        ConsumeMessageResponseDto response =
                consumerService.consumeAndSave();

        return switch (response.getStatus()) {

            case "PROCESSED" ->
                    ResponseEntity.ok(response);

            case "SENT_TO_DLQ" ->
                    ResponseEntity.status(HttpStatus.ACCEPTED)
                            .body(response);

            case "QUEUE_EMPTY" ->
                    ResponseEntity.ok(response);

            default ->
                    ResponseEntity.status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    ).body(response);
        };
    }

    @GetMapping
    public ResponseEntity<String> verificarEndpoint() {
        return ResponseEntity.ok(
                "Endpoint consumidor disponible."
        );
    }
}