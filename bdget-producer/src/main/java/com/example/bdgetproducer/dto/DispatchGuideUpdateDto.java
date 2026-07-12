package com.example.bdgetproducer.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class DispatchGuideUpdateDto {

    private String origen;
    private String destino;
    private String descripcion;

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }
}
