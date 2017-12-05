package com.wutong.datacenter.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class OrderUtil {


    public static void main(String[] args) {
        getBidOrderId();
    }

    public static String getBidOrderId(){
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");//
        Date date = new Date();
        String format = df.format(date);
//        System.out.println(format);
        String uuid = UUID.randomUUID().toString().split("-")[0];
        String s = format + uuid;
        System.out.println(s);
        return s;
    }
}
