package org.apache.slider.ext.tpl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jpliu
 * @date 2020/10/12
 */
public class TemplateClusterConfParser {
    static final Logger LOG = LoggerFactory.getLogger(TemplateClusterConf.class);

    protected final GsonBuilder gsonBuilder = new GsonBuilder();
    protected final Gson gson;

    public TemplateClusterConfParser() {
        gson = gsonBuilder.setPrettyPrinting().create();
    }

    public TemplateClusterConf fromJsonString(String json){
        return gson.fromJson(json,TemplateClusterConf.class);
    }

    public TemplateClusterConf fromJsonStream(InputStream is) throws IOException {
        LOG.debug("loading from input stream");
        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer);
        return fromJsonString(writer.toString());
    }


    public static void main(String[] args) throws IOException {
        String path = "/Users/jpliu/github/incubator-retired-slider/app-packages/app-statsengine/t1.json";
        FileInputStream fileInputStream =  new FileInputStream(path);

        TemplateClusterConfParser parser = new TemplateClusterConfParser();
        TemplateClusterConf templateClusterConf = parser.fromJsonStream(fileInputStream);

        System.out.println("tttt");
    }
}
