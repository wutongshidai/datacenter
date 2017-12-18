package com.wutong.datacenter.configuration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.wutong.wxpay.core.config.WxPayConfig;
import com.wutong.wxpay.core.service.WxPayService;
import com.wutong.wxpay.core.service.impl.WxPayServiceImpl;

@Configuration
@ConditionalOnClass(WxPayService.class)
@EnableConfigurationProperties(WxPayProperties.class)
public class WxPayConfiguration {
  @Autowired
  private WxPayProperties properties;

  @Bean
  @ConditionalOnMissingBean
  public WxPayConfig config() {
    WxPayConfig payConfig = new WxPayConfig();
    payConfig.setAppId(this.properties.getAppId());
    payConfig.setMchId(this.properties.getMchId());
    payConfig.setMchKey(this.properties.getMchKey());
    payConfig.setNotifyUrl(this.properties.getNotifyUrl());
    payConfig.setTradeType(this.properties.getTradeType());
    payConfig.setKeyPath(this.properties.getKeyPath());

    return payConfig;
  }

  @Bean("wxPayService")
  public WxPayService wxPayService(WxPayConfig payConfig) {
    WxPayService wxPayService = new WxPayServiceImpl();
    wxPayService.setConfig(payConfig);
    return wxPayService;
  }

}
