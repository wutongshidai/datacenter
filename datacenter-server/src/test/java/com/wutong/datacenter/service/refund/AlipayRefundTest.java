//package com.wutong.datacenter.service.refund;
//
//import com.alipay.api.AlipayApiException;
//import com.alipay.api.AlipayClient;
//import com.alipay.api.DefaultAlipayClient;
//import com.alipay.api.request.AlipayFundTransToaccountTransferRequest;
//import com.alipay.api.response.AlipayFundTransToaccountTransferResponse;
//
//public class AlipayRefundTest {
//
//    public static void main(String[] args) throws AlipayApiException {
//        AlipayClient alipayClient = new DefaultAlipayClient("https://openapi.alipay.com/gateway.do","app_id","your private_key","json","GBK","alipay_public_key","RSA2");
//        AlipayFundTransToaccountTransferRequest request = new AlipayFundTransToaccountTransferRequest();
//        request.setBizContent("{" +
//                "    \"out_biz_no\":\"3142321423432\"," +
//                "    \"payee_type\":\"ALIPAY_LOGONID\"," +
//                "    \"payee_account\":\"abc@sina.com\"," +
//                "    \"amount\":\"12.23\"," +
//                "    \"payer_show_name\":\"上海交通卡退款\"," +
//                "    \"payee_real_name\":\"张三\"," +
//                "    \"remark\":\"转账备注\"," +
//                "  }");
//        AlipayFundTransToaccountTransferResponse response = alipayClient.execute(request);
//        if(response.isSuccess()){
//            System.out.println("调用成功");
//        } else {
//            System.out.println("调用失败");
//        }
//    }
//}
