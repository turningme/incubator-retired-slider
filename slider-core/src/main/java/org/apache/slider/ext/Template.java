package org.apache.slider.ext;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.slider.ext.template.PartitionAllocateMap;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

/**
 * @author jpliu
 * @date 2020/9/23
 * Template represents one kind of cluster roles ,  and may exist many kind of instances , named  {@link TemplateInstance}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Template extends TemplateBase {
    int parallelism;
    int partitionNum;

    List<TemplateInstance> templateInstanceList = new ArrayList<>();


    public Template(String name, String path, int parallelism, int partitionNum) {
        this.name = name;
        this.path = path;
        this.parallelism = parallelism;
        this.partitionNum = partitionNum;
    }


    public void merge(Template template) {
        assert StringUtils.isNotBlank(name);
        path = StringUtils.isNotBlank(path) ? path : template.path;
        parallelism = parallelism > 0 ? parallelism : template.parallelism;
        partitionNum = partitionNum > 0 ? partitionNum : template.partitionNum;
    }

    public List<TemplateInstance> resolveInstances() {
        assert parallelism > 0 : "parallelism=" + parallelism +  " is not legal";
        assert partitionNum > 0;
        assert partitionNum >= parallelism;

        PartitionAllocateMap partitionAllocateMap = new PartitionAllocateMap(partitionNum, parallelism);
        Iterator<Map.Entry<Integer, List<Integer>>> iter = partitionAllocateMap.allocate().entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<Integer, List<Integer>> en = iter.next();
            int seq = en.getKey();
            List<Integer> partitions = en.getValue();
            TemplateInstance templateInstance = new TemplateInstance(partitions,seq);

            templateInstance.setName(getName());
            templateInstance.setPath(getPath());
            templateInstance.setMemXms(getMemXms());
            templateInstance.setMemXmx(getMemXmx());

            templateInstanceList.add(templateInstance);
        }

        return templateInstanceList;
    }


    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
    }

    public List<TemplateInstance> getTemplateInstanceList() {
        return templateInstanceList;
    }

    public void setTemplateInstanceList(List<TemplateInstance> templateInstanceList) {
        this.templateInstanceList = templateInstanceList;
    }
}
