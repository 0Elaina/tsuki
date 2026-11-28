package com.nep.tsuki.file.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.nep.tsuki.file.constants.FileRabbitConstants;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FileJobsProperties.class)
public class FileRabbitConfig {

    @Bean
    DirectExchange fileJobsExchange() {
        return ExchangeBuilder
                .directExchange(FileRabbitConstants.JOBS_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    Queue fileInteractiveQueue() {
        return QueueBuilder
                .durable(FileRabbitConstants.INTERACTIVE_QUEUE)
                .build();
    }

    @Bean
    Binding fileInteractiveBinding(Queue fileInteractiveQueue, DirectExchange fileJobsExchange) {
        return BindingBuilder
                .bind(fileInteractiveQueue)
                .to(fileJobsExchange)
                .with(FileRabbitConstants.INTERACTIVE_ROUTING_KEY);
    }

    @Bean
    Queue fileCleanupQueue() {
        return QueueBuilder
                .durable(FileRabbitConstants.CLEANUP_QUEUE)
                .build();
    }

    @Bean
    Binding fileCleanupBinding(Queue fileCleanupQueue, DirectExchange fileJobsExchange) {
        return BindingBuilder
                .bind(fileCleanupQueue)
                .to(fileJobsExchange)
                .with(FileRabbitConstants.CLEANUP_ROUTING_KEY);
    }

    @Bean(FileRabbitConstants.INTERACTIVE_LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory fileInteractiveListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            FileJobsProperties fileJobsProperties) {
        return createContainerFactory(configurer, connectionFactory, fileJobsProperties.getInteractiveConcurrency());
    }

    @Bean(FileRabbitConstants.CLEANUP_LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory fileCleanupListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            FileJobsProperties fileJobsProperties) {
        return createContainerFactory(configurer, connectionFactory, fileJobsProperties.getCleanupConcurrency());
    }

    private SimpleRabbitListenerContainerFactory createContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            int concurrency) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }
}
