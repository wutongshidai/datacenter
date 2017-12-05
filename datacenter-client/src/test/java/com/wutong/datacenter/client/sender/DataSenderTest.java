package com.wutong.datacenter.client.sender;

import com.wutong.datacenter.DataSenderApplication;
import com.wutong.datacenter.core.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = DataSenderApplication.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class DataSenderTest {

    @Autowired
    public DataCenterClient dataCenterClient;

    private String[] orderIds = {"201711174a03c08d"};//, "201711173768ce6a", "201711174a03c08d", "20171117cbe5aad3"};

    @Test
    public void testSend() {
        for (int i = 0; i < orderIds.length; i++) {
            Map<String, String> data = new HashMap<>();
            data.put("bidOrderId", String.valueOf(orderIds[i]));
            data.put("refundUserId", "1");
//            data.put("refundStatus", "1");
            Message message = new Message();
            message.setTopic("refund_deposit_apply");
            message.setData(data);
            dataCenterClient.send(message);
        }

    }
}
