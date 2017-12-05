package com.wutong.datacenter.core.initializer;

import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 根据配置动态注册Queue
 * @author Y.H
 *
 */
public class RabbitConfigurationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		ConfigurableEnvironment env = applicationContext.getEnvironment();
		String topics = env.getProperty("rabbit.topics");
		if (StringUtils.isNotBlank(topics)) {
			String[] ts = topics.split(",");
			for (String topic : ts) {
				topic = topic.trim();
				Queue queue = new Queue(topic);
				TopicExchange topicExchange = new TopicExchange(topic + ".exchange");
				Binding binding = BindingBuilder.bind(queue).to(topicExchange).with(topic);
				applicationContext.getBeanFactory().registerSingleton(topic, queue);
				applicationContext.getBeanFactory().registerSingleton(topic + ".exchange", topicExchange);
				applicationContext.getBeanFactory().registerSingleton(topic + ".binding", binding);
			}
		}
	}

}
