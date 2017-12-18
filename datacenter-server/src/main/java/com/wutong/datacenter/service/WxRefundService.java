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
import com.parasol.core.service.AlipayService;
import com.parasol.core.service.BidRefundOrderService;
import com.parasol.core.service.PurseInfoService;
import com.wutong.datacenter.client.sender.DataCenterClient;
import com.wutong.datacenter.configuration.AlipayProperties;
import com.wutong.datacenter.core.Message;
import com.wutong.wxpay.core.exception.WxPayException;
import com.wutong.wxpay.core.request.WxPayRefundRequest;
import com.wutong.wxpay.core.result.WxPayRefundResult;
import com.wutong.wxpay.core.service.WxPayService;

import net.sf.json.JSONObject;

/**
 * 支付宝支付服务
 * 
 * @author Y.H
 *
 */
@Component
public class WxRefundService {

	
	@Autowired
	private DataCenterClient dataCenterClient;
	
	@Autowired
	private WxPayService wxPayService;
	
	@RabbitHandler
	@RabbitListener(queues="WXPAY_PC_refund")
	public void process(Message message) {
		Map<String, String> data = (Map<String, String>)message.getData();
		String bidOrderId = String.valueOf(data.get("bidOrderId"));
		String refundOrderId = String.valueOf(data.get("refundOrderId"));
		double refundAmount = Double.valueOf(data.get("amount").toString());
		
		WxPayRefundRequest wxPayRefundRequest = new WxPayRefundRequest();
		wxPayRefundRequest.setOutRefundNo(refundOrderId);
		wxPayRefundRequest.setOutTradeNo(bidOrderId);
		wxPayRefundRequest.setTotalFee(Double.valueOf(refundAmount * 100).intValue());
		wxPayRefundRequest.setRefundFee(Double.valueOf(refundAmount * 100).intValue());
//		wxPayRefundRequest.setRefundFee(960);
		wxPayRefundRequest.setRefundDesc("保证金退款");
		Message successMessage = new Message();
		Map<String, String> successData = data;
		successData.put("refundOrderId", refundOrderId);
		try {
			WxPayRefundResult result = wxPayService.refund(wxPayRefundRequest);
			String resultCode = result.getResultCode();
			if ("SUCCESS".equals(resultCode)) {
				System.out.println("调用成功");
				successMessage.setTopic("refund_deposit_success");
				successData.put("refundStatus", "1");
			} else {
				System.out.println("调用失败");
				successMessage.setTopic("refund_deposit_error");
				successData.put("refundStatus", "0");
			}
			successMessage.setData(successData);
		} catch (WxPayException e) {
			System.out.println("调用失败");
			successMessage.setTopic("refund_deposit_error");
			successData.put("refundStatus", "0");
			successMessage.setData(successData);
			e.printStackTrace(System.err);
		} finally {
			dataCenterClient.send(successMessage);
		}
	}
}
