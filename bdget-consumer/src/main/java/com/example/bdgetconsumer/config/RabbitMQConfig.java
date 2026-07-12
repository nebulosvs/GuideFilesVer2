package com.example.bdgetconsumer.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.bdgetconsumer.dto.DispatchGuideMessageDto;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE = "dispatch-guide-queue";
    public static final String EXCHANGE = "dispatch-guide-exchange";
    public static final String ROUTING_KEY = "dispatch-guide";

    public static final String DLQ = "dispatch-guide-dlq";
    public static final String DLX = "dispatch-guide-dlx";
    public static final String DLQ_ROUTING_KEY = "dispatch-guide-dead";

    @Bean
    public DirectExchange dispatchGuideExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue dispatchGuideQueue() {
        return new Queue(
                QUEUE,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange", DLX,
                        "x-dead-letter-routing-key", DLQ_ROUTING_KEY
                )
        );
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DLQ, true, false, false);
    }

    @Bean
    public Binding dispatchGuideBinding(
            Queue dispatchGuideQueue,
            DirectExchange dispatchGuideExchange) {

        return BindingBuilder
                .bind(dispatchGuideQueue)
                .to(dispatchGuideExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(
            Queue deadLetterQueue,
            DirectExchange deadLetterExchange) {

        return BindingBuilder
                .bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {

        Jackson2JsonMessageConverter converter =
                new Jackson2JsonMessageConverter();

        DefaultJackson2JavaTypeMapper typeMapper =
                new DefaultJackson2JavaTypeMapper();

        Map<String, Class<?>> mappings = new HashMap<>();

        mappings.put(
                "com.example.bdgetproducer.dto.DispatchGuideMessageDto",
                DispatchGuideMessageDto.class
        );

        typeMapper.setIdClassMapping(mappings);
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}