//package com.wutong.datacenter.core.configuration;
//
//import org.springframework.amqp.core.*;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class RabbitConfiguration {
//
//    @Bean(name="refund_deposit_apply")
//    public Queue queue() {
//        return new Queue("tender.refund_deposit_apply");
//    }
//
//    @Bean(name="refund_deposit.exchange")
//    public TopicExchange exchange() {
//        return new TopicExchange("tender.refund_deposit.exchange");
//    }
//
//    @Bean
//    public Binding bindingExchangeTopic(@Qualifier("refund_deposit") Queue queue, @Qualifier("refund_deposit.exchange") TopicExchange exchange) {
//        return BindingBuilder.bind(queue).to(exchange).with("tender.refund_deposit");
//    }
//
////    @Bean
////    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
////        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
//////        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
////        return rabbitTemplate;
////    }
//
////    @Bean
////    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
////        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory = new SimpleRabbitListenerContainerFactory();
////        rabbitListenerContainerFactory.setConnectionFactory(connectionFactory);
////        rabbitListenerContainerFactory.setMessageConverter(new Jackson2JsonMessageConverter());
////        return rabbitListenerContainerFactory;
////    }
//
//}
