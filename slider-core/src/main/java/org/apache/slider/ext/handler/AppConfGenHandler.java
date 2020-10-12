package org.apache.slider.ext.handler;

import org.apache.slider.api.RoleKeys;
import org.apache.slider.ext.TemplateInstance;
import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.AppConfBean;
import org.apache.slider.ext.utils.ConvertUtil;

import static org.apache.slider.api.ResourceKeys.COMPONENT_INSTANCES;
import static org.apache.slider.api.ResourceKeys.YARN_CORES;
import static org.apache.slider.api.ResourceKeys.YARN_MEMORY;
import static org.apache.slider.ext.ExtConstants.COMPONENT_SLIDER_DEFAULT;
import static org.apache.slider.ext.ExtConstants.COMPONENT_SLIDER_JVM_HEAP_DEFAULT;
import static org.apache.slider.ext.ExtConstants.TEMPLATE_CONFIGURATION_CONF_KEY;
import static org.apache.slider.ext.ExtConstants.TEMPLATE_CONFIGURATION_MEMXMX_KEY;
import static org.apache.slider.ext.utils.ConvertUtil.getMemStringWithMeasure;
import static org.apache.slider.providers.agent.AgentKeys.APP_RESOURCES;

/**
 * Created by jpliu on 2020/9/25.
 */
public class AppConfGenHandler implements Handler<AppConfBean> , TemplateTopology.InstanceVisitor{
    AppConfBean appConfBean;


    @Override
    public void handle(TemplateTopology templateTopology) {
        appConfBean = new AppConfBean();

        //default settings
        appConfBean.addComponents(COMPONENT_SLIDER_DEFAULT, RoleKeys.JVM_HEAP , COMPONENT_SLIDER_JVM_HEAP_DEFAULT);
        //JVM_OPTS

        //template cluster specific settings
        templateTopology.traversal(this);
    }

    @Override
    public AppConfBean getHandle() {
        return appConfBean;
    }

    @Override
    public void visit(TemplateInstance templateInstance) {
        String componentName = templateInstance.getName();

        String confPath = templateInstance.getPath();
        String memXmx = getMemStringWithMeasure(templateInstance.getMemXmx(), "M");
        //overwrite the  option application.resources
        appConfBean.addComponents(componentName, APP_RESOURCES,confPath);
        appConfBean.addComponents(componentName , ConvertUtil.getConfigurationPrefix(TEMPLATE_CONFIGURATION_CONF_KEY), ConvertUtil.getShortNameFromPath(confPath));
        appConfBean.addComponents(componentName , ConvertUtil.getConfigurationPrefix(TEMPLATE_CONFIGURATION_MEMXMX_KEY), memXmx);
    }
}
