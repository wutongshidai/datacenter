package com.wutong.datacenter.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Message implements Serializable {

    public Message(String topic, Map<String, ? extends Serializable> data) {
        this.topic = topic;
        this.data = data;
    }

    public Message(){}

    private String topic;
    
    private String method;

    private Map<String, ? extends Serializable> data = new HashMap<>();

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Map<String, ? extends Serializable> getData() {
        return data;
    }

    public void setData(Map<String, ? extends Serializable> data) {
        this.data = data;
    }

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}
}
