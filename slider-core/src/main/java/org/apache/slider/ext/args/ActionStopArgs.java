package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.ParametersDelegate;

import org.apache.slider.common.params.AbstractActionArgs;

import static org.apache.slider.ext.ExtConstants.ACTION_START;
import static org.apache.slider.ext.ExtConstants.ACTION_STOP;
import static org.apache.slider.ext.ExtConstants.ARG_APPLICATION_NAME;

/**
 * Created by jpliu on 2020/9/23.
 */

@Parameters(commandNames = {ACTION_STOP}, commandDescription = "start the overall templates topology ")
public class ActionStopArgs extends AbstractActionArgs{
    @Parameter(names = {ARG_APPLICATION_NAME}, description = "Application name , identifier of one application .")
    public String applicationName;

    @ParametersDelegate
    public TemplateArgsDelegate templatesDelegate = new TemplateArgsDelegate();



    @Override
    public String getActionName() {
        return ACTION_START;
    }

    public String getApplicationName() {
        return applicationName;
    }
}
