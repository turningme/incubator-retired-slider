package org.apache.slider.ext.handler;

import org.apache.slider.ext.TemplateTopology;

/**
 * Created by jpliu on 2020/9/25.
 */
public interface Handler<T> {
    void handle(TemplateTopology templateTopology);

    T getHandle();
}
