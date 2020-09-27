package org.apache.slider.ext.handler.bean;

import java.util.Iterator;

import org.apache.slider.providers.agent.application.metadata.Application;
import org.apache.slider.providers.agent.application.metadata.CommandScript;
import org.apache.slider.providers.agent.application.metadata.Component;
import org.apache.slider.providers.agent.application.metadata.ComponentExport;
import org.apache.slider.providers.agent.application.metadata.ConfigFile;
import org.apache.slider.providers.agent.application.metadata.Export;
import org.apache.slider.providers.agent.application.metadata.ExportGroup;
import org.apache.slider.providers.agent.application.metadata.Metainfo;
import org.apache.slider.providers.agent.application.metadata.OSPackage;
import org.apache.slider.providers.agent.application.metadata.OSSpecific;

import static org.apache.slider.ext.ExtConstants.META_APP_COMMENT_DEFAULT;
import static org.apache.slider.ext.ExtConstants.META_APP_VERSION_DEFAULT;
import static org.apache.slider.ext.ExtConstants.META_SCHEMA_VERSION_DEFAULT;

/**
 * Created by jpliu on 2020/9/25.
 */
public class MetaInfoBean {
    Metainfo metainfo;
    Application application;
    String name;

    public MetaInfoBean(String name) {
        this.name = name;
        init();
    }

    private void init() {
        metainfo = new Metainfo();
        application = new Application();
        metainfo.setApplication(application);

        metainfo.setSchemaVersion(META_SCHEMA_VERSION_DEFAULT);
        application.setComment(META_APP_COMMENT_DEFAULT);
        application.setVersion(META_APP_VERSION_DEFAULT);
        application.setName(this.name);
    }

    public void setApplicationName(String appName){
        application.setName(appName);
    }

    public void addExportGroup(String groupName, String exportName, String val) {
        Iterator<ExportGroup> iter = application.getExportGroups().iterator();
        ExportGroup target = null;
        while (iter.hasNext()) {
            ExportGroup eg = iter.next();
            if (groupName.equals(eg.getName())) {
                target = eg;
            }
        }

        if (target == null) {
            target = new ExportGroup();
            target.setName(groupName);
        }


        Export export = new Export();
        export.setName(exportName);
        export.setValue(val);

        target.addExport(export);

        application.addExportGroup(target);
    }


    public void addComponentAbstract(String name, String category, String appExports) {
        Component component = new Component();
        component.setName(name);
        component.setCategory(category);
        component.setAppExports(appExports);
        component.setMinInstanceCount(String.valueOf(0));
        component.setMaxInstanceCount(String.valueOf(1));

        application.addComponent(component);
    }


    public void addComponentExports(String componentName, String exportName, String exportVal) {
        Component targetComponent = lookupComponent(componentName);

        ComponentExport componentExport = new ComponentExport();
        componentExport.setName(exportName);
        componentExport.setValue(exportVal);

        targetComponent.addComponentExport(componentExport);
    }

    private Component lookupComponent(String componentName) {
        Iterator<Component> iter = application.getComponents().iterator();
        Component targetComponent = null;
        while (iter.hasNext()) {
            Component c = iter.next();
            if (componentName.equals(c.getName())) {
                targetComponent = c;
                break;
            }
        }

        if (targetComponent == null) {
            targetComponent = new Component();
            // this should not happen
            targetComponent.setName(componentName);
        }

        return targetComponent;
    }


    public void addComponentScript(String componentName, String script, String scriptType) {
        Component targetComponent = lookupComponent(componentName);
        CommandScript commandScript = new CommandScript();
        commandScript.setScript(script);
        commandScript.setScriptType(scriptType);

        targetComponent.addCommandScript(commandScript);
    }


    public void addOsSpecific(String osType, String packageType, String packageName) {
        Iterator<OSSpecific> osPackages = application.getOSSpecifics().iterator();
        OSSpecific target = null;
        while (osPackages.hasNext()) {
            OSSpecific osSpecific = osPackages.next();
            if (osType.equals(osSpecific.getOsType())) {
                target = osSpecific;
                break;
            }

        }

        if (target == null) {
            target = new OSSpecific();
            target.setOsType(osType);
        }

        OSPackage osPackage = new OSPackage();
        osPackage.setName(packageName);
        osPackage.setType(packageType);

        target.addOSPackage(osPackage);
    }


    public void addConfigFiles(String xype, String fileName, String dictionaryName) {
        ConfigFile configFile = new ConfigFile();
        configFile.setType(xype);
        configFile.setFileName(fileName);
        configFile.setDictionaryName(dictionaryName);

        application.addConfigFile(configFile);
    }


    public Metainfo getMetainfo() {
        return metainfo;
    }

    public void setMetainfo(Metainfo metainfo) {
        this.metainfo = metainfo;
    }
}
