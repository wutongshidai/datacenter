package com.wutong.datacenter.client.sender;

import com.wutong.datacenter.core.Message;
import com.wutong.datacenter.core.RabbitTemplateFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


@Component
public class DataCenterClient {

    @Autowired
    private RabbitTemplateFactory rabbitTemplateFactory;

    public void send(Message message) {
        rabbitTemplateFactory.getTemplate(message.getTopic()).convertAndSend(message.getTopic(), message);
    }

}
