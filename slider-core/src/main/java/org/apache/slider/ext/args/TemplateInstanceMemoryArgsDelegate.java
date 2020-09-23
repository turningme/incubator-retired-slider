package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.slider.common.params.AbstractArgsDelegate;
import org.apache.slider.common.params.DontSplitArguments;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;

import static org.apache.slider.ext.ExtConstants.ARG_TEMPLATE_INSTANCE_MEMORY_NAME;

/**
 * Created by jpliu on 2020/9/23.
 */
public class TemplateInstanceMemoryArgsDelegate extends AbstractArgsDelegate {
    @Parameter(names = {ARG_TEMPLATE_INSTANCE_MEMORY_NAME},
            arity = 3,
            description = "--template.instance.mem <name> <xms> <xmx>  , measure in MB . ",
            splitter = DontSplitArguments.class)
    public List<String> tplTuples = new ArrayList<>(0);


    public Map<String, Map<String,String>>  getTemplateMap() throws BadCommandArgumentsException {
        return convertTripleListToMaps("template.mem", tplTuples);
    }


    public List<String> getTemplateTuples() {
        return tplTuples;
    }
}
