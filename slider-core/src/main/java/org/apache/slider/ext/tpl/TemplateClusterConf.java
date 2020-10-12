package org.apache.slider.ext.tpl;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by jpliu on 2020/10/12.
 */
public class TemplateClusterConf {
    /**
     * application name
     */
    String appName;
    /**
     * tar ball file path
     */
    String tarball;

    /**
     * sub templates
     */
    Map<String,TemplateConf> templates;

    Map<String,TemplateInstanceConf> templateInstances;

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getTarball() {
        return tarball;
    }

    public void setTarball(String tarball) {
        this.tarball = tarball;
    }

    public Map<String, TemplateConf> getTemplates() {
        return templates;
    }

    public void setTemplates(Map<String, TemplateConf> templates) {
        this.templates = templates;
    }

    public Map<String, TemplateInstanceConf> getTemplateInstances() {
        return templateInstances;
    }

    public void setTemplateInstances(Map<String, TemplateInstanceConf> templateInstances) {
        this.templateInstances = templateInstances;
    }


    public Map<String, String> getTemplateInfo(){
        Map<String, String> result = new HashMap<>();
        Iterator<Map.Entry<String, TemplateConf>> iter = templates.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, TemplateConf> en = iter.next();
            String tplName = en.getKey();
            TemplateConf templateConf = en.getValue();
            result.put(tplName,templateConf.getConf());
        }

        return result;
    }

    public Map<String, Integer> getTemplateByParallelism(){
        Map<String, Integer> result = new HashMap<>();
        Iterator<Map.Entry<String, TemplateConf>> iter = templates.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, TemplateConf> en = iter.next();
            String tplName = en.getKey();
            TemplateConf templateConf = en.getValue();
            result.put(tplName,templateConf.getParallel());
        }

        return result;
    }

    public Map<String, Integer> getTemplatePartitionNum(){
        Map<String, Integer> result = new HashMap<>();
        Iterator<Map.Entry<String, TemplateConf>> iter = templates.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, TemplateConf> en = iter.next();
            String tplName = en.getKey();
            TemplateConf templateConf = en.getValue();
            result.put(tplName,templateConf.getPartitions());
        }

        return result;
    }

    public Map<String, Map<String, String>> getTemplateMemInfo(){
        Map<String, Map<String,String>> result = new HashMap<>();
        Iterator<Map.Entry<String, TemplateConf>> iter = templates.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, TemplateConf> en = iter.next();
            String tplName = en.getKey();
            final TemplateConf templateConf = en.getValue();
            result.put(tplName,new HashMap<String, String>(){{put(templateConf.getMemMix() + "",templateConf.getMemMax()+ "");}});
        }

        return result;
    }

    public Map<String, Map<String, String>> getTemplateInstanceMemInfo(){
        Map<String, Map<String,String>> result = new HashMap<>();
        Iterator<Map.Entry<String, TemplateInstanceConf>> iter = templateInstances.entrySet().iterator();
        while (iter.hasNext()){
            Map.Entry<String, TemplateInstanceConf> en = iter.next();
            String tplName = en.getKey();
            final TemplateInstanceConf templateConf = en.getValue();
            result.put(tplName,new HashMap<String, String>(){{put(templateConf.getMemMix() + "",templateConf.getMemMax()+ "");}});
        }

        return result;
    }
}
