package com.example.bdgetconsumer.dto;

import com.example.bdgetconsumer.model.DispatchGuideSummary;

public class ConsumeMessageResponseDto {

    private boolean success;
    private String status;
    private String message;
    private DispatchGuideSummary summary;

    public ConsumeMessageResponseDto() {
    }

    public ConsumeMessageResponseDto(
            boolean success,
            String status,
            String message,
            DispatchGuideSummary summary) {

        this.success = success;
        this.status = status;
        this.message = message;
        this.summary = summary;
    }

    public static ConsumeMessageResponseDto processed(
            DispatchGuideSummary summary) {

        return new ConsumeMessageResponseDto(
                true,
                "PROCESSED",
                "Guía procesada correctamente, subida a S3 y guardada en Oracle.",
                summary
        );
    }

    public static ConsumeMessageResponseDto sentToDlq(String cause) {

        return new ConsumeMessageResponseDto(
                false,
                "SENT_TO_DLQ",
                "El procesamiento falló y el mensaje fue enviado a la DLQ. Causa: "
                        + cause,
                null
        );
    }

    public static ConsumeMessageResponseDto queueEmpty() {

        return new ConsumeMessageResponseDto(
                false,
                "QUEUE_EMPTY",
                "No hay mensajes disponibles en la cola.",
                null
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DispatchGuideSummary getSummary() {
        return summary;
    }

    public void setSummary(DispatchGuideSummary summary) {
        this.summary = summary;
    }
}