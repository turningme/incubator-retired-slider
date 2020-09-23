package org.apache.slider.ext;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author jpliu
 * @date 2020/9/23
 *
 * name + seq  is its unique key .
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class TemplateInstance  extends TemplateBase{
    int seq;
    List<Integer> partitions;


    public TemplateInstance(List<Integer> partitions, int seq) {
        this.partitions = partitions;
        this.seq = seq;
    }


    public List<Integer> getPartitions() {
        return partitions;
    }

    public int getSeq() {
        return seq;
    }

    @Override
    public String getName() {
        return name + "-" + seq;
    }

    public void setPartitions(List<Integer> partitions) {
        this.partitions = partitions;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }
}
