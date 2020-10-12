package org.apache.slider.ext.tpl;

/**
 * Created by jpliu on 2020/10/12.
 */
public class TemplateConf {
    String name;
    String conf;
    int parallel;
    int partitions;

    /**
     * in MB unit
     */
    int memMax;
    int memMix;


    public String getConf() {
        return conf;
    }

    public void setConf(String conf) {
        this.conf = conf;
    }

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getParallel() {
        return parallel;
    }

    public void setParallel(int parallel) {
        this.parallel = parallel;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }
}
