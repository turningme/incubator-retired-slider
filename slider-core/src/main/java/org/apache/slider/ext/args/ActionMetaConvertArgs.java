package org.apache.slider.ext.args;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.apache.slider.common.params.AbstractActionArgs;

import static org.apache.slider.ext.ExtConstants.ACTION_META_CONVERT;
import static org.apache.slider.ext.ExtConstants.ARG_META_CONVERT_DEST;
import static org.apache.slider.ext.ExtConstants.ARG_META_CONVERT_SRC;


/**
 * Created by jpliu on 2020/9/23.
 */

@Parameters(commandNames = {ACTION_META_CONVERT}, commandDescription = "convert meta file, from one style into another  ")
public class ActionMetaConvertArgs extends AbstractActionArgs {

    @Parameter(names = {ARG_META_CONVERT_SRC}, description = "Source file path .", required = true)
    public String sourcePath;


    @Parameter(names = {ARG_META_CONVERT_DEST}, description = "Destination or target file path.")
    public String destPath;

    @Override
    public String getActionName() {
        return ACTION_META_CONVERT;
    }


    /**
     * Using cluster name here , indicating the flag , from xml2json , or json2xml( not implemented yet )
     * @return
     */
    public String getFlag(){
        return getClusterName();
    }


    public String getDestPath() {
        return destPath;
    }

    public String getSourcePath() {
        return sourcePath;
    }
}
