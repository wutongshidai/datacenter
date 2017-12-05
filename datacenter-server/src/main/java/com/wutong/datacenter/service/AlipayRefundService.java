package com.wutong.datacenter.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayFundTransToaccountTransferRequest;
import com.alipay.api.response.AlipayFundTransToaccountTransferResponse;
import com.parasol.core.refund.BidRefundOrder;
import com.parasol.core.service.BidRefundOrderService;
import com.parasol.core.service.PurseInfoService;
import com.wutong.datacenter.client.sender.DataCenterClient;
import com.wutong.datacenter.configuration.AlipayProperties;
import com.wutong.datacenter.core.Message;
import net.sf.json.JSONObject;

/**
 * 支付宝支付服务
 * 
 * @author Y.H
 *
 */
@Component
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayRefundService {

	@Autowired
	private AlipayProperties alipayProperties;
	
	@Autowired
	private DataCenterClient dataCenterClient;
	
	@RabbitHandler
	@RabbitListener(queues="alipay_refund")
	public void process(Message message) {
		AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do",
				alipayProperties.getAppId(), alipayProperties.getPrivateKey(), "json", "GBK",
				alipayProperties.getAlipayPublicKey(), "RSA2");
		AlipayFundTransToaccountTransferRequest request = new AlipayFundTransToaccountTransferRequest();
		Map<String, String> data = (Map<String, String>)message.getData();
		String refundOrderId = data.get("refundOrderId");
			Map<String, Object> bizContent = new HashMap<>();
			bizContent.put("out_biz_no", refundOrderId);
			bizContent.put("payee_type", "ALIPAY_LOGONID");
			bizContent.put("payee_account", data.get("account"));
			bizContent.put("amount", data.get("amount"));
//			bizContent.put("payer_show_name", payerShowName);
//			bizContent.put("payee_real_name", payeeRealName);
			bizContent.put("remark", "投标保证金退款");
			request.setBizContent(JSONObject.fromObject(bizContent).toString());
			try {
				AlipayFundTransToaccountTransferResponse response = alipayClient.execute(request);
				if (response.isSuccess()) {
					System.out.println("调用成功");
					Message successMessage = new Message();
					successMessage.setTopic("refund_deposit_success");
					Map<String, String> successData = data;
					successData.put("refundOrderId", refundOrderId);
					successData.put("refundStatus", "1");
					successMessage.setData(successData);
					dataCenterClient.send(successMessage);
				} else {
					System.out.println("调用失败");
				}
			} catch (AlipayApiException e) {
				e.printStackTrace();
			}
	}
}
