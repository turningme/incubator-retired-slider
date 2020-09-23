package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParametersDelegate;

import java.util.Map;

import org.apache.slider.common.params.AbstractActionArgs;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;

import static org.apache.slider.ext.ExtConstants.ARG_APPLICATION_NAME;

/**
 * Created by jpliu on 2020/9/23.
 */
public abstract class TemplateClusterBuildingActions extends AbstractActionArgs{
    @Parameter(names = {ARG_APPLICATION_NAME}, description = "Application name , identifier of one application .")
    public String applicationName;

    @ParametersDelegate
    public TemplateArgsDelegate templatesDelegate = new TemplateArgsDelegate();


    @ParametersDelegate
    public TemplateParallelArgsDelegate templatesParallelDelegate = new TemplateParallelArgsDelegate();

    @ParametersDelegate
    public TemplateMemoryArgsDelegate templatesMemDelegate = new TemplateMemoryArgsDelegate();

    @ParametersDelegate
    public TemplateInstanceMemoryArgsDelegate templateInstanceMemoryDelegate = new TemplateInstanceMemoryArgsDelegate();

    @ParametersDelegate
    TemplatePartitionArgsDelegate templatePartitionArgsDelegate = new TemplatePartitionArgsDelegate();


    public String getApplicationName() {
        return applicationName;
    }


    public Map<String, String> getTemplateMap() throws BadCommandArgumentsException {
        return templatesDelegate.getTemplateMap();
    }

    public Map<String, String> getTemplatePrallelMap() throws BadCommandArgumentsException {
        return templatesParallelDelegate.getTemplateMap();
    }


    public Map<String, Map<String,String>> getTemplateMemMap() throws BadCommandArgumentsException {
        return templatesMemDelegate.getTemplateMap();
    }

    public Map<String, Map<String,String>>  getTemplateInstanceMemMap() throws BadCommandArgumentsException {
        return templateInstanceMemoryDelegate.getTemplateMap();
    }

    public Map<String, String> getTemplatePartitionMap() throws BadCommandArgumentsException {
        return templatePartitionArgsDelegate.getTemplateMap();
    }

}
