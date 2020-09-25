package org.apache.slider.ext;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

import org.apache.hadoop.conf.Configuration;
import org.apache.slider.common.params.AbstractActionArgs;
import org.apache.slider.common.params.AbstractArgsDelegate;
import org.apache.slider.common.params.ArgOps;
import org.apache.slider.common.tools.SliderUtils;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.exceptions.ErrorStrings;
import org.apache.slider.core.exceptions.SliderException;
import org.apache.slider.ext.args.ActionBuildArgs;
import org.apache.slider.ext.args.ActionMetaConvertArgs;
import org.apache.slider.ext.args.ActionStartArgs;

import static org.apache.slider.common.params.SliderActions.ACTION_HELP;
import static org.apache.slider.ext.ExtConstants.ACTION_BUILD;
import static org.apache.slider.ext.ExtConstants.ACTION_META_CONVERT;
import static org.apache.slider.ext.ExtConstants.ACTION_START;

/**
 * Parse SE Cluster input arguments into StartupInformation representation .
 * @author jpliu
 * @date 2020/9/22
 */
public class CmdlineParser extends AbstractArgsDelegate {
    public final JCommander commander;
    private final String[] args;



    @Parameter(names = ARG_HELP, help = true)
    public boolean help;

    private AbstractActionArgs coreAction;



    private final ActionStartArgs actionStart = new ActionStartArgs();
    private final ActionBuildArgs actionBuild = new ActionBuildArgs();
    private final ActionMetaConvertArgs actionMetaConvert = new ActionMetaConvertArgs();

    /**
     *
     * @param args
     */
    public CmdlineParser(String[] args) {
        this.args = args;
        commander = new JCommander(this);
    }

    /**
     * Override point to add a set of actions
     */
    protected void addActionArguments() {
        commander.addCommand(actionStart);
        commander.addCommand(actionBuild);
        commander.addCommand(actionMetaConvert);
    }

    /**
     * Parse routine -includes registering the action-specific argument classes
     * and postprocess it
     * @throws SliderException on any problem
     */
    public void parse() throws SliderException {
        addActionArguments();
        try {
            commander.parse(getArgs());
        } catch (ParameterException e) {
            throw new BadCommandArgumentsException(e, "%s in %s",
                                                   e.toString(),
                                                   (getArgs() != null
                                                           ? (SliderUtils.join(getArgs(),
                                                                               " ", false))
                                                           : "[]"));
        }
        //now copy back to this class some of the attributes that are common to all
        //actions
        postProcess();
    }


    /**
     * validate args
     * then post process the arguments
     */
    public void postProcess() throws SliderException {
        applyAction();
        validate();
    }

    public void validate(){
        // TODO: 2020/9/23
    }

    protected void bindCoreAction(AbstractActionArgs action) {
        coreAction = action;
    }

    public String[] getArgs() {
        return args;
    }

    public String getAction() {
        return commander.getParsedCommand();
    }

    public void applyAction() throws SliderException {
        String action = getAction();
        if (SliderUtils.isUnset(action)) {
            action = ACTION_HELP;
        }


        switch (action) {
            case ACTION_BUILD:
                bindCoreAction(actionBuild);
                break;
            case ACTION_START:
                bindCoreAction(actionStart);
                break;
            case ACTION_META_CONVERT:
                bindCoreAction(actionMetaConvert);
                break;
            default:
                throw new BadCommandArgumentsException(ErrorStrings.ERROR_UNKNOWN_ACTION
                                                               + " " + action);
        }

    }

    public AbstractActionArgs getCoreAction() {
        return coreAction;
    }

    public void applyFileSystemBinding(Configuration conf) {
        ArgOps.applyFileSystemBinding(getFilesystemBinding(), conf);
    }

    public String getFilesystemBinding() {
        return coreAction.filesystemBinding;
    }


    public ActionBuildArgs getActionBuild() {
        return actionBuild;
    }

    public ActionStartArgs getActionStart() {
        return actionStart;
    }

    public ActionMetaConvertArgs getActionMetaConvert() {
        return actionMetaConvert;
    }

    public static void main(String[] args) throws SliderException {
        String[] t1 = new String[]{"start", "x1" , "--app.name" ,"app1", "--template" ,"name1", "v1", "--template", "n2" ,"v2"};
        CmdlineParser parser = new CmdlineParser(t1);
        parser.parse();
        System.out.println("finish");
    }


}
