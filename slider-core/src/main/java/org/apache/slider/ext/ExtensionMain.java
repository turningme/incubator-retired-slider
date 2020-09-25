package org.apache.slider.ext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathNotFoundException;
import org.apache.hadoop.registry.client.binding.RegistryUtils;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.slider.client.SliderYarnClientImpl;
import org.apache.slider.common.params.AbstractActionArgs;
import org.apache.slider.common.tools.ConfigHelper;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.exceptions.NotFoundException;
import org.apache.slider.core.exceptions.SliderException;
import org.apache.slider.core.main.RunService;
import org.apache.slider.core.main.ServiceLauncher;
import org.apache.slider.core.registry.YarnAppListClient;
import org.apache.slider.ext.args.ActionBuildArgs;
import org.apache.slider.ext.args.ActionMetaConvertArgs;
import org.apache.slider.ext.persist.TemplateTopologySerDeser;
import org.apache.slider.ext.utils.ConvertUtil;
import org.apache.slider.server.services.utility.AbstractSliderLaunchedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.slider.common.SliderKeys.SLIDER_CLIENT_XML;
import static org.apache.slider.common.tools.SliderUtils.forceLogin;
import static org.apache.slider.common.tools.SliderUtils.initProcessSecurity;
import static org.apache.slider.common.tools.SliderUtils.isHadoopClusterSecure;
import static org.apache.slider.common.tools.SliderUtils.loadSliderClientXML;
import static org.apache.slider.common.tools.SliderUtils.patchConfiguration;
import static org.apache.slider.common.tools.SliderUtils.validateSliderClientEnvironment;
import static org.apache.slider.core.main.LauncherExitCodes.EXIT_SUCCESS;
import static org.apache.slider.core.main.LauncherExitCodes.EXIT_UNIMPLEMENTED;
import static org.apache.slider.core.main.LauncherExitCodes.EXIT_USAGE;
import static org.apache.slider.ext.ExtConstants.ACTION_BUILD;
import static org.apache.slider.ext.ExtConstants.ACTION_META_CONVERT;
import static org.apache.slider.ext.ExtConstants.ACTION_START;
import static org.apache.slider.ext.ExtConstants.ACTION_STOP;

/**
 * Created by jpliu on 2020/9/22.
 */
public class ExtensionMain extends AbstractSliderLaunchedService implements RunService {
    static final Logger LOG = LoggerFactory.getLogger(ExtensionMain.class);

    /**
     * The parser for command line input .
     */
    private CmdlineParser parser;
    /**
     * Yarn client service
     */
    private SliderYarnClientImpl yarnClient;
    private YarnAppListClient yarnAppListClient;
    protected SliderFileSystem sliderFileSystem;


    public ExtensionMain() {
        super("Template-Cluster");
    }

    public ExtensionMain(String name) {
        super(name);
    }

    /**
     *
     * @param args
     * @return
     */
    private int actionBuild(ActionBuildArgs args) throws BadCommandArgumentsException, IOException {
        String name = args.getApplicationName();
        TemplateTopology templateTopology = new TemplateTopology(name);

        templateTopology.addTemplate(args.getTemplateMap());
        templateTopology.addTemplateByParallelism(ConvertUtil.convert(args.getTemplatePrallelMap()));
        templateTopology.addTemplateByPartitionNum(ConvertUtil.convert(args.getTemplatePartitionMap()));
        templateTopology.addTemplateMemInfo(args.getTemplateMemMap());

        templateTopology.resolveTemplateInstances();

        templateTopology.addTemplateInstanceMemInfo(args.getTemplateInstanceMemMap());

        //parse to json
        LOG.info(TemplateTopologySerDeser.toString(templateTopology));
        //persist it on hdfs


        //generate metainfo

        //genrate appconf

        //generate resource


        //file directory structure planing


        //file generate and layout them

        //call build , and start from slider client


        return EXIT_SUCCESS;
    }


    /**
     *
     * @param args
     * @return
     * @throws BadCommandArgumentsException
     * @throws IOException
     */
    private int actionMetaConvert(ActionMetaConvertArgs args) throws BadCommandArgumentsException, IOException{
        String flag = args.getFlag();
        System.out.println("flag = " + flag);

        String sourcePath = args.getSourcePath();
        String targetPath = args.getDestPath();

        // determine if print the converted result to console
        boolean consolePrint = StringUtils.isBlank(targetPath) ? true :false;

        MetaInfoProcessor metaInfoProcessor = new MetaInfoProcessor();
        File sourceFile = new File(sourcePath);
        LocalFileSystem localFileSystem = FileSystem.getLocal(new Configuration());
        OutputStream outputStream = consolePrint ? new PrintStream(System.out): localFileSystem.create(new Path(new File(targetPath).toURI()));
        metaInfoProcessor.convertXml2JsonFile(localFileSystem.open(new Path(sourceFile.toURI())), outputStream);
        return EXIT_SUCCESS;
    }

    public void launch(String[] args) throws Throwable {
        parser = new CmdlineParser(args);
        parser.parse();

        int exitCode = EXIT_SUCCESS;
        String action = parser.getAction();
        if (isUnset(action)) {
            throw new SliderException(EXIT_USAGE, "usage ");
        }


        switch (action) {
            case ACTION_BUILD:
                actionBuild(parser.getActionBuild());
                break;

            case ACTION_START:
                break;

            case ACTION_STOP:
                break;

            default:
                throw new SliderException(EXIT_UNIMPLEMENTED,
                                          "Unimplemented: " + action);
        }
    }

    /**
     * Launched service execution. This runs {@link #exec()}
     * then catches some exceptions and converts them to exit codes
     *
     * @return an exit code
     * @throws Throwable
     */
    @Override
    public int runService() throws Throwable {
        try {
            return exec();
        } catch (FileNotFoundException | PathNotFoundException nfe) {
            throw new NotFoundException(nfe, nfe.toString());
        }
    }


    /**
     * Execute the command line
     *
     * @return an exit code
     * @throws Throwable on a failure
     */
    public int exec() throws Throwable {

        int exitCode = EXIT_SUCCESS;
        String action = parser.getAction();
        if (isUnset(action)) {
            throw new SliderException(EXIT_USAGE, "usage ");
        }

        switch (action) {
            case ACTION_BUILD:
                exitCode = actionBuild(parser.getActionBuild());
                break;

            case ACTION_START:
                break;

            case ACTION_STOP:
                break;

            case ACTION_META_CONVERT:
                actionMetaConvert(parser.getActionMetaConvert());
                break;

            default:
                throw new SliderException(EXIT_UNIMPLEMENTED, "Unimplemented: " + action);
        }

        return exitCode;
    }

    @Override
    public Configuration bindArgs(Configuration config, String... args) throws Exception {
        config = super.bindArgs(config, args);
        parser = new CmdlineParser(args);
        parser.parse();
        // add the slider XML config
        ConfigHelper.injectSliderXMLResource();
        // yarn-ify
        YarnConfiguration yarnConfiguration = new YarnConfiguration(config);
        return patchConfiguration(yarnConfiguration);
    }


    @Override
    protected void serviceInit(Configuration conf) throws Exception {
        Configuration clientConf = loadSliderClientXML();
        ConfigHelper.mergeConfigurations(conf, clientConf, SLIDER_CLIENT_XML, true);
//        parser.applyDefinitions(conf);
        parser.applyFileSystemBinding(conf);
        AbstractActionArgs coreAction = parser.getCoreAction();
        // init security with our conf
        if (!coreAction.disableSecureLogin() && isHadoopClusterSecure(conf)) {
            forceLogin();
            initProcessSecurity(conf);
        }
        if (coreAction.getHadoopServicesRequired()) {
            initHadoopBinding();
        }
        super.serviceInit(conf);
    }


    protected void initHadoopBinding() throws IOException, SliderException {
        // validate the client
        validateSliderClientEnvironment(null);
        //create the YARN client
        yarnClient = new SliderYarnClientImpl();
     /*    yarnClient.init(getConfig());
       if (getServiceState() == STATE.STARTED) {
            yarnClient.start();
        }
        addService(yarnClient);*/
        yarnAppListClient =
                new YarnAppListClient(yarnClient, getUsername(), getConfig());
        // create the filesystem
        sliderFileSystem = new SliderFileSystem(getConfig());
    }

    public String getUsername() throws IOException {
        return RegistryUtils.currentUser();
    }

    public static final String SERVICE_CLASSNAME = "org.apache.slider.ext.ExtensionMain";

    public static void main(String[] args) {
        // String[] t1 = new String[]{"build", "x1" , "--app.name" ,"app1", "--template" ,"name1", "v1", "--template", "n2" ,"v2","--template.parallel","name1","1","--template.parallel","n2","1","--template.partition","name1","2","--template.partition","n2","2","--template.mem","name1","100","100","--template.mem","n2","200","200","--template.instance.mem","name1-0","1000","1000","--template.instance.mem","n2-0","2000","2000"};
       /* ExtensionMain main = new ExtensionMain("");
        try {
            main.launch(args);
        } catch (Throwable throwable) {
            LOG.error("{}",throwable);
        }*/


        //turn the args to a list
        List<String> argsList = Arrays.asList(args);
        //create a new list, as the ArrayList type doesn't push() on an insert
        List<String> extendedArgs = new ArrayList<String>(argsList);
        //insert the service name
        extendedArgs.add(0, SERVICE_CLASSNAME);
        //now have the service launcher do its work
        ServiceLauncher.serviceMain(extendedArgs);
    }
}
