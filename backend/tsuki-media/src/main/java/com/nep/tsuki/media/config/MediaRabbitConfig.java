package com.nep.tsuki.media.config;

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

import com.nep.tsuki.media.constants.MediaRabbitConstants;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MediaJobsProperties.class)
public class MediaRabbitConfig {
    /**
     * 声明媒体任务持久化直连交换机
     */
    @Bean
    DirectExchange mediaJobsExchange() {
        return ExchangeBuilder
                .directExchange(MediaRabbitConstants.JOBS_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 声明图片处理持久化队列
     */
    @Bean
    Queue mediaImageProcessQueue() {
        return QueueBuilder
                .durable(MediaRabbitConstants.IMAGE_PROCESS_QUEUE)
                .build();
    }

    /**
     * 将图片处理队列通过指定路由键绑定到媒体任务交换机
     */
    @Bean
    Binding mediaImageProcessBinding(Queue mediaImageProcessQueue, DirectExchange mediaJobsExchange) {
        return BindingBuilder
                .bind(mediaImageProcessQueue)
                .to(mediaJobsExchange)
                .with(MediaRabbitConstants.IMAGE_PROCESS_ROUTING_KEY);
    }

    /**
     * 配置图片处理专用监听容器工厂
     */
    @Bean(MediaRabbitConstants.IMAGE_LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory mediaImageListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MediaJobsProperties mediaJobsProperties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        int concurrency = mediaJobsProperties.getImageConcurrency();
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }
}
