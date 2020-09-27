package org.apache.slider.ext.redstats;

/**
 * Created by jpliu on 2020/9/25.
 */
public class RedstatsConfigs {
    private String kafkaHosts;
    private String topic;

    public RedstatsConfigs() {
    }


    public String getKafkaHosts() {
        return kafkaHosts;
    }

    public void setKafkaHosts(String kafkaHosts) {
        this.kafkaHosts = kafkaHosts;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
