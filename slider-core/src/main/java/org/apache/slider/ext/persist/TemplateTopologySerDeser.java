package org.apache.slider.ext.persist;

import java.io.IOException;

import org.apache.slider.core.persist.JsonSerDeser;
import org.apache.slider.ext.TemplateTopology;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;

/**
 * Created by jpliu on 2020/9/23.
 */
public class TemplateTopologySerDeser extends JsonSerDeser<TemplateTopology> {

    public TemplateTopologySerDeser() {
        super(TemplateTopology.class);
    }


    private static final TemplateTopologySerDeser INSTANCE = new TemplateTopologySerDeser();

    public static String toString(TemplateTopology instance) throws IOException,
            JsonGenerationException,
            JsonMappingException {
        synchronized (INSTANCE) {
            return INSTANCE.toJson(instance);
        }
    }

}
