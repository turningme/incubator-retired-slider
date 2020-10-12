package org.apache.slider.ext.tpl;

/**
 * Created by jpliu on 2020/10/12.
 */
public class TemplateInstanceConf {
    /**
     * in MB unit
     */
    int memMax;
    int memMix;

    public int getMemMax() {
        return memMax;
    }

    public void setMemMax(int memMax) {
        this.memMax = memMax;
    }

    public int getMemMix() {
        return memMix;
    }

    public void setMemMix(int memMix) {
        this.memMix = memMix;
    }
}
