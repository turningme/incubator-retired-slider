package org.apache.slider.ext.utils;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.hadoop.io.IOUtils;

/**
 *
 * @author jpliu
 * @date 2020/9/27
 */
public class IOHelper {
    private static final String UTF_8 = "UTF-8";

    public static void writeString(String content, OutputStream dataOutputStream) throws IOException {
        try {
            byte[] b = content.getBytes(UTF_8);
            dataOutputStream.write(b);
            dataOutputStream.flush();
            dataOutputStream.close();
        } finally {
            IOUtils.closeStream(dataOutputStream);
        }
    }
}
