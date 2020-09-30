package org.apache.slider.ext.handler.bean;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;


/**
 * Created by jpliu on 2020/9/27.
 */
public class PythonParamBean {
    static final String  TPL_PARAMS = "templates/params.tpl";

    public String getContent() throws IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(TPL_PARAMS);
        List<String> lines = IOUtils.readLines(in);

        StringBuffer sbuf = new StringBuffer();

        Iterator<String> iter = lines.iterator();

        while (iter.hasNext()){
            String line = iter.next();
            sbuf.append(line).append("\n");
        }


        return sbuf.toString();
    }
}
