package org.apache.slider.ext;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.slider.ext.utils.ConvertUtil;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;
/**
 * Created by jpliu on 2020/9/23.
 * The representation for all the templates and its instances .
 */

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class TemplateTopology {

    public Map<String, Template> templateMap;
    public Map<String, TemplateInstance> templateInstanceMap;

    private String name;

    public TemplateTopology(String appName) {
        name = "tpl-topo-" + appName;
        init();
    }

    void init() {
        templateMap = new HashMap<>();
        templateInstanceMap = new HashMap<>();
    }


    public void addTemplate(Map<String, String> templateKeyInfo) {
        Iterator<Map.Entry<String, String>> iter = templateKeyInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, String> en = iter.next();
            String name = en.getKey();
            String path = en.getValue();

            Template template = new Template(name, path, -1, -1);

            Template old = templateMap.get(name);
            if (old == null) {
                templateMap.put(name, template);
            } else {
                old.merge(template);
            }
        }
    }

    /**
     * Add new template , mainly the name and parallelism .
     * Replace previous one if duplicated .
     *
     * @param templateKeyInfo
     */
    public void addTemplateByParallelism(Map<String, Integer> templateKeyInfo) {
        Iterator<Map.Entry<String, Integer>> iter = templateKeyInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Integer> en = iter.next();
            String name = en.getKey();
            Integer parallelism = en.getValue();

            Template template = new Template(name, "", parallelism, -1);
            Template old = templateMap.get(name);
            if (old == null) {
                templateMap.put(name, template);
            } else {
                old.merge(template);
            }
        }
    }


    public void addTemplateByPartitionNum(Map<String, Integer> templateKeyInfo) {
        Iterator<Map.Entry<String, Integer>> iter = templateKeyInfo.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Integer> en = iter.next();
            String name = en.getKey();
            Integer partitionNum = en.getValue();

            Template template = new Template(name, "", -1, partitionNum);

            Template old = templateMap.get(name);
            if (old == null) {
                templateMap.put(name, template);
            } else {
                old.merge(template);
            }
        }
    }


    public void addTemplateMemInfo(Map<String, Map<String, String>> memMap) {
        Iterator<Map.Entry<String, Map<Integer, Integer>>> iter = ConvertUtil.convertMem(memMap).entrySet().iterator();

        while (iter.hasNext()){
            Map.Entry<String, Map<Integer, Integer>> en = iter.next();
            String name = en.getKey();

            assert templateMap.containsKey(name);

            Map<Integer, Integer> memTuple = en.getValue();
            assert memTuple.size() == 1;

            Map.Entry<Integer ,Integer> memEntry = memTuple.entrySet().iterator().next();
            int memXms = memEntry.getKey();
            int memXmx = memEntry.getValue();

            templateMap.get(name).setMemXms(memXmx);
            templateMap.get(name).setMemXmx(memXms);
        }
    }


    public void addTemplateInstanceMemInfo(Map<String, Map<String, String>> memMap) {
        Iterator<Map.Entry<String, Map<Integer, Integer>>> iter = ConvertUtil.convertMem(memMap).entrySet().iterator();

        while (iter.hasNext()){
            Map.Entry<String, Map<Integer, Integer>> en = iter.next();
            String name = en.getKey();

            assert templateInstanceMap.containsKey(name);

            Map<Integer, Integer> memTuple = en.getValue();
            assert memTuple.size() == 1;

            Map.Entry<Integer ,Integer> memEntry = memTuple.entrySet().iterator().next();
            int memXms = memEntry.getKey();
            int memXmx = memEntry.getValue();

            templateInstanceMap.get(name).setMemXms(memXmx);
            templateInstanceMap.get(name).setMemXmx(memXms);
        }
    }


    public void resolveTemplateInstances() {
        Iterator<Map.Entry<String, Template>> iter = templateMap.entrySet().iterator();

        while (iter.hasNext()) {
            Map.Entry<String, Template> en = iter.next();
            Template val = en.getValue();
            List<TemplateInstance> templateInstances = val.resolveInstances();
            for (TemplateInstance templateInstance : templateInstances) {
                String instanceName = templateInstance.getName();
                templateInstanceMap.put(instanceName, templateInstance);
            }

        }

    }


    public String getName() {
        return name;
    }
}
