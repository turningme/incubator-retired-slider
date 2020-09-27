package org.apache.slider.ext.redstats;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.io.IOUtils;

/**
 * Created by jpliu on 2020/9/27.
 */
public class RedstatsConfigRewrite {
    HierarchicalINIConfiguration conf ;

    public RedstatsConfigRewrite() {
        conf = new HierarchicalINIConfiguration();
    }

    public void init(HierarchicalINIConfiguration  ini){
        conf = (HierarchicalINIConfiguration) ini.clone();
    }


    public void rewritePartitions(String partitions){
        SubnodeConfiguration kafkaSection = conf.getSection("kafka");
        if (!kafkaSection.isEmpty()){
            kafkaSection.setProperty("consumed..partition..list",partitions);
        }
    }


    public void save(Writer writer) throws ConfigurationException {


        PrintWriter p = new PrintWriter(writer);

        HierarchicalINIConfiguration iniConfiguration = conf ;
        Set<String> sets = iniConfiguration.getSections();
        for (String s:sets) {
            p.println();
            p.print(String.format("[%s]\n",s));
            SubnodeConfiguration subConf = iniConfiguration.getSection(s);
            Iterator<String> keys =  subConf.getKeys();
            while (keys.hasNext()){
                String key = keys.next();
                if (StringUtils.isBlank(key)){
                    continue;
                }

                Object value = subConf.getProperty(key);
                p.print(String.format("%s=%s\n", StringUtils.replace(key, "..", "."), value));
            }
        }


        IOUtils.closeStream(p);
    }


    public void save(OutputStream outputStream) throws ConfigurationException {
        OutputStreamWriter outputStreamWriter  = new OutputStreamWriter(outputStream);
        save(outputStreamWriter);
    }
}
