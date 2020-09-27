package org.apache.slider.ext.handler;

import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.PythonParamBean;

/**
 *
 * @author jpliu
 * @date 2020/9/27
 */
public class PythonParamGenHandler implements Handler<PythonParamBean>{
    PythonParamBean pythonParamBean;

    @Override
    public PythonParamBean getHandle() {
        return pythonParamBean;
    }

    @Override
    public void handle(TemplateTopology templateTopology) {
        pythonParamBean = new PythonParamBean();
    }
}
