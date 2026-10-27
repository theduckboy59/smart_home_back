package com.smarthome.smart_home_back.mqtt;

public interface MqttTransport {

    @FunctionalInterface
    interface Receiver {
        void receive(String topic, String payload, boolean retained);
    }

    boolean connected();

    void publish(String payload) throws Exception;

    void setReceiver(Receiver receiver);
}