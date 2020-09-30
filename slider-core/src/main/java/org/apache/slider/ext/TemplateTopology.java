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
    public String tarballPath;
    public Map<String, Template> templateMap;
    public Map<String, TemplateInstance> templateInstanceMap;

    static final String PREFIX = "template_";
    private String name;

    public TemplateTopology(String appName) {
        name = PREFIX + appName;
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


    /**
     * validate all the fields ,  throw exception if any field wrong .
     * make sure all the memory fields not negative .
     * make sure config paths exist .
     * make sure the correctness of template job  parallelism and partitions .
     *
     */
    public void validateComplete(){

    }


    /**
     * complete all the phases and make this available
     */
    public void complete(){
        validateComplete();
    }



    public String getName() {
        return name;
    }


    public void traversal(InstanceVisitor visitor){
        Iterator<Map.Entry<String,Template>> iter = templateMap.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String,Template> en = iter.next();
            Template template = en.getValue();
            Iterator<TemplateInstance> instanceIter = template.getTemplateInstanceList().iterator();
            while(instanceIter.hasNext()) {
                TemplateInstance templateInstance = instanceIter.next();
                visitor.visit(templateInstance);
            }
        }
    }

    public Map<String, TemplateInstance> getTemplateInstanceMap() {
        return templateInstanceMap;
    }

    public Map<String, Template> getTemplateMap() {
        return templateMap;
    }

    public String getTarballPath() {
        return tarballPath;
    }

    public void setTarballPath(String tarballPath) {
        this.tarballPath = tarballPath;
    }

    public void updateTemplateConfigPath(String basePath){
        Iterator<Map.Entry<String,Template>> iter = templateMap.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Template> en = iter.next();
            Template template = en.getValue();
            String tplName = template.getName();

            Iterator<TemplateInstance> iterInner = template.getTemplateInstanceList().iterator();
            while (iterInner.hasNext()){
                TemplateInstance templateInstance = iterInner.next();
                //like xx/yy-number
                String shortName = tplName +"/" + tplName + "-"+templateInstance.getSeq() + ".conf";

                templateInstance.setPath(String.format("%s/%s", basePath , shortName));
            }
        }

    }


    /**
     * The Visitor pattern
     */
    public interface InstanceVisitor{
        void visit(TemplateInstance templateInstance);
    }
}
