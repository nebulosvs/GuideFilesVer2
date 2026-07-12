package com.example.bdgetconsumer.service;

import java.nio.charset.StandardCharsets;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.bdgetconsumer.config.RabbitMQConfig;
import com.example.bdgetconsumer.dto.ConsumeMessageResponseDto;
import com.example.bdgetconsumer.dto.DispatchGuideMessageDto;
import com.example.bdgetconsumer.model.DispatchGuideSummary;
import com.example.bdgetconsumer.repository.DispatchGuideSummaryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.GetResponse;

@Service
public class RabbitMQConsumerService {

    private final RabbitTemplate rabbitTemplate;
    private final DispatchGuideSummaryRepository summaryRepository;
    private final EfsStorageService efsStorageService;
    private final AwsService awsService;
    private final ObjectMapper objectMapper;

    @Value("${cloud.aws.s3.bucket-name:}")
    private String bucketName;

    public RabbitMQConsumerService(
            RabbitTemplate rabbitTemplate,
            DispatchGuideSummaryRepository summaryRepository,
            EfsStorageService efsStorageService,
            AwsService awsService,
            ObjectMapper objectMapper) {

        this.rabbitTemplate = rabbitTemplate;
        this.summaryRepository = summaryRepository;
        this.efsStorageService = efsStorageService;
        this.awsService = awsService;
        this.objectMapper = objectMapper;
    }

    public ConsumeMessageResponseDto consumeAndSave() {

        return rabbitTemplate.execute(channel -> {

            /*
             * autoAck = false.
             * El mensaje no se elimina hasta enviar ACK o NACK.
             */
            GetResponse response = channel.basicGet(
                    RabbitMQConfig.QUEUE,
                    false
            );

            if (response == null) {
                return null;
            }

            long deliveryTag = response.getEnvelope().getDeliveryTag();

            try {
                String json = new String(
                        response.getBody(),
                        StandardCharsets.UTF_8
                );

                DispatchGuideMessageDto request =
                        objectMapper.readValue(
                                json,
                                DispatchGuideMessageDto.class
                        );

                validateMessage(request);
                validateBucketConfigured();

                /*
                 * Error controlado para demostrar la DLQ.
                 * La descripción debe ser exactamente "error".
                 */
                if (request.getDescripcion() != null
                        && request.getDescripcion()
                                .trim()
                                .equalsIgnoreCase("error")) {

                    throw new RuntimeException(
                            "Error forzado para probar la DLQ"
                    );
                }

                if (!efsStorageService.fileExists(request.getEfsPath())) {
                    throw new RuntimeException(
                            "Archivo no encontrado en EFS: "
                                    + request.getEfsPath()
                    );
                }

                byte[] content =
                        efsStorageService.readFile(request.getEfsPath());

                awsService.uploadFile(
                        bucketName,
                        request.getS3Key(),
                        content,
                        resolveContentType(request.getFileName())
                );

                DispatchGuideSummary summary =
                        buildSummary(request);

                DispatchGuideSummary savedSummary =
                        summaryRepository.save(summary);

                /*
                 * Procesamiento correcto.
                 * Se confirma el mensaje y RabbitMQ lo elimina.
                 */
                channel.basicAck(deliveryTag, false);

                System.out.println(
                        "ACK enviado. Guía procesada correctamente. ID: "
                                + request.getGuideId()
                );

                return ConsumeMessageResponseDto.processed(
                    savedSummary
                );

            } catch (Exception exception) {

                try {
                     channel.basicNack(
                            deliveryTag,
                             false,
                             false
                     );

                     System.err.println(
                             "NACK enviado. Mensaje dirigido a la DLQ. Causa: "
                                    + exception.getMessage()
                    );

                    return ConsumeMessageResponseDto.sentToDlq(
                            exception.getMessage()
                    );

                } catch (Exception nackException) {

                    throw new IllegalStateException(
                            "Falló el procesamiento y tampoco fue posible "
                                    + "enviar el mensaje a la DLQ.",
                            nackException
                    );
                }
            }
        });
    }

    private DispatchGuideSummary buildSummary(
            DispatchGuideMessageDto request) {

        DispatchGuideSummary summary =
                new DispatchGuideSummary();

        summary.setGuideId(request.getGuideId());
        summary.setTransportista(request.getTransportista());
        summary.setFecha(request.getFecha());
        summary.setPedidoId(request.getPedidoId());
        summary.setOrigen(request.getOrigen());
        summary.setDestino(request.getDestino());
        summary.setDescripcion(request.getDescripcion());
        summary.setFileName(request.getFileName());
        summary.setS3Key(request.getS3Key());

        return summary;
    }

    private void validateMessage(DispatchGuideMessageDto request) {

        if (request == null) {
            throw new IllegalArgumentException(
                    "El mensaje recibido es nulo."
            );
        }

        if (request.getGuideId() == null) {
            throw new IllegalArgumentException(
                    "El mensaje no contiene guideId."
            );
        }

        if (request.getEfsPath() == null
                || request.getEfsPath().isBlank()) {

            throw new IllegalArgumentException(
                    "El mensaje no contiene efsPath."
            );
        }

        if (request.getS3Key() == null
                || request.getS3Key().isBlank()) {

            throw new IllegalArgumentException(
                    "El mensaje no contiene s3Key."
            );
        }
    }

    private void validateBucketConfigured() {

        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException(
                    "El bucket S3 no está configurado."
            );
        }
    }

    private String resolveContentType(String fileName) {

        if (fileName != null
                && fileName.toLowerCase().endsWith(".txt")) {

            return "text/plain";
        }

        return "application/pdf";
    }
}