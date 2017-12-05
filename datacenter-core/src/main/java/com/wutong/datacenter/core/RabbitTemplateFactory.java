package com.wutong.datacenter.core;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;


@Component
public class RabbitTemplateFactory {

    @Autowired
    private ConnectionFactory connectionFactory;

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    private Map<String, RabbitTemplate> templateCache = new HashMap<>();

    public RabbitTemplate getTemplate(String topicName) {
        if (templateCache.containsKey(topicName)) {
            return templateCache.get(topicName);
        }
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setQueue(topicName);
        templateCache.put(topicName, rabbitTemplate);
        return rabbitTemplate;
    }
}
