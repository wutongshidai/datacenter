package com.wutong.datacenter.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.alibaba.fastjson.JSONObject;
import com.parasol.core.bid.Bid_order;
import com.parasol.core.purse.PurseInfo;
import com.parasol.core.refund.BidRefundOrder;
import com.parasol.core.service.BidRefundOrderService;
import com.parasol.core.service.BidService;
import com.parasol.core.service.PurseInfoService;
import com.wutong.datacenter.client.sender.DataCenterClient;
import com.wutong.datacenter.core.Message;
import com.wutong.datacenter.utils.OrderUtil;

import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 退款申请处理服务
 * @author Y.H
 *
 */
@Component
public class BidOrderRefundApplyService {

	@Reference
	private BidService bidService;

	@Reference
	private BidRefundOrderService bidRefundOrderService;
	
	@Autowired
	private DataCenterClient dataCenterClient;

	@SuppressWarnings("unchecked")
	@RabbitHandler
	@RabbitListener(queues = "refund_deposit_apply")
	public void process(Message message) {
		String applyResultCode = "100001";
		String resultMessage = "异步系统处理请求失败";
		// step1 获取执行参数，订单标识
		Map<String, String> data = (Map<String, String>) message.getData();
		if (data != null) {
			String orderId = data.get("bidOrderId");
			int refundOperaterUserId = Integer.valueOf(data.get("refundUserId"));
			Bid_order bidOrder = findOrder(orderId);
			// step2 校验订单是否为可退款
			if (refundAble(bidOrder)) {
				// step3 创建退款订单
				BidRefundOrder bidRefundOrder = createRefundOrder(bidOrder, refundOperaterUserId);
				Integer result = bidRefundOrderService.createRefundOrder(bidRefundOrder);
				if (result == 1) {
					// step4 发送退款执行指令
					Message refundOperateMessage = new Message();
					Map<String, String> refundOperateData = new HashMap<>();
					refundOperateData.put("refundOrderId", bidRefundOrder.getRefundOrderId());
					refundOperateData.put("bidOrderId", bidOrder.getId());
					refundOperateData.put("refundUserId", String.valueOf(refundOperaterUserId));
					refundOperateMessage.setTopic("refund_deposit");
					refundOperateMessage.setData(refundOperateData);
					dataCenterClient.send(refundOperateMessage);
//					return;
				} else {
					resultMessage = "创建退款订单失败";
				}
			} else {
				resultMessage = "当前订单不可退款";
			}
		} else {
			resultMessage = "未取得客户端信息";
		}
		//TODO 写入申请执行日志
	}
	
	/**
	 * 转账成功后回调, 执行数据状态更新，
	 * @param message
	 */
	@RabbitHandler
	@RabbitListener(queues="refund_deposit_success")
	public void callback(Message message) {
		Map<String, String> data = (Map<String, String>)message.getData();
		String refundOrderId = data.get("refundOrderId");
		int refundStatus = Integer.valueOf(data.get("refundStatus"));
		int rows = bidRefundOrderService.updateRefundOrderStatus(refundOrderId, refundStatus);
		System.out.println("订单状态更新" + (rows > 0 ? "成功" : "失败"));
		message.setTopic("process_bid_order_after_refund");
		dataCenterClient.send(message);
		
	}
	
	@RabbitHandler
	@RabbitListener(queues="process_bid_order_after_refund")
	public void processBidOrder(Message message) {
		Map<String, String> data = (Map<String, String>)message.getData();
		String bidOrderId = data.get("bidOrderId");
		Bid_order bidOrder = findOrder(bidOrderId);
		bidOrder.setRefundStatus(Integer.valueOf(data.get("refundStatus")));
		bidOrder.setRefundUser(Integer.valueOf(data.get("refundUserId")));
		bidOrder.setUpdatetime(new Date(System.currentTimeMillis()));
		bidService.updateOrder(bidOrder);
	}
	
	
	@Reference
	private PurseInfoService purseInfoService;
	
	@RabbitHandler
	@RabbitListener(queues="refund_deposit")
	public void processDeposit(Message message) {
		//step1 查询退款订单
		String refundOrderId = String.valueOf(message.getData().get("refundOrderId"));
		BidRefundOrder bidRefundOrder = findRefundOrder(refundOrderId);
		//step2 查询用户绑定的默认退款账号
		int refundTargetUserId = bidRefundOrder.getRefundTargetUserId();
		//step3 根据账号类型选择退款通道
		PurseInfo purseInfo = findDefault(refundTargetUserId);
		if (purseInfo != null) {
			String purseType = purseInfo.getAccountType();
			String account = purseInfo.getAccount();
			double amount = bidRefundOrder.getRefundAmount();
			//step4 退款
			//发送退款指令给指定支付渠道
			Message payMessage = new Message();
			Map<String, String> data = (Map<String, String>) message.getData();
			data.put("account", account);
			data.put("amount", String.valueOf(amount));
			payMessage.setTopic(purseType + "_refund");
			payMessage.setData(data);
			dataCenterClient.send(payMessage);
		}
		
		//step5 写入日志
	}
	
	private BidRefundOrder findRefundOrder(String refundOrderId) {
		return bidRefundOrderService.findById(refundOrderId);
	}

	private PurseInfo findDefault(int userId) {
		return purseInfoService.findDefault(userId);
	}
	

	/**
	 * 根据保证金订单编号查询订单
	 * @param orderId
	 * @return
	 */
	private Bid_order findOrder(String orderId) {
		if (StringUtils.isNotBlank(orderId)) {
			return bidService.getMyBidById(orderId);
		}
		return null;
	}

	/**
	 * 校验订单可退款状态
	 * 
	 * @param orderId
	 * @return
	 */
	private boolean refundAble(Bid_order bidOrder) {
		if (bidOrder != null) {
			return bidOrder.getPayStatus() == 1 && bidOrder.getRefundStatus() == 0;
		}
		return false;
	}

	/**
	 * 创建退款订单
	 * @param bidOrder
	 * @return
	 */
	private BidRefundOrder createRefundOrder(Bid_order bidOrder, int refundOperaterUserId) {
		double bidBond = bidOrder.getBidBond();
		int refundTargetUserId = bidOrder.getComUserid();
		BidRefundOrder bidRefundOrder = new BidRefundOrder();
		bidRefundOrder.setBidOrderId(bidOrder.getId());
		bidRefundOrder.setRefundAmount(bidBond);
		bidRefundOrder.setRefundOrderId(OrderUtil.getBidOrderId());
		bidRefundOrder.setRefundTargetUserId(refundTargetUserId);
		bidRefundOrder.setRefundUserId(refundOperaterUserId);
		return bidRefundOrder;
	}
}
