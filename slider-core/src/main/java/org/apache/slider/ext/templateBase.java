package org.apache.slider.ext;

/**
 * Created by jpliu on 2020/9/23.
 */
public class TemplateBase {
    String name;
    String path;
    Integer memXms;
    Integer memXmx;

    public Integer getMemXms() {
        return memXms;
    }

    public void setMemXms(Integer memXms) {
        this.memXms = memXms;
    }

    public Integer getMemXmx() {
        return memXmx;
    }

    public void setMemXmx(Integer memXmx) {
        this.memXmx = memXmx;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
