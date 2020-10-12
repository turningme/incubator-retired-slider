package org.apache.slider.common.tools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.io.IOUtils;

/**
 * Created by jpliu on 2020/10/12.
 */
public class SliderFSUtils {

    public static void retriveConfigContentAsString(String resourcePath, Map<String,String> mapHolder) throws IOException {
        SliderFileSystem sliderFileSystem = new SliderFileSystem(new Configuration());
        FSDataInputStream fsDataInputStream = sliderFileSystem.getFileSystem().open(new org.apache.hadoop.fs.Path(resourcePath));

        StringBuffer sbuf = new StringBuffer();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fsDataInputStream));
        String temp = "";
        while ((temp = bufferedReader.readLine()) != null){
            sbuf.append(temp).append("  |  ");
        }

        mapHolder.put("Content", sbuf.toString());
        IOUtils.closeStream(bufferedReader);
        IOUtils.closeStream(fsDataInputStream);
    }
}
