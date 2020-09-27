package org.apache.slider.ext.handler;

import org.apache.slider.ext.TemplateInstance;
import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.ResourceBean;

import static org.apache.slider.api.ResourceKeys.COMPONENT_INSTANCES;
import static org.apache.slider.api.ResourceKeys.DEF_YARN_MEMORY;
import static org.apache.slider.api.ResourceKeys.YARN_CORES;
import static org.apache.slider.api.ResourceKeys.YARN_MEMORY;
import static org.apache.slider.ext.ExtConstants.COMPONENT_SLIDER_DEFAULT;

/**
 * Created by jpliu on 2020/9/25.
 */
public class ResourceGenHandler implements Handler<ResourceBean>, TemplateTopology.InstanceVisitor {
    ResourceBean resourceBean;

    public ResourceGenHandler() {
    }

    @Override
    public void handle(TemplateTopology templateTopology) {
        resourceBean = new ResourceBean();
        //default settings
        resourceBean.addComponents(COMPONENT_SLIDER_DEFAULT, YARN_MEMORY, String.valueOf(DEF_YARN_MEMORY));

        //custom settings
        templateTopology.traversal(this);


    }

    @Override
    public ResourceBean getHandle() {
        return resourceBean;
    }

    @Override
    public void visit(TemplateInstance templateInstance) {

        String componentName = templateInstance.getName();

        resourceBean.addComponents(componentName, YARN_MEMORY, String.valueOf(templateInstance.getMemXmx()));
        resourceBean.addComponents(componentName, YARN_CORES, String.valueOf(1));
        resourceBean.addComponents(componentName, COMPONENT_INSTANCES, String.valueOf(1));
    }
}
