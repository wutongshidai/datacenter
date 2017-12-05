package com.wutong.datacenter.common.jms.utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.Map;

public class DataTopicUtils {


    public static final JSONObject toJSONObject(Map<String, ? extends Serializable> data) throws JSONException {
        JSONObject jsonObject = new JSONObject(data);
        return jsonObject;
    }
}
