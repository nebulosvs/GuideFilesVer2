package com.example.bdgetproducer.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.example.bdgetproducer.config.RabbitMQConfig;
import com.example.bdgetproducer.dto.DispatchGuideMessageDto;

@Service
public class RabbitMQProducer {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendMessage(DispatchGuideMessageDto dispatchGuide) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                dispatchGuide
        );

        System.out.println(
                "Mensaje enviado a RabbitMQ. Pedido: "
                + dispatchGuide.getPedidoId()
        );
    }
}