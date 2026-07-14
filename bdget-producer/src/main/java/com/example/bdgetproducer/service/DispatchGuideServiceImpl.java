package com.example.bdgetproducer.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
         * Valor temporal para evitar insertar un NULL
         * antes de conocer el ID de la guía.
         */
        guide.setFileName("PENDIENTE");

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

        /*
         * El consumidor es quien hará la subida automática.
         */
        guide.setUploadedToS3(false);

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
         * Primero se consulta directamente S3.
         * Esto evita depender de uploadedToS3, ya que la subida
         * automática la realiza el microservicio consumidor.
         */
        if (canCheckS3(guide)
                && awsService.objectExists(
                        bucketName,
                        guide.getS3Key())) {

            return awsService.downloadS3File(
                    bucketName,
                    guide.getS3Key()
            );
        }

        /*
         * Si todavía no está en S3, se descarga desde EFS.
         */
        if (!efsStorageService.fileExists(guide.getEfsPath())) {
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
         * Siempre actualiza la copia compartida en EFS.
         */
        efsStorageService.writeFile(
                guide.getEfsPath(),
                updatedContent
        );

        /*
         * Si el archivo realmente existe en S3,
         * también se sobrescribe allí.
         */
        if (canCheckS3(guide)
                && awsService.objectExists(
                        bucketName,
                        guide.getS3Key())) {

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
         * Elimina directamente desde S3 si el objeto existe,
         * aunque uploadedToS3 esté desactualizado.
         */
        if (canCheckS3(guide)
                && awsService.objectExists(
                        bucketName,
                        guide.getS3Key())) {

            awsService.deleteObject(
                    bucketName,
                    guide.getS3Key()
            );
        }

        /*
         * Elimina también el archivo compartido desde EFS.
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

        List<DispatchGuide> guides = new ArrayList<>();

        if (transportista != null
                && !transportista.isBlank()
                && fecha != null) {

            guides =
                    dispatchGuideRepository
                            .findByTransportistaAndFechaOrderByCreatedAtDesc(
                                    transportista.trim(),
                                    fecha
                            );

        } else if (transportista != null
                && !transportista.isBlank()) {

            guides =
                    dispatchGuideRepository
                            .findByTransportistaOrderByCreatedAtDesc(
                                    transportista.trim()
                            );

        } else if (fecha != null) {

            guides =
                    dispatchGuideRepository
                            .findByFechaOrderByCreatedAtDesc(fecha);

        } else {
            guides = dispatchGuideRepository.findAll();
        }

        if (bucketName != null
                && !bucketName.isBlank()
                && transportista != null
                && !transportista.isBlank()) {

            String dateFolder =
                    fecha != null
                            ? fecha.format(DATE_FOLDER_FORMAT)
                            : null;

            String prefix =
                    guideFileService.buildS3Prefix(
                            transportista.trim(),
                            dateFolder
                    );

            if (!prefix.isBlank()) {
                awsService.listS3Files(
                        bucketName,
                        prefix
                );
            }
        }

        return guides.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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
                && guide.getS3Key() != null
                && !guide.getS3Key().isBlank();
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
        response.setUploadedToS3(guide.isUploadedToS3());
        response.setCreatedAt(guide.getCreatedAt());
        response.setUpdatedAt(guide.getUpdatedAt());

        return response;
    }
}