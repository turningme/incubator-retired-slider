package org.apache.slider.ext;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.hadoop.io.IOUtils;
import org.apache.slider.providers.agent.application.metadata.Metainfo;
import org.apache.slider.providers.agent.application.metadata.MetainfoParser;

/**
 * Created by jpliu on 2020/9/24.
 * processing meta information like  json or  xml style .
 *
 */
public class MetaInfoProcessor extends MetainfoParser{
    private static final String UTF_8 = "UTF-8";

    public String convertXml2Json(InputStream inputStream) throws IOException {
        Metainfo metainfo = fromXmlStream(inputStream);
        return toJsonString(metainfo);
    }



    public void convertXml2JsonFile(InputStream inputStream , OutputStream outputStream) throws IOException {
        Metainfo metainfo = fromXmlStream(inputStream);
        String metainfoStr = toJsonString(metainfo);


        try {
            byte[] b = metainfoStr.getBytes(UTF_8);
            outputStream.write(b);
            outputStream.flush();
            outputStream.close();
        }catch (IOException e){
            throw e;
        }finally {
            IOUtils.closeStream(outputStream);
        }
    }
}
