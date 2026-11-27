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

    /**
     * 声明文件任务持久化直连交换机
     */
    @Bean
    DirectExchange fileJobsExchange() {
        return ExchangeBuilder
                .directExchange(FileRabbitConstants.JOBS_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * 声明图片处理持久化队列
     */
    @Bean
    Queue fileImageProcessQueue() {
        return QueueBuilder
                .durable(FileRabbitConstants.IMAGE_PROCESS_QUEUE)
                .build();
    }

    /**
     * 将图片处理队列通过指定路由键绑定到文件任务交换机
     */
    @Bean
    Binding fileImageProcessBinding(Queue fileImageProcessQueue, DirectExchange fileJobsExchange) {
        return BindingBuilder
                .bind(fileImageProcessQueue)
                .to(fileJobsExchange)
                .with(FileRabbitConstants.IMAGE_PROCESS_ROUTING_KEY);
    }

    /**
     * 配置图片处理专用监听容器工厂
     */
    @Bean(FileRabbitConstants.IMAGE_LISTENER_CONTAINER_FACTORY)
    SimpleRabbitListenerContainerFactory fileImageListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            FileJobsProperties fileJobsProperties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setPrefetchCount(1);
        int concurrency = fileJobsProperties.getImageConcurrency();
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }
}
