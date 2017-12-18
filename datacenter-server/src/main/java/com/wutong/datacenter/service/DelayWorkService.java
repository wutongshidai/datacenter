package com.wutong.datacenter.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.wutong.datacenter.client.sender.DataCenterClient;
import com.wutong.datacenter.core.Message;

public class DelayWorkService {
	
	private DataCenterClient client;
	
	private Message message;
	
	/**
	 * 初始化线程池，容量10
	 */
	private ScheduledExecutorService scheduledExecutorServicePool = Executors.newScheduledThreadPool(10);

	public ScheduledExecutorService getScheduledExecutorServicePool() {
		return scheduledExecutorServicePool;
	}


	/**
	 * 延迟执行时间
	 */
	private Long delay;
	
	/**
	 * 延迟构造
	 * @param delay 延迟时间，单位"秒"
	 */
	public DelayWorkService(Message message, DataCenterClient client, Long delay) {
		this.message = message;
		this.client = client;
		this.delay = delay;
	}
	
	/**
	 * 延迟构造
	 * @param delay 延迟时间
	 * @param timeUnit 时间单位，仅支持TimeUnit.HOURS, TimeUnit.MINUTES, TimeUnit.SECONDS
	 */
	public DelayWorkService(Message message, DataCenterClient client, Long delay, TimeUnit timeUnit) {
		this.message = message;
		if (timeUnit.equals(TimeUnit.HOURS)) {
			this.delay = delay * 60 * 60;
		} else if (timeUnit.equals(TimeUnit.MINUTES)) {
			this.delay = delay * 60;
		} else if (timeUnit.equals(TimeUnit.SECONDS)) {
			this.delay = delay;
		}
		this.client = client;
	}
	
	/**
	 * 延迟执行指定任务
	 * @param message
	 * @param delay 延迟时间，单位秒
	 */
	public static void runTask(Message message, DataCenterClient client, long delay) {
		runTask(message, client, delay, TimeUnit.SECONDS);
	}
	
	public static void runTask(Message message, DataCenterClient client, long delay, TimeUnit timeUnit) {
		DelayWorkService service = new DelayWorkService(message, client, delay, timeUnit);
		service.run();
	}
	
	private void run() {
		RefundTask task = new RefundTask(this.message, this.client);
		this.getScheduledExecutorServicePool().schedule(task, delay, TimeUnit.SECONDS);
	}
	

	class RefundTask implements Runnable {

		private Message message;
		
		private DataCenterClient client;
		
		public RefundTask(Message message, DataCenterClient client) {
			this.message = message;
			this.client = client;
		}
		
		@Override
		public void run() {
			client.send(message);
		}
	}
	
}
