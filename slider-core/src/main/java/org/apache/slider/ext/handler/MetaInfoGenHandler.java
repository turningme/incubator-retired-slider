package org.apache.slider.ext.handler;

import org.apache.slider.ext.TemplateInstance;
import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.MetaInfoBean;
import org.apache.slider.providers.agent.application.metadata.AbstractComponent;

import static org.apache.slider.providers.agent.application.metadata.AbstractComponent.CATEGORY_SLAVE;

/**
 * Created by jpliu on 2020/9/25.
 */
public class MetaInfoGenHandler implements Handler<MetaInfoBean> , TemplateTopology.InstanceVisitor{
    MetaInfoBean metaInfoBean;

    @Override
    public void handle(TemplateTopology templateTopology) {
        // template topology name , assigned to app name of  metainfo.xml
        metaInfoBean = new MetaInfoBean(templateTopology.getName());

        //custom setting
        metaInfoBean.setApplicationName(templateTopology.getName());

        templateTopology.traversal(this);
    }

    @Override
    public MetaInfoBean getHandle() {
        return metaInfoBean;
    }

    @Override
    public void visit(TemplateInstance templateInstance) {
        String componentName = templateInstance.getName();
        metaInfoBean.addComponentAbstract(componentName, CATEGORY_SLAVE ,"");
        metaInfoBean.addComponentScript(componentName, "scripts/xx.py", AbstractComponent.TYPE_PYTHON);

        metaInfoBean.addOsSpecific("any","tarball", "files/tomcat-8.0.30.tar.gz");
    }
}
