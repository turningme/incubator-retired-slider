package org.apache.slider.ext.redstats;

import java.io.Reader;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.hadoop.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by jpliu on 2020/9/25.
 */
public class RedstatsParser {
    static final Logger LOG = LoggerFactory.getLogger(RedstatsParser.class);
    HierarchicalINIConfiguration conf = new HierarchicalINIConfiguration();
    RedstatsConfigs configs = new RedstatsConfigs();

    public void parse(Reader reader) throws ConfigurationException {
        conf.load(reader);
        parseKafka();
        IOUtils.closeStream(reader);
    }



    void parseKafka(){
        SubnodeConfiguration kafkaSection = conf.getSection("kafka");
        if (!kafkaSection.isEmpty()){
            LOG.info("[kafka] section");
            String topic = kafkaSection.getString("topic");
            kafkaSection.setProperty("","");
            LOG.info("topic={}", topic);
            configs.setTopic(topic);
        }
    }


    public HierarchicalINIConfiguration getConf() {
        return conf;
    }

    public void setConf(HierarchicalINIConfiguration conf) {
        this.conf = conf;
    }

    public RedstatsConfigs getConfigs() {
        return configs;
    }

    public void setConfigs(RedstatsConfigs configs) {
        this.configs = configs;
    }

}
