package org.apache.slider.ext;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.persist.ConfTreeSerDeser;
import org.apache.slider.ext.handler.AppConfGenHandler;
import org.apache.slider.ext.handler.MetaInfoGenHandler;
import org.apache.slider.ext.handler.ResourceGenHandler;
import org.apache.slider.ext.handler.bean.AppConfBean;
import org.apache.slider.ext.handler.bean.MetaInfoBean;
import org.apache.slider.ext.handler.bean.ResourceBean;
import org.apache.slider.ext.persist.AppDefinitionPaths;
import org.apache.slider.ext.redstats.RedstatsConfigManager;

import static org.apache.slider.ext.ExtConstants.TEMPLATE_CLUSTER_LAYOUT_BASE_DIR;

/**
 * Created by jpliu on 2020/9/25.
 */
public class AppDefinitionLayout {

    private AppDefinitionPaths appDefinitionPaths;
    private MetaInfoBean metaInfoBean;
    private AppConfBean appConfBean;
    private ResourceBean resourceBean;
    private LocalFileSystem localFileSystem;

    public AppDefinitionLayout(LocalFileSystem localFileSystem) {
        this.localFileSystem = localFileSystem;
        init();
    }

    void init(){

    }

    public void resolveFromTemplateTopology(TemplateTopology templateTopology){
        appDefinitionPaths = new AppDefinitionPaths(new Path(new File(TEMPLATE_CLUSTER_LAYOUT_BASE_DIR + "/" + templateTopology.getName()).toURI()));

        //generate metainfo
        MetaInfoGenHandler metaInfoGenHandler = new MetaInfoGenHandler();
        metaInfoGenHandler.handle(templateTopology);
        metaInfoBean = metaInfoGenHandler.getHandle();

        //genrate appconf
        AppConfGenHandler appConfGenHandler = new AppConfGenHandler();
        appConfGenHandler.handle(templateTopology);
        appConfBean = appConfGenHandler.getHandle();

        //generate resource
        ResourceGenHandler resourceGenHandler = new ResourceGenHandler();
        resourceGenHandler.handle(templateTopology);
        resourceBean = resourceGenHandler.getHandle();


    }


    /**
     * create the real file and paths for the specified  template cluster application definition .
     */
    public void materialize() throws IOException, BadCommandArgumentsException {
        //detect if root folder exists , by now the root folder should be removed as soon as program exits .
        if (localFileSystem.exists(appDefinitionPaths.basePath)){
            throw new BadCommandArgumentsException("Application local definition file exists . path = " + appDefinitionPaths.basePath);
        }

        //init root path
        localFileSystem. mkdirs(appDefinitionPaths.basePath);

        //init all subpaths
        localFileSystem.mkdirs(appDefinitionPaths.filesPath);
        localFileSystem.mkdirs(appDefinitionPaths.scriptsPath);
        localFileSystem.mkdirs(appDefinitionPaths.templatesPath);

        //persist metainfo.json
        MetaInfoProcessor metaInfoProcessor = new MetaInfoProcessor();
        metaInfoProcessor.persistMetaInfo(metaInfoBean.getMetainfo(),localFileSystem.create(appDefinitionPaths.metainfoPath,true));

        ConfTreeSerDeser confTreeSerDeser = new ConfTreeSerDeser();
        //persist resources.json
        confTreeSerDeser.save(localFileSystem, appDefinitionPaths.resourcesPath,resourceBean.getConfTree() , true);

        //persist appconf.json
        confTreeSerDeser.save(localFileSystem, appDefinitionPaths.appConfPath,appConfBean.getConfTree() , true);
    }


    /**
     * Generate the distributed configuration instances from the template config .
     * @param templateTopology
     * @param sliderFileSystem
     */
    public void generateConfiguration(TemplateTopology templateTopology, SliderFileSystem sliderFileSystem) throws IOException, ConfigurationException {
        String  confBasePath = "/user/root/.template-cluster";
        sliderFileSystem.getFileSystem().mkdirs(new Path(confBasePath));
        templateTopology.updateTemplateConfigPath(confBasePath);

        RedstatsConfigManager configManager = AppConfigManagerFactory.createAppConfigManager();
        configManager.process(sliderFileSystem,templateTopology);
        configManager.compelete(sliderFileSystem);
    }



}
