package com.example.bdgetproducer.service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.example.bdgetproducer.model.DispatchGuide;

@Service
public class DispatchGuideFileService {

    private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    public String buildDateFolder(DispatchGuide guide) {
        return guide.getFecha().format(DATE_FOLDER_FORMAT);
    }

    public String buildFileName(Long guideId) {
        return "guia" + guideId + ".txt";
    }

    public String buildEfsRelativePath(DispatchGuide guide) {
        return buildDateFolder(guide) + "/" + guide.getTransportista() + "/" + guide.getFileName();
    }

    public String buildS3Key(DispatchGuide guide) {
        return buildDateFolder(guide) + "/" + guide.getTransportista() + "/" + guide.getFileName();
    }

    public String buildS3Prefix(String transportista, String dateFolder) {
        if (transportista != null && !transportista.isBlank() && dateFolder != null && !dateFolder.isBlank()) {
            return dateFolder + "/" + transportista + "/";
        }
        if (dateFolder != null && !dateFolder.isBlank()) {
            return dateFolder + "/";
        }
        if (transportista != null && !transportista.isBlank()) {
            return transportista + "/";
        }
        return "";
    }

    public byte[] generateGuideContent(DispatchGuide guide) {
        StringBuilder content = new StringBuilder();
        content.append("GUIA DE DESPACHO\n");
        content.append("================\n\n");
        content.append("Numero de guia: ").append(guide.getId()).append("\n");
        content.append("Pedido: ").append(guide.getPedidoId()).append("\n");
        content.append("Transportista: ").append(guide.getTransportista()).append("\n");
        content.append("Fecha: ").append(guide.getFecha()).append("\n");
        content.append("Origen: ").append(guide.getOrigen()).append("\n");
        content.append("Destino: ").append(guide.getDestino()).append("\n");
        if (guide.getDescripcion() != null && !guide.getDescripcion().isBlank()) {
            content.append("Descripcion: ").append(guide.getDescripcion()).append("\n");
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }
}
