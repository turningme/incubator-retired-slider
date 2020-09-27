package org.apache.slider.ext.handler;

import org.apache.slider.ext.TemplateTopology;
import org.apache.slider.ext.handler.bean.PythonScriptBean;

/**
 * Created by jpliu on 2020/9/27.
 */
public class PythonScriptGenHandler implements Handler<PythonScriptBean>{
    PythonScriptBean pythonScriptBean;

    @Override
    public PythonScriptBean getHandle() {
        return pythonScriptBean;
    }

    @Override
    public void handle(TemplateTopology templateTopology) {
        pythonScriptBean = new PythonScriptBean();
    }
}
