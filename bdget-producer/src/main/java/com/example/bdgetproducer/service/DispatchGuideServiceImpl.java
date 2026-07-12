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

import com.example.bdgetproducer.dto.DispatchGuideRequestDto;
import com.example.bdgetproducer.dto.DispatchGuideResponseDto;
import com.example.bdgetproducer.dto.DispatchGuideUpdateDto;
import com.example.bdgetproducer.dto.DispatchGuideMessageDto;
import com.example.bdgetproducer.exception.AccessDeniedException;
import com.example.bdgetproducer.exception.ResourceNotFoundException;
import com.example.bdgetproducer.model.DispatchGuide;
import com.example.bdgetproducer.repository.DispatchGuideRepository;

@Service
public class DispatchGuideServiceImpl implements DispatchGuideService {

    private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

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
    public DispatchGuideResponseDto createGuide(DispatchGuideRequestDto request) {
        DispatchGuide guide = new DispatchGuide();
        guide.setTransportista(request.getTransportista().trim());
        guide.setFecha(request.getFecha());
        guide.setPedidoId(request.getPedidoId().trim());
        guide.setOrigen(request.getOrigen().trim());
        guide.setDestino(request.getDestino().trim());
        guide.setDescripcion(request.getDescripcion());

        guide.setFileName("PENDIENTE");

        guide = dispatchGuideRepository.save(guide);
        guide.setFileName(guideFileService.buildFileName(guide.getId()));

        String efsRelativePath = guideFileService.buildEfsRelativePath(guide);
        efsStorageService.writeFile(efsRelativePath, guideFileService.generateGuideContent(guide));

        guide.setEfsPath(efsRelativePath);
        guide.setS3Key(guideFileService.buildS3Key(guide));
        guide = dispatchGuideRepository.save(guide);

        DispatchGuideMessageDto message = new DispatchGuideMessageDto();

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

        rabbitMQProducer.sendMessage(message);
        
        return toResponse(guide);
    }

    @Override
    @Transactional
    public DispatchGuideResponseDto uploadGuideToS3(Long id) {
        validateBucketConfigured();
        DispatchGuide guide = findGuideOrThrow(id);

        if (!efsStorageService.fileExists(guide.getEfsPath())) {
            throw new ResourceNotFoundException("La guia no existe en EFS: " + guide.getEfsPath());
        }

        byte[] content = efsStorageService.readFile(guide.getEfsPath());
        awsService.uploadFile(bucketName, guide.getS3Key(), content, "text/plain");

        guide.setUploadedToS3(true);
        guide = dispatchGuideRepository.save(guide);

        return toResponse(guide);
    }

    @Override
    public byte[] downloadGuide(Long id, String transportista) {
        DispatchGuide guide = findGuideOrThrow(id);
        validateTransportistaPermission(guide, transportista);

        if (guide.isUploadedToS3()) {
            validateBucketConfigured();
            if (!awsService.objectExists(bucketName, guide.getS3Key())) {
                throw new ResourceNotFoundException("Guia no encontrada en S3: " + guide.getS3Key());
            }
            return awsService.downloadS3File(bucketName, guide.getS3Key());
        }

        if (!efsStorageService.fileExists(guide.getEfsPath())) {
            throw new ResourceNotFoundException("Guia no encontrada en EFS: " + guide.getEfsPath());
        }

        return efsStorageService.readFile(guide.getEfsPath());
    }

    @Override
    @Transactional
    public DispatchGuideResponseDto updateGuide(Long id, DispatchGuideUpdateDto request, String transportista) {
        DispatchGuide guide = findGuideOrThrow(id);
        validateTransportistaPermission(guide, transportista);

        if (request.getOrigen() != null && !request.getOrigen().isBlank()) {
            guide.setOrigen(request.getOrigen().trim());
        }
        if (request.getDestino() != null && !request.getDestino().isBlank()) {
            guide.setDestino(request.getDestino().trim());
        }
        if (request.getDescripcion() != null) {
            guide.setDescripcion(request.getDescripcion());
        }

        byte[] updatedContent = guideFileService.generateGuideContent(guide);
        efsStorageService.writeFile(guide.getEfsPath(), updatedContent);

        if (guide.isUploadedToS3()) {
            validateBucketConfigured();
            awsService.uploadFile(bucketName, guide.getS3Key(), updatedContent, "text/plain");
        }

        guide = dispatchGuideRepository.save(guide);
        return toResponse(guide);
    }

    @Override
    @Transactional
    public void deleteGuide(Long id, String transportista) {
        DispatchGuide guide = findGuideOrThrow(id);
        validateTransportistaPermission(guide, transportista);

        if (guide.isUploadedToS3()) {
            validateBucketConfigured();
            if (awsService.objectExists(bucketName, guide.getS3Key())) {
                awsService.deleteObject(bucketName, guide.getS3Key());
            }
        }

        if (guide.getEfsPath() != null) {
            efsStorageService.deleteFile(guide.getEfsPath());
        }

        dispatchGuideRepository.delete(guide);
    }

    @Override
    public List<DispatchGuideResponseDto> getGuideHistory(String transportista, LocalDate fecha) {
        List<DispatchGuide> guides = new ArrayList<>();

        if (transportista != null && !transportista.isBlank() && fecha != null) {
            guides = dispatchGuideRepository.findByTransportistaAndFechaOrderByCreatedAtDesc(
                    transportista.trim(), fecha);
        } else if (transportista != null && !transportista.isBlank()) {
            guides = dispatchGuideRepository.findByTransportistaOrderByCreatedAtDesc(transportista.trim());
        } else if (fecha != null) {
            guides = dispatchGuideRepository.findByFechaOrderByCreatedAtDesc(fecha);
        } else {
            guides = dispatchGuideRepository.findAll();
        }

        if (bucketName != null && !bucketName.isBlank() && transportista != null && !transportista.isBlank()) {
            String dateFolder = fecha != null ? fecha.format(DATE_FOLDER_FORMAT) : null;
            String prefix = guideFileService.buildS3Prefix(transportista.trim(), dateFolder);
            if (!prefix.isBlank()) {
                awsService.listS3Files(bucketName, prefix);
            }
        }

        return guides.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private DispatchGuide findGuideOrThrow(Long id) {
        return dispatchGuideRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Guia de despacho no encontrada con id: " + id));
    }

    private void validateTransportistaPermission(DispatchGuide guide, String transportista) {
        if (transportista == null || transportista.isBlank()) {
            throw new AccessDeniedException("Debe indicar el transportista para validar permisos de acceso");
        }
        if (!guide.getTransportista().equalsIgnoreCase(transportista.trim())) {
            throw new AccessDeniedException("No tiene permisos para acceder a la guia del transportista indicado");
        }
    }

    private void validateBucketConfigured() {
        if (bucketName == null || bucketName.isBlank()) {
            throw new RuntimeException("El bucket S3 no esta configurado. Defina AWS_S3_BUCKET_NAME.");
        }
    }

    private DispatchGuideResponseDto toResponse(DispatchGuide guide) {
        DispatchGuideResponseDto response = new DispatchGuideResponseDto();
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
