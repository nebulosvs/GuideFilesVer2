package com.example.bdgetproducer.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bdgetproducer.dto.DispatchGuideMessageDto;
import com.example.bdgetproducer.dto.DispatchGuideRequestDto;
import com.example.bdgetproducer.dto.DispatchGuideResponseDto;
import com.example.bdgetproducer.dto.DispatchGuideUpdateDto;
import com.example.bdgetproducer.exception.AccessDeniedException;
import com.example.bdgetproducer.exception.ResourceNotFoundException;
import com.example.bdgetproducer.model.DispatchGuide;
import com.example.bdgetproducer.repository.DispatchGuideRepository;

@Service
public class DispatchGuideServiceImpl implements DispatchGuideService {

    private static final DateTimeFormatter DATE_FOLDER_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMM");

    @Autowired
    private DispatchGuideRepository dispatchGuideRepository;

    @Autowired
    private DispatchGuideFileService guideFileService;

    @Autowired
    private EfsStorageService efsStorageService;

    @Autowired
    private AwsService awsService;

    @Autowired
    private RabbitMQProducer rabbitMQProducer;

    @Value("${cloud.aws.s3.bucket-name:}")
    private String bucketName;

    @Override
    @Transactional
    public DispatchGuideResponseDto createGuide(
            DispatchGuideRequestDto request) {

        DispatchGuide guide = new DispatchGuide();

        guide.setTransportista(request.getTransportista().trim());
        guide.setFecha(request.getFecha());
        guide.setPedidoId(request.getPedidoId().trim());
        guide.setOrigen(request.getOrigen().trim());
        guide.setDestino(request.getDestino().trim());
        guide.setDescripcion(request.getDescripcion());

        /*
         * Se asigna un nombre temporal porque todavía
         * no conocemos el ID generado por Oracle.
         */
        guide.setFileName("PENDIENTE");
        guide.setUploadedToS3(false);

        guide = dispatchGuideRepository.save(guide);

        guide.setFileName(
                guideFileService.buildFileName(guide.getId())
        );

        String efsRelativePath =
                guideFileService.buildEfsRelativePath(guide);

        byte[] guideContent =
                guideFileService.generateGuideContent(guide);

        efsStorageService.writeFile(
                efsRelativePath,
                guideContent
        );

        guide.setEfsPath(efsRelativePath);
        guide.setS3Key(
                guideFileService.buildS3Key(guide)
        );

        guide = dispatchGuideRepository.save(guide);

        DispatchGuideMessageDto message =
                buildRabbitMessage(guide);

        rabbitMQProducer.sendMessage(message);

        return toResponse(guide);
    }

    @Override
    @Transactional
    public DispatchGuideResponseDto uploadGuideToS3(Long id) {

        validateBucketConfigured();

        DispatchGuide guide = findGuideOrThrow(id);

        if (!efsStorageService.fileExists(guide.getEfsPath())) {
            throw new ResourceNotFoundException(
                    "La guía no existe en EFS: "
                            + guide.getEfsPath()
            );
        }

        byte[] content =
                efsStorageService.readFile(guide.getEfsPath());

        awsService.uploadFile(
                bucketName,
                guide.getS3Key(),
                content,
                resolveContentType(guide.getFileName())
        );

        guide.setUploadedToS3(true);
        guide = dispatchGuideRepository.save(guide);

        return toResponse(guide);
    }

    @Override
    public byte[] downloadGuide(
            Long id,
            String transportista) {

        DispatchGuide guide = findGuideOrThrow(id);

        validateTransportistaPermission(
                guide,
                transportista
        );

        /*
         * Se comprueba directamente S3 porque el consumidor
         * es quien realiza la subida automática y el campo
         * uploadedToS3 podría estar desactualizado.
         */
        if (existsInS3(guide)) {
            return awsService.downloadS3File(
                    bucketName,
                    guide.getS3Key()
            );
        }

        /*
         * Si todavía no está en S3, se intenta descargar
         * desde el almacenamiento compartido EFS.
         */
        if (guide.getEfsPath() == null
                || guide.getEfsPath().isBlank()
                || !efsStorageService.fileExists(guide.getEfsPath())) {

            throw new ResourceNotFoundException(
                    "La guía no fue encontrada en S3 ni en EFS."
            );
        }

        return efsStorageService.readFile(
                guide.getEfsPath()
        );
    }

    @Override
    @Transactional
    public DispatchGuideResponseDto updateGuide(
            Long id,
            DispatchGuideUpdateDto request,
            String transportista) {

        DispatchGuide guide = findGuideOrThrow(id);

        validateTransportistaPermission(
                guide,
                transportista
        );

        if (request.getOrigen() != null
                && !request.getOrigen().isBlank()) {

            guide.setOrigen(
                    request.getOrigen().trim()
            );
        }

        if (request.getDestino() != null
                && !request.getDestino().isBlank()) {

            guide.setDestino(
                    request.getDestino().trim()
            );
        }

        if (request.getDescripcion() != null) {
            guide.setDescripcion(
                    request.getDescripcion()
            );
        }

        byte[] updatedContent =
                guideFileService.generateGuideContent(guide);

        /*
         * Actualiza siempre la copia compartida en EFS.
         */
        efsStorageService.writeFile(
                guide.getEfsPath(),
                updatedContent
        );

        /*
         * Si el objeto existe realmente en S3,
         * también se reemplaza en el bucket.
         */
        if (existsInS3(guide)) {

            awsService.uploadFile(
                    bucketName,
                    guide.getS3Key(),
                    updatedContent,
                    resolveContentType(guide.getFileName())
            );

            guide.setUploadedToS3(true);
        }

        guide = dispatchGuideRepository.save(guide);

        return toResponse(guide);
    }

    @Override
    @Transactional
    public void deleteGuide(
            Long id,
            String transportista) {

        DispatchGuide guide = findGuideOrThrow(id);

        validateTransportistaPermission(
                guide,
                transportista
        );

        /*
         * Elimina el objeto actual de S3 sin depender
         * del valor almacenado en uploadedToS3.
         */
        if (existsInS3(guide)) {
            awsService.deleteObject(
                    bucketName,
                    guide.getS3Key()
            );
        }

        /*
         * Elimina también el archivo de EFS.
         */
        if (guide.getEfsPath() != null
                && !guide.getEfsPath().isBlank()) {

            efsStorageService.deleteFile(
                    guide.getEfsPath()
            );
        }

        dispatchGuideRepository.delete(guide);
    }

    @Override
    public List<DispatchGuideResponseDto> getGuideHistory(
            String transportista,
            LocalDate fecha) {

        validateBucketConfigured();

        String normalizedTransportista =
                normalizeTransportista(transportista);

        /*
         * Oracle entrega los metadatos de las guías.
         */
        List<DispatchGuide> databaseGuides =
                findGuidesByFilters(
                        normalizedTransportista,
                        fecha
                );

        /*
         * Se obtiene la lista actual de objetos del bucket.
         *
         * Cuando hay fecha, la estructura de carpetas permite
         * limitar la consulta al prefijo yyyyMM/.
         *
         * Cuando no hay fecha, se consulta desde la raíz porque
         * las claves tienen esta estructura:
         *
         * yyyyMM/transportista/guia{id}.txt
         */
        String s3Prefix = buildHistoryS3Prefix(fecha);

        List<String> currentS3Keys =
                awsService.listS3Files(
                        bucketName,
                        s3Prefix
                );

        Set<String> currentS3KeySet =
                currentS3Keys == null
                        ? new HashSet<>()
                        : new HashSet<>(currentS3Keys);

        /*
         * Solo se devuelven las guías que siguen existiendo
         * actualmente en el bucket S3.
         */
        return databaseGuides.stream()
                .filter(guide ->
                        guide.getS3Key() != null
                                && !guide.getS3Key().isBlank()
                                && currentS3KeySet.contains(
                                        guide.getS3Key()
                                )
                )
                .map(guide -> toResponse(guide, true))
                .collect(Collectors.toList());
    }

    private List<DispatchGuide> findGuidesByFilters(
            String transportista,
            LocalDate fecha) {

        if (transportista != null && fecha != null) {

            return dispatchGuideRepository
                    .findByTransportistaAndFechaOrderByCreatedAtDesc(
                            transportista,
                            fecha
                    );
        }

        if (transportista != null) {

            return dispatchGuideRepository
                    .findByTransportistaOrderByCreatedAtDesc(
                            transportista
                    );
        }

        if (fecha != null) {

            return dispatchGuideRepository
                    .findByFechaOrderByCreatedAtDesc(fecha);
        }

        return new ArrayList<>(
                dispatchGuideRepository.findAll()
        );
    }

    private String buildHistoryS3Prefix(LocalDate fecha) {

        if (fecha == null) {
            return "";
        }

        return fecha.format(DATE_FOLDER_FORMAT) + "/";
    }

    private String normalizeTransportista(
            String transportista) {

        if (transportista == null
                || transportista.isBlank()) {

            return null;
        }

        return transportista.trim();
    }

    private DispatchGuideMessageDto buildRabbitMessage(
            DispatchGuide guide) {

        DispatchGuideMessageDto message =
                new DispatchGuideMessageDto();

        message.setGuideId(guide.getId());
        message.setTransportista(guide.getTransportista());
        message.setFecha(guide.getFecha());
        message.setPedidoId(guide.getPedidoId());
        message.setOrigen(guide.getOrigen());
        message.setDestino(guide.getDestino());
        message.setDescripcion(guide.getDescripcion());
        message.setFileName(guide.getFileName());
        message.setEfsPath(guide.getEfsPath());
        message.setS3Key(guide.getS3Key());

        return message;
    }

    private DispatchGuide findGuideOrThrow(Long id) {

        return dispatchGuideRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Guía de despacho no encontrada con id: "
                                        + id
                        )
                );
    }

    private void validateTransportistaPermission(
            DispatchGuide guide,
            String transportista) {

        if (transportista == null
                || transportista.isBlank()) {

            throw new AccessDeniedException(
                    "Debe indicar el transportista "
                            + "para validar permisos de acceso"
            );
        }

        if (!guide.getTransportista()
                .equalsIgnoreCase(transportista.trim())) {

            throw new AccessDeniedException(
                    "No tiene permisos para acceder "
                            + "a la guía del transportista indicado"
            );
        }
    }

    private void validateBucketConfigured() {

        if (bucketName == null
                || bucketName.isBlank()) {

            throw new IllegalStateException(
                    "El bucket S3 no está configurado. "
                            + "Defina AWS_S3_BUCKET_NAME."
            );
        }
    }

    private boolean canCheckS3(DispatchGuide guide) {

        return bucketName != null
                && !bucketName.isBlank()
                && guide != null
                && guide.getS3Key() != null
                && !guide.getS3Key().isBlank();
    }

    private boolean existsInS3(DispatchGuide guide) {

        return canCheckS3(guide)
                && awsService.objectExists(
                        bucketName,
                        guide.getS3Key()
                );
    }

    private String resolveContentType(String fileName) {

        if (fileName != null
                && fileName.toLowerCase().endsWith(".txt")) {

            return "text/plain";
        }

        return "application/octet-stream";
    }

    private DispatchGuideResponseDto toResponse(
            DispatchGuide guide) {

        return toResponse(
                guide,
                guide.isUploadedToS3()
        );
    }

    private DispatchGuideResponseDto toResponse(
            DispatchGuide guide,
            boolean uploadedToS3) {

        DispatchGuideResponseDto response =
                new DispatchGuideResponseDto();

        response.setId(guide.getId());
        response.setTransportista(guide.getTransportista());
        response.setFecha(guide.getFecha());
        response.setPedidoId(guide.getPedidoId());
        response.setOrigen(guide.getOrigen());
        response.setDestino(guide.getDestino());
        response.setDescripcion(guide.getDescripcion());
        response.setFileName(guide.getFileName());
        response.setS3Key(guide.getS3Key());
        response.setEfsPath(guide.getEfsPath());
        response.setUploadedToS3(uploadedToS3);
        response.setCreatedAt(guide.getCreatedAt());
        response.setUpdatedAt(guide.getUpdatedAt());

        return response;
    }
}