package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.slider.common.params.AbstractArgsDelegate;
import org.apache.slider.common.params.DontSplitArguments;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;

import static org.apache.slider.ext.ExtConstants.ARG_TEMPLATE_NAME;

/**
 * Created by jpliu on 2020/9/23.
 */
public class TemplateArgsDelegate extends AbstractArgsDelegate{


    @Parameter(names = {ARG_TEMPLATE_NAME},
            arity = 2,
            description = "--template <name> <path> ",
            splitter = DontSplitArguments.class)
    public List<String> tplTuples = new ArrayList<>(0);


    public Map<String, String> getTemplateMap() throws BadCommandArgumentsException {
        return convertTupleListToMap("template", tplTuples);
    }


    public List<String> getTemplateTuples() {
        return tplTuples;
    }
}
