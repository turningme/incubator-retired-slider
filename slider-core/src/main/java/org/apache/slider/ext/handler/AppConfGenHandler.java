package org.apache.slider.ext.handler;

import org.apache.slider.api.RoleKeys;
import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.AppConfBean;

import static org.apache.slider.ext.ExtConstants.COMPONENT_SLIDER_DEFAULT;
import static org.apache.slider.ext.ExtConstants.COMPONENT_SLIDER_JVM_HEAP_DEFAULT;

/**
 * Created by jpliu on 2020/9/25.
 */
public class AppConfGenHandler implements Handler<AppConfBean>{
    AppConfBean appConfBean;


    @Override
    public void handle(TemplateTopology templateTopology) {
        appConfBean = new AppConfBean();

        //default settings
        appConfBean.addComponents(COMPONENT_SLIDER_DEFAULT, RoleKeys.JVM_HEAP , COMPONENT_SLIDER_JVM_HEAP_DEFAULT);

        //template cluster specific settings
        
    }

    @Override
    public AppConfBean getHandle() {
        return appConfBean;
    }


}
