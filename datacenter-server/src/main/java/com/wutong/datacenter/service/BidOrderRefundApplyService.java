package com.wutong.datacenter.service;

import com.alibaba.dubbo.config.annotation.Reference;
import com.parasol.core.bid.Bid_order;
import com.parasol.core.refund.BidOrderRefundTask;
import com.parasol.core.refund.BidRefundOrder;
import com.parasol.core.service.BidOrderRefundTaskService;
import com.parasol.core.service.BidRefundOrderService;
import com.parasol.core.service.BidService;
import com.wutong.datacenter.client.sender.DataCenterClient;
import com.wutong.datacenter.configuration.AlipayRefundProperties;
import com.wutong.datacenter.configuration.RedisProperties;
import com.wutong.datacenter.core.Message;
import com.wutong.datacenter.utils.OrderUtil;

import org.apache.commons.lang.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.redisson.client.RedisClientConfig;
import org.redisson.client.RedisConnection;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.config.Config;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 退款申请处理服务
 * @author Y.H
 *
 */
@Component
@EnableConfigurationProperties(value= {AlipayRefundProperties.class, RedisProperties.class})
public class BidOrderRefundApplyService {

	@Autowired
	private AlipayRefundProperties alipayRefundProperties;
	
	@Autowired
	private RedisProperties redisProperties;
	
	@Reference
	private BidService bidService;

	@Reference
	private BidRefundOrderService bidRefundOrderService;
	
	@Reference
	private BidOrderRefundTaskService bidOrderRefundTaskService;
	
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
			//加锁，防止并发或多次执行同一任务，采用分布式锁+业务锁模式
			Config config = new Config();
			config.useSingleServer().setAddress(redisProperties.getAddress());
			RedissonClient redissonClient = Redisson.create(config);
			RLock lock = redissonClient.getLock("refund_service_" + orderId);
			lock.lock();
			//业务锁
			RedisClientConfig redisClientConfig = new RedisClientConfig();
			redisClientConfig.setAddress(redisProperties.getAddress());
			RedisClient client = RedisClient.create(redisClientConfig);
			RedisConnection connection = client.connect();
			Object exeStatus = connection.sync(StringCodec.INSTANCE, RedisCommands.GET, "refund_execute_" + orderId);
			if (exeStatus != null) {//正在对该订单执行退款任务， 进程退出，保证每个退款任务只有一个进程执行，无论成败
				return;
			}
			connection.sync(StringCodec.INSTANCE,  RedisCommands.SET, "refund_execute_" + orderId, 1);
			
			int refundOperaterUserId = Integer.valueOf(data.get("refundUserId"));
			Bid_order bidOrder = findOrder(orderId);
			// step2 校验订单是否为可退款
			if (refundAble(bidOrder)) {
				// step3 创建退款订单
				BidRefundOrder bidRefundOrder = createRefundOrder(bidOrder, refundOperaterUserId);
				Integer result = bidRefundOrderService.createRefundOrder(bidRefundOrder);
				if (result == 1) {
					
					String payChannel = bidOrder.getPayChannel();
					if (payChannel != null) {
						// step4 发送退款执行指令
						Message refundOperateMessage = new Message();
						Map<String, String> refundOperateData = new HashMap<>();
						refundOperateData.put("refundOrderId", bidRefundOrder.getRefundOrderId());
						refundOperateData.put("bidOrderId", bidOrder.getId());
						refundOperateData.put("refundUserId", String.valueOf(refundOperaterUserId));
						refundOperateData.put("payChannel", payChannel);
//						dataCenterClient.send(refundOperateMessage); //变更为延迟5小时候执行
						
						//创建订单退款任务
						BidOrderRefundTask bidOrderRefundTask = new BidOrderRefundTask();
						bidOrderRefundTask.setBidOrderId(bidOrder.getId());
						String paramCode = OrderUtil.getBidOrderId();
						bidOrderRefundTask.setParamCode(paramCode);
						int taskId = bidOrderRefundTaskService.create(bidOrderRefundTask);
						refundOperateData.put("refundTaskId", String.valueOf(taskId));
						refundOperateData.put("paramCode", paramCode);
						
						refundOperateMessage.setTopic("refund_deposit");
						refundOperateMessage.setData(refundOperateData);
						
						//修改订单状态为正在退款
						updateRefunding(bidOrder);
						
						//发布延时任务，等待执行
						DelayWorkService.runTask(refundOperateMessage, dataCenterClient, alipayRefundProperties.getDelay(), TimeUnit.HOURS);
					} else {
						resultMessage = "支付方式未知。";
					}
				} else {
					resultMessage = "创建退款订单失败";
				}
			} else {
				resultMessage = "当前订单不可退款";
			}
			connection.sync(StringCodec.INSTANCE, RedisCommands.DEL, "refund_execute_" + orderId);
			lock.unlock();//释放锁
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
		int refundTaskId = Integer.valueOf(message.getData().get("refundTaskId").toString());
		bidOrderRefundTaskService.updateStatus(refundTaskId, 1);
		System.out.println("订单状态更新" + (rows > 0 ? "成功" : "失败"));
		message.setTopic("process_bid_order_after_refund");
		dataCenterClient.send(message);
		
	}
	
	/**
	 * 转账失败后回调, 执行数据状态更新，与成功操作完全一致，单独列出目的为了后面业务扩展
	 * @param message
	 */
	@RabbitHandler
	@RabbitListener(queues="refund_deposit_error")
	public void callbackError(Message message) {
		Map<String, String> data = (Map<String, String>)message.getData();
		String refundOrderId = data.get("refundOrderId");
		int refundStatus = Integer.valueOf(data.get("refundStatus"));
		int rows = bidRefundOrderService.updateRefundOrderStatus(refundOrderId, refundStatus);
		int refundTaskId = Integer.valueOf(message.getData().get("refundTaskId").toString());
		bidOrderRefundTaskService.updateStatus(refundTaskId, 8);
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
		//释放业务锁
		RedisClientConfig redisClientConfig = new RedisClientConfig();
		redisClientConfig.setAddress(redisProperties.getAddress());
		RedisClient client = RedisClient.create(redisClientConfig);
		RedisConnection connection = client.connect();
		connection.sync(StringCodec.INSTANCE, RedisCommands.DEL, "refund_execute_" + bidOrderId);
	}
	
	
	@RabbitHandler
	@RabbitListener(queues="refund_deposit")
	public void processDeposit(Message message) {
		//step1 查询退款订单
		String refundOrderId = String.valueOf(message.getData().get("refundOrderId"));
		String bidOrderId = String.valueOf(message.getData().get("bidOrderId"));
		
		
		BidRefundOrder bidRefundOrder = findRefundOrder(refundOrderId);
		
		//验证退款任务有效
		int refundTaskId = Integer.valueOf(message.getData().get("refundTaskId").toString());
		BidOrderRefundTask task = bidOrderRefundTaskService.findById(refundTaskId);
		if (task != null && task.getStatus().intValue() == 0) {
			boolean result = bidOrderRefundTaskService.updateStatus(refundTaskId, 9);//变更状态为执行中
			if (result) {
				double amount = bidRefundOrder.getRefundAmount();
				String payChannel = String.valueOf(message.getData().get("payChannel"));
		
				//发送退款指令给指定支付渠道
				Message payMessage = new Message();
				Map<String, String> data = (Map<String, String>) message.getData();
		//		data.put("account", account);
				data.put("amount", String.valueOf(amount));
				data.put("refundOrderId", String.valueOf(refundOrderId));
				data.put("bidOrderId", String.valueOf(bidOrderId));
				payMessage.setTopic(payChannel + "_refund");
				payMessage.setData(data);
				dataCenterClient.send(payMessage);
			}
		}
		//step2 查询用户绑定的默认退款账号
//		int refundTargetUserId = bidRefundOrder.getRefundTargetUserId();
//		//step3 根据账号类型选择退款通道
//		PurseInfo purseInfo = findDefault(refundTargetUserId);
//		if (purseInfo != null) {
//			String purseType = purseInfo.getAccountType();
//			String account = purseInfo.getAccount();
//			double amount = bidRefundOrder.getRefundAmount();
//			//step4 退款
//			//发送退款指令给指定支付渠道
//			Message payMessage = new Message();
//			Map<String, String> data = (Map<String, String>) message.getData();
//			data.put("account", account);
//			data.put("amount", String.valueOf(amount));
//			payMessage.setTopic(purseType + "_refund");
//			payMessage.setData(data);
//			dataCenterClient.send(payMessage);
//		}

		//释放业务锁
		RedisClientConfig redisClientConfig = new RedisClientConfig();
		redisClientConfig.setAddress(redisProperties.getAddress());
		RedisClient client = RedisClient.create(redisClientConfig);
		RedisConnection connection = client.connect();
		connection.sync(StringCodec.INSTANCE, RedisCommands.DEL, "refund_execute_" + bidRefundOrder.getBidOrderId());
		//step5 写入日志
	}
	
	private BidRefundOrder findRefundOrder(String refundOrderId) {
		return bidRefundOrderService.findById(refundOrderId);
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
	
	private boolean updateRefunding(Bid_order bidOrder) {
		bidOrder.setRefundStatus(9);
		return bidService.updateOrder(bidOrder) == 1;
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
