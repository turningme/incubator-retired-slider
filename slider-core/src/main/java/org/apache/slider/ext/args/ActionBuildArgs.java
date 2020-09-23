package org.apache.slider.ext.args;

import com.beust.jcommander.Parameters;

import static org.apache.slider.ext.ExtConstants.ACTION_BUILD;


/**
 * Created by jpliu on 2020/9/23.
 */

@Parameters(commandNames = {ACTION_BUILD}, commandDescription = "build the overall templates topology ")
public class ActionBuildArgs extends TemplateClusterBuildingActions{
    @Override
    public String getActionName() {
        return ACTION_BUILD;
    }
}
