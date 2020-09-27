package org.apache.slider.ext.redstats;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.util.StringUtils;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.ext.AppConfigManager;
import org.apache.slider.ext.Template;
import org.apache.slider.ext.TemplateInstance;
import org.apache.slider.ext.TemplateTopology;

/**
 * Created by jpliu on 2020/9/27.
 *
 */
public class RedstatsConfigManager implements AppConfigManager{


    public RedstatsConfigManager() {
    }


    /**
     * parse local config file , split it into many partitions , and save it .
     * @param sliderFileSystem
     * @throws ConfigurationException
     * @throws IOException
     */
    public void process(SliderFileSystem sliderFileSystem , TemplateTopology templateTopology) throws ConfigurationException, IOException {


        Iterator<Template> iter = templateTopology.getTemplateMap().values().iterator();
        while (iter.hasNext()){
            Template en = iter.next();
            String parent = en.getPath();
            File parentFile = new File(parent);

            FileReader fileReader = new FileReader(parentFile);
            RedstatsParser redstatsParser = new RedstatsParser();
            redstatsParser.parse(fileReader);
            IOUtils.closeStream(fileReader);


            for (TemplateInstance templateInstance: en.getTemplateInstanceList()) {
                RedstatsConfigRewrite redstatsConfigRewrite = new RedstatsConfigRewrite();
                redstatsConfigRewrite.init(redstatsParser.getConf());
                redstatsConfigRewrite.rewritePartitions(StringUtils.join(",",templateInstance.getPartitions()));

                Path targetPath = new Path(templateInstance.getPath());

                //save to hdfs path , overwrite if exists .
                OutputStream targetOutputStream = sliderFileSystem.getFileSystem().create(targetPath,true);
                redstatsConfigRewrite.save(targetOutputStream);
                IOUtils.closeStream(targetOutputStream);
            }

        }


    }


    public void compelete(SliderFileSystem sliderFileSystem){
        // TODO: 2020/9/27  
    }



}
