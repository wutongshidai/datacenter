package com.wutong.datacenter.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="tasks.refund.alipay")
public class AlipayRefundProperties {

	private Integer delay;

	public Integer getDelay() {
		return delay;
	}

	public void setDelay(Integer delay) {
		this.delay = delay;
	}
}
