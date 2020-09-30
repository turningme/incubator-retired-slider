/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.slider.providers.agent;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.registry.client.binding.RegistryPathUtils;
import org.apache.hadoop.registry.client.types.Endpoint;
import org.apache.hadoop.registry.client.types.ProtocolTypes;
import org.apache.hadoop.registry.client.types.ServiceRecord;
import org.apache.hadoop.registry.client.types.yarn.PersistencePolicies;
import org.apache.hadoop.registry.client.types.yarn.YarnRegistryAttributes;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.Container;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.slider.api.ClusterDescription;
import org.apache.slider.api.ClusterNode;
import org.apache.slider.api.InternalKeys;
import org.apache.slider.api.OptionKeys;
import org.apache.slider.api.ResourceKeys;
import org.apache.slider.api.StatusKeys;
import org.apache.slider.common.SliderExitCodes;
import org.apache.slider.common.SliderKeys;
import org.apache.slider.common.SliderXmlConfKeys;
import org.apache.slider.common.tools.SliderFileSystem;
import org.apache.slider.common.tools.SliderUtils;
import org.apache.slider.core.conf.AggregateConf;
import org.apache.slider.core.conf.ConfTreeOperations;
import org.apache.slider.core.conf.MapOperations;
import org.apache.slider.core.exceptions.BadCommandArgumentsException;
import org.apache.slider.core.exceptions.BadConfigException;
import org.apache.slider.core.exceptions.NoSuchNodeException;
import org.apache.slider.core.exceptions.SliderException;
import org.apache.slider.core.launch.CommandLineBuilder;
import org.apache.slider.core.launch.ContainerLauncher;
import org.apache.slider.core.registry.docstore.ExportEntry;
import org.apache.slider.core.registry.docstore.PublishedConfiguration;
import org.apache.slider.core.registry.docstore.PublishedExports;
import org.apache.slider.core.registry.info.CustomRegistryConstants;
import org.apache.slider.providers.AbstractProviderService;
import org.apache.slider.providers.MonitorDetail;
import org.apache.slider.providers.ProviderCore;
import org.apache.slider.providers.ProviderRole;
import org.apache.slider.providers.ProviderUtils;
import org.apache.slider.providers.agent.application.metadata.AbstractComponent;
import org.apache.slider.providers.agent.application.metadata.Application;
import org.apache.slider.providers.agent.application.metadata.CommandScript;
import org.apache.slider.providers.agent.application.metadata.Component;
import org.apache.slider.providers.agent.application.metadata.ComponentCommand;
import org.apache.slider.providers.agent.application.metadata.ComponentExport;
import org.apache.slider.providers.agent.application.metadata.ComponentsInAddonPackage;
import org.apache.slider.providers.agent.application.metadata.ConfigFile;
import org.apache.slider.providers.agent.application.metadata.DefaultConfig;
import org.apache.slider.providers.agent.application.metadata.DockerContainer;
import org.apache.slider.providers.agent.application.metadata.Export;
import org.apache.slider.providers.agent.application.metadata.ExportGroup;
import org.apache.slider.providers.agent.application.metadata.Metainfo;
import org.apache.slider.providers.agent.application.metadata.OSPackage;
import org.apache.slider.providers.agent.application.metadata.OSSpecific;
import org.apache.slider.providers.agent.application.metadata.Package;
import org.apache.slider.providers.agent.application.metadata.PropertyInfo;
import org.apache.slider.server.appmaster.actions.ProviderReportedContainerLoss;
import org.apache.slider.server.appmaster.actions.RegisterComponentInstance;
import org.apache.slider.server.appmaster.state.ContainerPriority;
import org.apache.slider.server.appmaster.state.RoleInstance;
import org.apache.slider.server.appmaster.state.StateAccessForProviders;
import org.apache.slider.server.appmaster.web.rest.agent.AgentCommandType;
import org.apache.slider.server.appmaster.web.rest.agent.AgentRestOperations;
import org.apache.slider.server.appmaster.web.rest.agent.CommandReport;
import org.apache.slider.server.appmaster.web.rest.agent.ComponentStatus;
import org.apache.slider.server.appmaster.web.rest.agent.ExecutionCommand;
import org.apache.slider.server.appmaster.web.rest.agent.HeartBeat;
import org.apache.slider.server.appmaster.web.rest.agent.HeartBeatResponse;
import org.apache.slider.server.appmaster.web.rest.agent.Register;
import org.apache.slider.server.appmaster.web.rest.agent.RegistrationResponse;
import org.apache.slider.server.appmaster.web.rest.agent.RegistrationStatus;
import org.apache.slider.server.appmaster.web.rest.agent.StatusCommand;
import org.apache.slider.server.services.security.CertificateManager;
import org.apache.slider.server.services.security.SecurityStore;
import org.apache.slider.server.services.security.StoresGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.apache.slider.server.appmaster.web.rest.RestPaths.SLIDER_PATH_AGENTS;

/**
 * This class implements the server-side logic for application deployment through Slider application package
 */
public class AgentProviderService extends AbstractProviderService implements
    ProviderCore,
    AgentKeys,
    SliderKeys, AgentRestOperations {


  protected static final Logger log =
      LoggerFactory.getLogger(AgentProviderService.class);
  private static final ProviderUtils providerUtils = new ProviderUtils(log);
  private static final String LABEL_MAKER = "___";
  private static final String CONTAINER_ID = "container_id";
  private static final String GLOBAL_CONFIG_TAG = "global";
  private static final String LOG_FOLDERS_TAG = "LogFolders";
  private static final String HOST_FOLDER_FORMAT = "%s:%s";
  private static final String CONTAINER_LOGS_TAG = "container_log_dirs";
  private static final String CONTAINER_PWDS_TAG = "container_work_dirs";
  private static final String COMPONENT_TAG = "component";
  private static final String APPLICATION_TAG = "application";
  private static final String COMPONENT_DATA_TAG = "ComponentInstanceData";
  private static final String SHARED_PORT_TAG = "SHARED";
  private static final String PER_CONTAINER_TAG = "{PER_CONTAINER}";
  private static final int MAX_LOG_ENTRIES = 40;
  private static final int DEFAULT_HEARTBEAT_MONITOR_INTERVAL = 60 * 1000;

  private final Object syncLock = new Object();
  private final ComponentTagProvider tags = new ComponentTagProvider();
  private int heartbeatMonitorInterval = 0;
  private AgentClientProvider clientProvider;
  private AtomicInteger taskId = new AtomicInteger(0);
  private volatile Metainfo metaInfo = null;
  private Map<String, DefaultConfig> defaultConfigs = null;
  private ComponentCommandOrder commandOrder = null;
  private HeartbeatMonitor monitor;
  private Boolean canAnyMasterPublish = null;
  private AgentLaunchParameter agentLaunchParameter = null;
  private String clusterName = null;
  private boolean isInUpgradeMode;
  private Set<String> upgradeContainers = new HashSet<String>();
  private boolean appStopInitiated;

  private final Map<String, ComponentInstanceState> componentStatuses =
      new ConcurrentHashMap<String, ComponentInstanceState>();
  private final Map<String, Map<String, String>> componentInstanceData =
      new ConcurrentHashMap<String, Map<String, String>>();
  private final Map<String, Map<String, List<ExportEntry>>> exportGroups =
      new ConcurrentHashMap<String, Map<String, List<ExportEntry>>>();
  private final Map<String, Map<String, String>> allocatedPorts =
      new ConcurrentHashMap<String, Map<String, String>>();
  private final Map<String, Metainfo> packageMetainfo = 
      new ConcurrentHashMap<String, Metainfo>();

  private final Map<String, ExportEntry> logFolderExports =
      Collections.synchronizedMap(new LinkedHashMap<String, ExportEntry>(MAX_LOG_ENTRIES, 0.75f, false) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
          return size() > MAX_LOG_ENTRIES;
        }
      });
  private final Map<String, ExportEntry> workFolderExports =
      Collections.synchronizedMap(new LinkedHashMap<String, ExportEntry>(MAX_LOG_ENTRIES, 0.75f, false) {
        protected boolean removeEldestEntry(Map.Entry eldest) {
          return size() > MAX_LOG_ENTRIES;
        }
      });
  private final Map<String, Set<String>> containerExportsMap =
      new HashMap<String, Set<String>>();

  /**
   * Create an instance of AgentProviderService
   */
  public AgentProviderService() {
    super("AgentProviderService");
    setAgentRestOperations(this);
    setHeartbeatMonitorInterval(DEFAULT_HEARTBEAT_MONITOR_INTERVAL);
  }

  @Override
  public String getHumanName() {
    return "Slider Agent";
  }

  @Override
  public List<ProviderRole> getRoles() {
    return AgentRoles.getRoles();
  }

  @Override
  protected void serviceInit(Configuration conf) throws Exception {
    super.serviceInit(conf);
    clientProvider = new AgentClientProvider(conf);
  }

  @Override
  public Configuration loadProviderConfigurationInformation(File confDir) throws
      BadCommandArgumentsException,
      IOException {
    return new Configuration(false);
  }

  @Override
  public void validateInstanceDefinition(AggregateConf instanceDefinition)
      throws
      SliderException {
    clientProvider.validateInstanceDefinition(instanceDefinition, null);

    ConfTreeOperations resources =
        instanceDefinition.getResourceOperations();

    Set<String> names = resources.getComponentNames();
    names.remove(SliderKeys.COMPONENT_AM);
    for (String name : names) {
      Component componentDef = getMetaInfo().getApplicationComponent(name);
      if (componentDef == null) {
        throw new BadConfigException(
            "Component %s is not a member of application.", name);
      }

      MapOperations componentConfig = resources.getMandatoryComponent(name);
      int count =
          componentConfig.getMandatoryOptionInt(ResourceKeys.COMPONENT_INSTANCES);
      int definedMinCount = componentDef.getMinInstanceCountInt();
      int definedMaxCount = componentDef.getMaxInstanceCountInt();
      if (count < definedMinCount || count > definedMaxCount) {
        throw new BadConfigException("Component %s, %s value %d out of range. "
                                     + "Expected minimum is %d and maximum is %d",
                                     name,
                                     ResourceKeys.COMPONENT_INSTANCES,
                                     count,
                                     definedMinCount,
                                     definedMaxCount);
      }
    }
  }

  // Reads the metainfo.xml in the application package and loads it
  private void buildMetainfo(AggregateConf instanceDefinition,
                             SliderFileSystem fileSystem) throws IOException, SliderException {
    String appDef = SliderUtils.getApplicationDefinitionPath(instanceDefinition
        .getAppConfOperations());

    if (metaInfo == null) {
      synchronized (syncLock) {
        if (metaInfo == null) {
          readAndSetHeartbeatMonitoringInterval(instanceDefinition);
          initializeAgentDebugCommands(instanceDefinition);

          metaInfo = getApplicationMetainfo(fileSystem, appDef, false);
          log.info("Master package metainfo: {}", metaInfo.toString());
          if (metaInfo == null || metaInfo.getApplication() == null) {
            log.error("metainfo.xml is unavailable or malformed at {}.", appDef);
            throw new SliderException(
                "metainfo.xml is required in app package.");
          }
          commandOrder = new ComponentCommandOrder(metaInfo.getApplication().getCommandOrders());
          defaultConfigs = initializeDefaultConfigs(fileSystem, appDef, metaInfo);
          monitor = new HeartbeatMonitor(this, getHeartbeatMonitorInterval());
          monitor.start();

          // build a map from component to metainfo
          String addonAppDefString = instanceDefinition.getAppConfOperations()
              .getGlobalOptions().getOption(AgentKeys.ADDONS, null);
          log.debug("All addon appdefs: {}", addonAppDefString);
          if (addonAppDefString != null) {
            Scanner scanner = new Scanner(addonAppDefString).useDelimiter(",");
            while (scanner.hasNext()) {
              String addonAppDef = scanner.next();
              String addonAppDefPath = instanceDefinition
                  .getAppConfOperations().getGlobalOptions().get(addonAppDef);
              log.debug("Addon package {} is stored at: {}", addonAppDef
                  + addonAppDefPath);
              Metainfo addonMetaInfo = getApplicationMetainfo(fileSystem,
                  addonAppDefPath, true);
              addonMetaInfo.validate();
              packageMetainfo.put(addonMetaInfo.getApplicationPackage()
                  .getName(), addonMetaInfo);
            }
            log.info("Metainfo map for master and addon: {}",
                packageMetainfo.toString());
          }
        }
      }
    }
  }

  @Override
  public void initializeApplicationConfiguration(
      AggregateConf instanceDefinition, SliderFileSystem fileSystem)
      throws IOException, SliderException {
    buildMetainfo(instanceDefinition, fileSystem);
  }

  @Override
  public void buildContainerLaunchContext(ContainerLauncher launcher,
                                          AggregateConf instanceDefinition,
                                          Container container,
                                          ProviderRole providerRole,
                                          SliderFileSystem fileSystem,
                                          Path generatedConfPath,
                                          MapOperations resourceComponent,
                                          MapOperations appComponent,
                                          Path containerTmpDirPath) throws
      IOException,
      SliderException {
    
    String roleName = providerRole.name;
    String roleGroup = providerRole.group;
    String appDef = SliderUtils.getApplicationDefinitionPath(instanceDefinition
        .getAppConfOperations());

    initializeApplicationConfiguration(instanceDefinition, fileSystem);

    log.info("Build launch context for Agent");
    log.debug(instanceDefinition.toString());
    
    //if we are launching docker based app on yarn, then we need to pass docker image
    if (isYarnDockerContainer(roleGroup)) {
      launcher.setYarnDockerMode(true);
      launcher.setDockerImage(getConfigFromMetaInfo(roleGroup, "image"));
      launcher.setRunPrivilegedContainer(getConfigFromMetaInfo(roleGroup, "runPriviledgedContainer"));
      launcher
          .setYarnContainerMountPoints(getConfigFromMetaInfoWithAppConfigOverriding(
              roleGroup, "yarn.container.mount.points"));
    }

    // Set the environment
    launcher.putEnv(SliderUtils.buildEnvMap(appComponent,
        getStandardTokenMap(getAmState().getAppConfSnapshot(), roleName, roleGroup)));

    String workDir = ApplicationConstants.Environment.PWD.$();
    launcher.setEnv("AGENT_WORK_ROOT", workDir);
    log.info("AGENT_WORK_ROOT set to {}", workDir);
    String logDir = ApplicationConstants.LOG_DIR_EXPANSION_VAR;
    launcher.setEnv("AGENT_LOG_ROOT", logDir);
    log.info("AGENT_LOG_ROOT set to {}", logDir);
    if (System.getenv(HADOOP_USER_NAME) != null) {
      launcher.setEnv(HADOOP_USER_NAME, System.getenv(HADOOP_USER_NAME));
    }
    // for 2-Way SSL
    launcher.setEnv(SLIDER_PASSPHRASE, instanceDefinition.getPassphrase());
    //add english env
    launcher.setEnv("LANG", "en_US.UTF-8");
    launcher.setEnv("LC_ALL", "en_US.UTF-8");
    launcher.setEnv("LANGUAGE", "en_US.UTF-8");

    //local resources

    // TODO: Should agent need to support App Home
    String scriptPath = new File(AgentKeys.AGENT_MAIN_SCRIPT_ROOT, AgentKeys.AGENT_MAIN_SCRIPT).getPath();
    String appHome = instanceDefinition.getAppConfOperations().
        getGlobalOptions().get(AgentKeys.PACKAGE_PATH);
    if (SliderUtils.isSet(appHome)) {
      scriptPath = new File(appHome, AgentKeys.AGENT_MAIN_SCRIPT).getPath();
    }

    // set PYTHONPATH
    List<String> pythonPaths = new ArrayList<String>();
    pythonPaths.add(AgentKeys.AGENT_MAIN_SCRIPT_ROOT);
    pythonPaths.add(AgentKeys.AGENT_JINJA2_ROOT);
    String pythonPath = StringUtils.join(File.pathSeparator, pythonPaths);
    launcher.setEnv(PYTHONPATH, pythonPath);
    log.info("PYTHONPATH set to {}", pythonPath);

    Path agentImagePath = null;
    String agentImage = instanceDefinition.getInternalOperations().
        get(InternalKeys.INTERNAL_APPLICATION_IMAGE_PATH);
    if (SliderUtils.isUnset(agentImage)) {
      agentImagePath =
          new Path(new Path(new Path(instanceDefinition.getInternalOperations().get(InternalKeys.INTERNAL_TMP_DIR),
                                     container.getId().getApplicationAttemptId().getApplicationId().toString()),
                            AgentKeys.PROVIDER_AGENT),
                   SliderKeys.AGENT_TAR);
    } else {
       agentImagePath = new Path(agentImage);
    }

    if (fileSystem.getFileSystem().exists(agentImagePath)) {
      LocalResource agentImageRes = fileSystem.createAmResource(agentImagePath, LocalResourceType.ARCHIVE);
      launcher.addLocalResource(AgentKeys.AGENT_INSTALL_DIR, agentImageRes);
    } else {
      String msg =
          String.format("Required agent image slider-agent.tar.gz is unavailable at %s", agentImagePath.toString());
      MapOperations compOps = appComponent;
      boolean relaxVerificationForTest = compOps != null ? Boolean.valueOf(compOps.
          getOptionBool(AgentKeys.TEST_RELAX_VERIFICATION, false)) : false;
      log.error(msg);

      if (!relaxVerificationForTest) {
        throw new SliderException(SliderExitCodes.EXIT_DEPLOYMENT_FAILED, msg);
      }
    }

    log.info("Using {} for agent.", scriptPath);
    LocalResource appDefRes = fileSystem.createAmResource(
        fileSystem.getFileSystem().resolvePath(new Path(appDef)),
        LocalResourceType.ARCHIVE);
    launcher.addLocalResource(AgentKeys.APP_DEFINITION_DIR, appDefRes);

    String agentConf = instanceDefinition.getAppConfOperations().
        getGlobalOptions().getOption(AgentKeys.AGENT_CONF, "");
    if (SliderUtils.isSet(agentConf)) {
      LocalResource agentConfRes = fileSystem.createAmResource(fileSystem
                                                                   .getFileSystem().resolvePath(new Path(agentConf)),
                                                               LocalResourceType.FILE);
      launcher.addLocalResource(AgentKeys.AGENT_CONFIG_FILE, agentConfRes);
    }

    String agentVer = instanceDefinition.getAppConfOperations().
        getGlobalOptions().getOption(AgentKeys.AGENT_VERSION, null);
    if (agentVer != null) {
      LocalResource agentVerRes = fileSystem.createAmResource(
          fileSystem.getFileSystem().resolvePath(new Path(agentVer)),
          LocalResourceType.FILE);
      launcher.addLocalResource(AgentKeys.AGENT_VERSION_FILE, agentVerRes);
    }

    if (SliderUtils.isHadoopClusterSecure(getConfig())) {
      localizeServiceKeytabs(launcher, instanceDefinition, fileSystem);
    }

    MapOperations amComponent = instanceDefinition.
        getAppConfOperations().getComponent(SliderKeys.COMPONENT_AM);
    boolean twoWayEnabled = amComponent != null ? Boolean.valueOf(amComponent.
        getOptionBool(AgentKeys.KEY_AGENT_TWO_WAY_SSL_ENABLED, false)) : false;
    if (twoWayEnabled) {
      localizeContainerSSLResources(launcher, container, fileSystem);
    }

    MapOperations compOps = appComponent;
    if (areStoresRequested(compOps)) {
      localizeContainerSecurityStores(launcher, container, roleName, fileSystem,
                                      instanceDefinition, compOps);
    }

    //add the configuration resources
    launcher.addLocalResources(fileSystem.submitDirectory(
        generatedConfPath,
        SliderKeys.PROPAGATED_CONF_DIR_NAME));

    String label = getContainerLabel(container, roleName, roleGroup);
    CommandLineBuilder operation = new CommandLineBuilder();

    String pythonExec = instanceDefinition.getAppConfOperations()
        .getGlobalOptions().getOption(SliderXmlConfKeys.PYTHON_EXECUTABLE_PATH,
                                      AgentKeys.PYTHON_EXE);

    operation.add(pythonExec);

    operation.add(scriptPath);
    operation.add(ARG_LABEL, label);
    operation.add(ARG_ZOOKEEPER_QUORUM);
    operation.add(getClusterOptionPropertyValue(OptionKeys.ZOOKEEPER_QUORUM));
    operation.add(ARG_ZOOKEEPER_REGISTRY_PATH);
    operation.add(getZkRegistryPath());

    String debugCmd = agentLaunchParameter.getNextLaunchParameter(roleGroup);
    if (SliderUtils.isSet(debugCmd)) {
      operation.add(ARG_DEBUG);
      operation.add(debugCmd);
    }

    operation.add("> " + ApplicationConstants.LOG_DIR_EXPANSION_VAR + "/"
        + AgentKeys.AGENT_OUT_FILE + " 2>&1");

    launcher.addCommand(operation.build());

    // localize addon package
    String addonAppDefString = instanceDefinition.getAppConfOperations()
        .getGlobalOptions().getOption(AgentKeys.ADDONS, null);
    log.debug("All addon appdefs: {}", addonAppDefString);
    if (addonAppDefString != null) {
      Scanner scanner = new Scanner(addonAppDefString).useDelimiter(",");
      while (scanner.hasNext()) {
        String addonAppDef = scanner.next();
        String addonAppDefPath = instanceDefinition
            .getAppConfOperations().getGlobalOptions().get(addonAppDef);
        log.debug("Addon package {} is stored at: {}", addonAppDef, addonAppDefPath);
        LocalResource addonPkgRes = fileSystem.createAmResource(
            fileSystem.getFileSystem().resolvePath(new Path(addonAppDefPath)),
            LocalResourceType.ARCHIVE);
        launcher.addLocalResource(AgentKeys.ADDON_DEFINITION_DIR + "/" + addonAppDef, addonPkgRes);
      }
      log.debug("Metainfo map for master and addon: {}",
          packageMetainfo.toString());
    }    

    // Additional files to localize in addition to the application def
    String appResourcesString = instanceDefinition.getAppConfOperations()
        .getGlobalOptions().getOption(AgentKeys.APP_RESOURCES, null);
    log.info("Configuration value for extra resources to localize: {}", appResourcesString);
    if (null != appResourcesString) {
      try (Scanner scanner = new Scanner(appResourcesString).useDelimiter(",")) {
        while (scanner.hasNext()) {
          String resource = scanner.next();
          Path resourcePath = new Path(resource);
          LocalResource extraResource = fileSystem.createAmResource(
              fileSystem.getFileSystem().resolvePath(resourcePath),
              LocalResourceType.FILE);
          String destination = AgentKeys.APP_RESOURCES_DIR + "/" + resourcePath.getName();
          log.info("Localizing {} to {}", resourcePath, destination);
          // TODO Can we try harder to avoid collisions?
          launcher.addLocalResource(destination, extraResource);
        }
      }
    }

    //Additional files to localize for current role group, this method should be refactored later
    String appResourcesStringByRole = appComponent.getOption(AgentKeys.APP_RESOURCES, null);
    log.info("Configuration value for current role's ({}) resources to localize: {}",roleGroup, appResourcesStringByRole);
    if (null != appResourcesStringByRole) {
      try (Scanner scanner = new Scanner(appResourcesStringByRole).useDelimiter(",")) {
        while (scanner.hasNext()) {
          String resource = scanner.next();
          Path resourcePath = new Path(resource);
          LocalResource extraResource = fileSystem.createAmResource(
                  fileSystem.getFileSystem().resolvePath(resourcePath),
                  LocalResourceType.FILE);
          String destination = AgentKeys.APP_RESOURCES_DIR + "/" + resourcePath.getName();
          log.info("Localizing {} to {}", resourcePath, destination);
          // TODO Can we try harder to avoid collisions?
          launcher.addLocalResource(destination, extraResource);
        }
      }
    }


    // initialize addon pkg states for all componentInstanceStatus
    Map<String, State> pkgStatuses = new TreeMap<>();
    for (Metainfo appPkg : packageMetainfo.values()) {
      // check each component of that addon to see if they apply to this
      // component 'role'
      for (ComponentsInAddonPackage comp : appPkg.getApplicationPackage()
          .getComponents()) {
        log.debug("Current component: {} component in metainfo: {}", roleName,
            comp.getName());
        if (comp.getName().equals(roleGroup)
            || comp.getName().equals(AgentKeys.ADDON_FOR_ALL_COMPONENTS)) {
          pkgStatuses.put(appPkg.getApplicationPackage().getName(), State.INIT);
        }
      }
    }
    log.debug("For component: {} pkg status map: {}", roleName,
        pkgStatuses.toString());
    
    // initialize the component instance state
    getComponentStatuses().put(label,
                               new ComponentInstanceState(
                                   roleName,
                                   container.getId(),
                                   getClusterInfoPropertyValue(OptionKeys.APPLICATION_NAME),
                                   pkgStatuses));
  }

  private void localizeContainerSecurityStores(ContainerLauncher launcher,
                                               Container container,
                                               String role,
                                               SliderFileSystem fileSystem,
                                               AggregateConf instanceDefinition,
                                               MapOperations compOps)
      throws SliderException, IOException {
    // generate and localize security stores
    SecurityStore[] stores = generateSecurityStores(container, role,
                                                    instanceDefinition, compOps);
    for (SecurityStore store : stores) {
      LocalResource keystoreResource = fileSystem.createAmResource(
          uploadSecurityResource(store.getFile(), fileSystem), LocalResourceType.FILE);
      launcher.addLocalResource(String.format("secstores/%s-%s.p12",
                                              store.getType(), role),
                                keystoreResource);
    }
  }

  private SecurityStore[] generateSecurityStores(Container container,
                                                 String role,
                                                 AggregateConf instanceDefinition,
                                                 MapOperations compOps)
      throws SliderException, IOException {
    return StoresGenerator.generateSecurityStores(container.getNodeId().getHost(),
                                           container.getId().toString(), role,
                                           instanceDefinition, compOps);
  }

  private boolean areStoresRequested(MapOperations compOps) {
    return compOps != null ? compOps.
        getOptionBool(SliderKeys.COMP_STORES_REQUIRED_KEY, false) : false;
  }

  private void localizeContainerSSLResources(ContainerLauncher launcher,
                                             Container container,
                                             SliderFileSystem fileSystem)
      throws SliderException {
    try {
      // localize server cert
      Path certsDir = fileSystem.buildClusterSecurityDirPath(getClusterName());
      LocalResource certResource = fileSystem.createAmResource(
          new Path(certsDir, SliderKeys.CRT_FILE_NAME),
            LocalResourceType.FILE);
      launcher.addLocalResource(AgentKeys.CERT_FILE_LOCALIZATION_PATH,
                                certResource);

      // generate and localize agent cert
      CertificateManager certMgr = new CertificateManager();
      String hostname = container.getNodeId().getHost();
      String containerId = container.getId().toString();
      certMgr.generateContainerCertificate(hostname, containerId);
      LocalResource agentCertResource = fileSystem.createAmResource(
          uploadSecurityResource(
            CertificateManager.getAgentCertficateFilePath(containerId),
            fileSystem), LocalResourceType.FILE);
      // still using hostname as file name on the agent side, but the files
      // do end up under the specific container's file space
      launcher.addLocalResource(AgentKeys.INFRA_RUN_SECURITY_DIR + hostname +
                                ".crt", agentCertResource);
      LocalResource agentKeyResource = fileSystem.createAmResource(
          uploadSecurityResource(
              CertificateManager.getAgentKeyFilePath(containerId), fileSystem),
            LocalResourceType.FILE);
      launcher.addLocalResource(AgentKeys.INFRA_RUN_SECURITY_DIR + hostname +
                                ".key", agentKeyResource);

    } catch (Exception e) {
      throw new SliderException(SliderExitCodes.EXIT_DEPLOYMENT_FAILED, e,
          "Unable to localize certificates.  Two-way SSL cannot be enabled");
    }
  }

  private Path uploadSecurityResource(File resource, SliderFileSystem fileSystem)
      throws IOException {
    Path certsDir = fileSystem.buildClusterSecurityDirPath(getClusterName());
    if (!fileSystem.getFileSystem().exists(certsDir)) {
      fileSystem.getFileSystem().mkdirs(certsDir,
        new FsPermission(FsAction.ALL, FsAction.NONE, FsAction.NONE));
    }
    Path destPath = new Path(certsDir, resource.getName());
    if (!fileSystem.getFileSystem().exists(destPath)) {
      FSDataOutputStream os = fileSystem.getFileSystem().create(destPath);
      byte[] contents = FileUtils.readFileToByteArray(resource);
      os.write(contents, 0, contents.length);

      os.flush();
      os.close();
      log.info("Uploaded {} to localization path {}", resource, destPath);
    }

    while (!fileSystem.getFileSystem().exists(destPath)) {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // ignore
      }
    }

    fileSystem.getFileSystem().setPermission(destPath,
      new FsPermission(FsAction.READ, FsAction.NONE, FsAction.NONE));

    return destPath;
  }

  private void localizeServiceKeytabs(ContainerLauncher launcher,
                                      AggregateConf instanceDefinition,
                                      SliderFileSystem fileSystem)
      throws IOException {
    String keytabPathOnHost = instanceDefinition.getAppConfOperations()
        .getComponent(SliderKeys.COMPONENT_AM).get(
            SliderXmlConfKeys.KEY_AM_KEYTAB_LOCAL_PATH);
    if (SliderUtils.isUnset(keytabPathOnHost)) {
      String amKeytabName = instanceDefinition.getAppConfOperations()
          .getComponent(SliderKeys.COMPONENT_AM).get(
              SliderXmlConfKeys.KEY_AM_LOGIN_KEYTAB_NAME);
      String keytabDir = instanceDefinition.getAppConfOperations()
          .getComponent(SliderKeys.COMPONENT_AM).get(
              SliderXmlConfKeys.KEY_HDFS_KEYTAB_DIR);
      // we need to localize the keytab files in the directory
      Path keytabDirPath = fileSystem.buildKeytabPath(keytabDir, null,
                                                      getClusterName());
      boolean serviceKeytabsDeployed = false;
      if (fileSystem.getFileSystem().exists(keytabDirPath)) {
        FileStatus[] keytabs = fileSystem.getFileSystem().listStatus(keytabDirPath);
        LocalResource keytabRes;
        for (FileStatus keytab : keytabs) {
          if (!amKeytabName.equals(keytab.getPath().getName())
              && keytab.getPath().getName().endsWith(".keytab")) {
            serviceKeytabsDeployed = true;
            log.info("Localizing keytab {}", keytab.getPath().getName());
            keytabRes = fileSystem.createAmResource(keytab.getPath(),
              LocalResourceType.FILE);
            launcher.addLocalResource(SliderKeys.KEYTAB_DIR + "/" +
                                    keytab.getPath().getName(),
                                    keytabRes);
          }
        }
      }
      if (!serviceKeytabsDeployed) {
        log.warn("No service keytabs for the application have been localized.  "
                 + "If the application requires keytabs for secure operation, "
                 + "please ensure that the required keytabs have been uploaded "
                 + "to the folder {}", keytabDirPath);
      }
    }
  }

  /**
   * build the zookeeper registry path.
   * 
   * @return the path the service registered at
   * @throws NullPointerException if the service has not yet registered
   */
  private String getZkRegistryPath() {
    Preconditions.checkNotNull(yarnRegistry, "Yarn registry not bound");
    String path = yarnRegistry.getAbsoluteSelfRegistrationPath();
    Preconditions.checkNotNull(path, "Service record path not defined");
    return path;
  }

  @Override
  public void rebuildContainerDetails(List<Container> liveContainers,
                                      String applicationId, Map<Integer, ProviderRole> providerRoleMap) {
    for (Container container : liveContainers) {
      // get the role name and label
      ProviderRole role = providerRoleMap.get(ContainerPriority
                                                  .extractRole(container));
      if (role != null) {
        String roleName = role.name;
        String label = getContainerLabel(container, roleName, role.group);
        log.info("Rebuilding in-memory: container {} in role {} in cluster {}",
                 container.getId(), roleName, applicationId);
        getComponentStatuses().put(label,
            new ComponentInstanceState(roleName, container.getId(),
                                       applicationId));
      } else {
        log.warn("Role not found for container {} in cluster {}",
                 container.getId(), applicationId);
      }
    }
  }

  @Override
  public boolean isSupportedRole(String role) {
    return true;
  }

  /**
   * Handle registration calls from the agents
   *
   * @param registration registration entry
   *
   * @return response
   */
  @Override
  public RegistrationResponse handleRegistration(Register registration) {
    log.info("Handling registration: {}", registration);
    RegistrationResponse response = new RegistrationResponse();
    String label = registration.getLabel();
    String pkg = registration.getPkg();
    State agentState = registration.getActualState();
    String appVersion = registration.getAppVersion();

    log.info("label: {} pkg: {}", label, pkg);

    if (getComponentStatuses().containsKey(label)) {
      response.setResponseStatus(RegistrationStatus.OK);
      ComponentInstanceState componentStatus = getComponentStatuses().get(label);
      componentStatus.heartbeat(System.currentTimeMillis());
      updateComponentStatusWithAgentState(componentStatus, agentState);

      String roleName = getRoleName(label);
      String roleGroup = getRoleGroup(label);
      String containerId = getContainerId(label);

      if (SliderUtils.isSet(registration.getTags())) {
        tags.recordAssignedTag(roleName, containerId, registration.getTags());
      } else {
        response.setTags(tags.getTag(roleName, containerId));
      }

      String hostFqdn = registration.getPublicHostname();
      Map<String, String> ports = registration.getAllocatedPorts();
      if (ports != null && !ports.isEmpty()) {
        processAllocatedPorts(hostFqdn, roleName, roleGroup, containerId, ports);
      }

      Map<String, String> folders = registration.getLogFolders();
      if (folders != null && !folders.isEmpty()) {
        publishFolderPaths(folders, containerId, roleName, hostFqdn);
      }

      // Set app version if empty. It gets unset during upgrade - why?
      checkAndSetContainerAppVersion(containerId, appVersion);
    } else {
      response.setResponseStatus(RegistrationStatus.FAILED);
      response.setLog("Label not recognized.");
      log.warn("Received registration request from unknown label {}", label);
    }
    log.info("Registration response: {}", response);
    return response;
  }

  // Checks if app version is empty. Sets it to the version as reported by the
  // container during registration phase.
  private void checkAndSetContainerAppVersion(String containerId,
      String appVersion) {
    StateAccessForProviders amState = getAmState();
    try {
      RoleInstance role = amState.getOwnedContainer(containerId);
      if (role != null) {
        String currentAppVersion = role.appVersion;
        log.debug("Container = {}, app version current = {} new = {}",
            containerId, currentAppVersion, appVersion);
        if (currentAppVersion == null
            || currentAppVersion.equals(APP_VERSION_UNKNOWN)) {
          amState.getOwnedContainer(containerId).appVersion = appVersion;
        }
      }
    } catch (NoSuchNodeException e) {
      // ignore - there is nothing to do if we don't find a container
      log.warn("Owned container {} not found - {}", containerId, e);
    }
  }

  /**
   * Handle heartbeat response from agents
   *
   * @param heartBeat incoming heartbeat from Agent
   *
   * @return response to send back
   */
  @Override
  public HeartBeatResponse handleHeartBeat(HeartBeat heartBeat) {
    log.debug("Handling heartbeat: {}", heartBeat);
    HeartBeatResponse response = new HeartBeatResponse();
    long id = heartBeat.getResponseId();
    response.setResponseId(id + 1L);

    String label = heartBeat.getHostname();
    String pkg = heartBeat.getPackage();

    log.debug("package received: " + pkg);
    
    String roleName = getRoleName(label);
    String roleGroup = getRoleGroup(label);
    String containerId = getContainerId(label);
    boolean doUpgrade = false;
    if (isInUpgradeMode && upgradeContainers.contains(containerId)) {
      doUpgrade = true;
    }

    StateAccessForProviders accessor = getAmState();
    CommandScript cmdScript = getScriptPathForMasterPackage(roleGroup);
    List<ComponentCommand> commands = getMetaInfo().getApplicationComponent(roleGroup).getCommands();

    if (!isDockerContainer(roleGroup) && !isYarnDockerContainer(roleGroup)
        && (cmdScript == null || cmdScript.getScript() == null)
        && commands.size() == 0) {
      log.error(
          "role.script is unavailable for {}. Commands will not be sent.",
          roleName);
      return response;
    }

    String scriptPath = null;
    long timeout = 600L;
    if (cmdScript != null) {
      scriptPath = cmdScript.getScript();
      timeout = cmdScript.getTimeout();
    }

    if (timeout == 0L) {
      timeout = 600L;
    }

    if (!getComponentStatuses().containsKey(label)) {
      // container is completed but still heart-beating, send terminate signal
      log.info(
          "Sending terminate signal to completed container (still heartbeating): {}",
          label);
      response.setTerminateAgent(true);
      return response;
    }

    List<ComponentStatus> statuses = heartBeat.getComponentStatus();
    if (statuses != null && !statuses.isEmpty()) {
      log.info("status from agent: " + statuses.toString());
      try {
        for(ComponentStatus status : statuses){
          RoleInstance role = null;
          if(status.getIp() != null && !status.getIp().isEmpty()){
            role = amState.getOwnedContainer(containerId);
            role.ip = status.getIp();
          }
          if(status.getHostname() != null && !status.getHostname().isEmpty()){
            role = amState.getOwnedContainer(containerId);
            role.hostname = status.getHostname();
          }
          if (role != null) {
            // create an updated service record (including hostname and ip) and publish...
            ServiceRecord record = new ServiceRecord();
            record.set(YarnRegistryAttributes.YARN_ID, containerId);
            record.description = roleName;
            record.set(YarnRegistryAttributes.YARN_PERSISTENCE,
                       PersistencePolicies.CONTAINER);
            // TODO:  switch record attributes to use constants from YarnRegistryAttributes
            // when it's been updated.
            if (role.ip != null) {
              record.set("yarn:ip", role.ip);
            }
            if (role.hostname != null) {
              record.set("yarn:hostname", role.hostname);
            }
            yarnRegistry.putComponent(
                RegistryPathUtils.encodeYarnID(containerId), record);

          }
        }


      } catch (NoSuchNodeException e) {
        // ignore - there is nothing to do if we don't find a container
        log.warn("Owned container {} not found - {}", containerId, e);
      } catch (IOException e) {
        log.warn("Error updating container {} service record in registry",
                 containerId, e);
      }
    }

    Boolean isMaster = isMaster(roleGroup);
    ComponentInstanceState componentStatus = getComponentStatuses().get(label);
    componentStatus.heartbeat(System.currentTimeMillis());
    if (doUpgrade) {
      switch (componentStatus.getState()) {
      case STARTED:
        componentStatus.setTargetState(State.UPGRADED);
        break;
      case UPGRADED:
        componentStatus.setTargetState(State.STOPPED);
        break;
      case STOPPED:
        componentStatus.setTargetState(State.TERMINATING);
        break;
      default:
        break;
      }
      log.info("Current state = {} target state {}",
          componentStatus.getState(), componentStatus.getTargetState());
    }

    if (appStopInitiated && !componentStatus.isStopInitiated()) {
      log.info("Stop initiated for label {}", label);
      componentStatus.setTargetState(State.STOPPED);
      componentStatus.setStopInitiated(true);
    }

    publishConfigAndExportGroups(heartBeat, componentStatus, roleGroup);
    CommandResult result = null;
    List<CommandReport> reports = heartBeat.getReports();
    if (SliderUtils.isNotEmpty(reports)) {
      CommandReport report = reports.get(0);
      Map<String, String> ports = report.getAllocatedPorts();
      if (SliderUtils.isNotEmpty(ports)) {
        processAllocatedPorts(heartBeat.getFqdn(), roleName, roleGroup, containerId, ports);
      }
      result = CommandResult.getCommandResult(report.getStatus());
      Command command = Command.getCommand(report.getRoleCommand());
      componentStatus.applyCommandResult(result, command, pkg);
      log.info("Component operation. Status: {}; new container state: {};"
          + " new component state: {}", result,
          componentStatus.getContainerState(), componentStatus.getState());

      if (command == Command.INSTALL && SliderUtils.isNotEmpty(report.getFolders())) {
        publishFolderPaths(report.getFolders(), containerId, roleName, heartBeat.getFqdn());
      }
    }

    int waitForCount = accessor.getInstanceDefinitionSnapshot().
        getAppConfOperations().getComponentOptInt(roleGroup, AgentKeys.WAIT_HEARTBEAT, 0);

    if (id < waitForCount) {
      log.info("Waiting until heartbeat count {}. Current val: {}", waitForCount, id);
      getComponentStatuses().put(label, componentStatus);
      return response;
    }

    Command command = componentStatus.getNextCommand(doUpgrade);
    try {
      if (Command.NOP != command) {
        log.debug("For comp {} pkg {} issuing {}", roleName,
            componentStatus.getNextPkgToInstall(), command.toString());
        if (command == Command.INSTALL) {
          log.info("Installing {} on {}.", roleName, containerId);
          if (isDockerContainer(roleGroup) || isYarnDockerContainer(roleGroup)){
            addInstallDockerCommand(roleName, roleGroup, containerId,
                response, null, timeout);
          } else if (scriptPath != null) {
            addInstallCommand(roleName, roleGroup, containerId, response,
                scriptPath, null, timeout, null);
          } else {
            // commands
            ComponentCommand installCmd = null;
            for (ComponentCommand compCmd : commands) {
              if (compCmd.getName().equals("INSTALL")) {
                installCmd = compCmd;
              }
            }
            addInstallCommand(roleName, roleGroup, containerId, response, null,
                installCmd, timeout, null);
          }
          componentStatus.commandIssued(command);
        } else if (command == Command.INSTALL_ADDON) {
          String nextPkgToInstall = componentStatus.getNextPkgToInstall();
          // retrieve scriptPath or command of that package for the component
          for (ComponentsInAddonPackage comp : packageMetainfo
              .get(nextPkgToInstall).getApplicationPackage().getComponents()) {
            // given nextPkgToInstall and roleName is determined, the if below
            // should only execute once per heartbeat
            log.debug("Addon component: {} pkg: {} script: {}", comp.getName(),
                nextPkgToInstall, comp.getCommandScript().getScript());
            if (comp.getName().equals(roleGroup)
                || comp.getName().equals(AgentKeys.ADDON_FOR_ALL_COMPONENTS)) {
              scriptPath = comp.getCommandScript().getScript();
              if (scriptPath != null) {
                addInstallCommand(roleName, roleGroup, containerId, response,
                    scriptPath, null, timeout, nextPkgToInstall);
              } else {
                ComponentCommand installCmd = null;
                for (ComponentCommand compCmd : comp.getCommands()) {
                  if (compCmd.getName().equals("INSTALL")) {
                    installCmd = compCmd;
                  }
                }
                addInstallCommand(roleName, roleGroup, containerId, response,
                    null, installCmd, timeout, nextPkgToInstall);
              }
            }
          }
          componentStatus.commandIssued(command);
        } else if (command == Command.START) {
          // check against dependencies
          boolean canExecute = commandOrder.canExecute(roleGroup, command, getComponentStatuses().values());
          if (canExecute) {
            log.info("Starting {} on {}.", roleName, containerId);
            if (isDockerContainer(roleGroup) || isYarnDockerContainer(roleGroup)){
              addStartDockerCommand(roleName, roleGroup, containerId,
                  response, null, timeout, false);
            } else if (scriptPath != null) {
              addStartCommand(roleName,
                              roleGroup,
                              containerId,
                              response,
                              scriptPath,
                              null,
                              null,
                              timeout,
                              isMarkedAutoRestart(roleGroup));
            } else {
              ComponentCommand startCmd = null;
              for (ComponentCommand compCmd : commands) {
                if (compCmd.getName().equals("START")) {
                  startCmd = compCmd;
                }
              }
              ComponentCommand stopCmd = null;
              for (ComponentCommand compCmd : commands) {
                if (compCmd.getName().equals("STOP")) {
                  stopCmd = compCmd;
                }
              }
              addStartCommand(roleName, roleGroup, containerId, response, null,
                  startCmd, stopCmd, timeout, false);
            }
            componentStatus.commandIssued(command);
          } else {
            log.info("Start of {} on {} delayed as dependencies have not started.", roleName, containerId);
          }
        } else if (command == Command.UPGRADE) {
          addUpgradeCommand(roleName, roleGroup, containerId, response,
              scriptPath, timeout);
          componentStatus.commandIssued(command, true);
        } else if (command == Command.STOP) {
          log.info("Stop command being sent to container with id {}",
              containerId);
          addStopCommand(roleName, roleGroup, containerId, response, scriptPath,
              timeout, doUpgrade);
          componentStatus.commandIssued(command);
        } else if (command == Command.TERMINATE) {
          log.info("A formal terminate command is being sent to container {}"
              + " in state {}", label, componentStatus.getState());
          response.setTerminateAgent(true);
        }
      }

      // if there is no outstanding command then retrieve config
      if (isMaster && componentStatus.getState() == State.STARTED
          && command == Command.NOP) {
        if (!componentStatus.getConfigReported()) {
          log.info("Requesting applied config for {} on {}.", roleName, containerId);
          if (isDockerContainer(roleGroup) || isYarnDockerContainer(roleGroup)){
            addGetConfigDockerCommand(roleName, roleGroup, containerId, response);
          } else {
            addGetConfigCommand(roleName, roleGroup, containerId, response);
          }
        }
      }
      
      // if restart is required then signal
      response.setRestartEnabled(false);
      if (componentStatus.getState() == State.STARTED
          && command == Command.NOP && isMarkedAutoRestart(roleGroup)) {
        response.setRestartEnabled(true);
      }

      //If INSTALL_FAILED and no INSTALL is scheduled let the agent fail
      if (componentStatus.getState() == State.INSTALL_FAILED
         && command == Command.NOP) {
        log.warn("Sending terminate signal to container that failed installation: {}", label);
        response.setTerminateAgent(true);
      }

    } catch (SliderException e) {
      log.warn("Component instance failed operation.", e);
      componentStatus.applyCommandResult(CommandResult.FAILED, command, null);
    }

    log.debug("Heartbeat response: " + response);
    return response;
  }

  private boolean isDockerContainer(String roleGroup) {
    String type = getMetaInfo().getApplicationComponent(roleGroup).getType();
    if (SliderUtils.isSet(type)) {
      return type.toLowerCase().equals(SliderUtils.DOCKER) || type.toLowerCase().equals(SliderUtils.DOCKER_YARN);
    }
    return false;
  }

  private boolean isYarnDockerContainer(String roleGroup) {
    String type = getMetaInfo().getApplicationComponent(roleGroup).getType();
    if (SliderUtils.isSet(type)) {
      return type.toLowerCase().equals(SliderUtils.DOCKER_YARN);
    }
    return false;
  }

  protected void processAllocatedPorts(String fqdn,
                                       String roleName,
                                       String roleGroup,
                                       String containerId,
                                       Map<String, String> ports) {
    RoleInstance instance;
    try {
      instance = getAmState().getOwnedContainer(containerId);
    } catch (NoSuchNodeException e) {
      log.warn("Failed to locate instance of container {}", containerId, e);
      instance = null;
    }
    for (Map.Entry<String, String> port : ports.entrySet()) {
      String portname = port.getKey();
      String portNo = port.getValue();
      log.info("Recording allocated port for {} as {}", portname, portNo);

      // add the allocated ports to the global list as well as per container list
      // per container allocation will over-write each other in the global
      this.getAllocatedPorts().put(portname, portNo);
      this.getAllocatedPorts(containerId).put(portname, portNo);
      if (instance != null) {
        try {
          // if the returned value is not a single port number then there are no
          // meaningful way for Slider to use it during export
          // No need to error out as it may not be the responsibility of the component
          // to allocate port or the component may need an array of ports
          instance.registerPortEndpoint(Integer.valueOf(portNo), portname);
        } catch (NumberFormatException e) {
          log.warn("Failed to parse {}", portNo, e);
        }
      }
    }

    processAndPublishComponentSpecificData(ports, containerId, fqdn, roleGroup);
    processAndPublishComponentSpecificExports(ports, containerId, fqdn, roleName, roleGroup);

    // and update registration entries
    if (instance != null) {
      queueAccess.put(new RegisterComponentInstance(instance.getId(),
          roleName, roleGroup, 0, TimeUnit.MILLISECONDS));
    }
  }

  private void updateComponentStatusWithAgentState(
      ComponentInstanceState componentStatus, State agentState) {
    if (agentState != null) {
      componentStatus.setState(agentState);
    }
  }

  @Override
  public Map<String, MonitorDetail> buildMonitorDetails(ClusterDescription clusterDesc) {
    Map<String, MonitorDetail> details = super.buildMonitorDetails(clusterDesc);
    buildRoleHostDetails(details);
    return details;
  }

  @Override
  public void applyInitialRegistryDefinitions(URL amWebURI,
      URL agentOpsURI,
      URL agentStatusURI,
      ServiceRecord serviceRecord)
    throws IOException {
    super.applyInitialRegistryDefinitions(amWebURI,
                                          agentOpsURI,
                                          agentStatusURI,
                                          serviceRecord);

    try {
      URL restURL = new URL(agentOpsURI, SLIDER_PATH_AGENTS);
      URL agentStatusURL = new URL(agentStatusURI, SLIDER_PATH_AGENTS);

      serviceRecord.addInternalEndpoint(
          new Endpoint(CustomRegistryConstants.AGENT_SECURE_REST_API,
                       ProtocolTypes.PROTOCOL_REST,
                       restURL.toURI()));
      serviceRecord.addInternalEndpoint(
          new Endpoint(CustomRegistryConstants.AGENT_ONEWAY_REST_API,
                       ProtocolTypes.PROTOCOL_REST,
                       agentStatusURL.toURI()));
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void notifyContainerCompleted(ContainerId containerId) {
    // containers get allocated and free'ed without being assigned to any
    // component - so many of the data structures may not be initialized
    if (containerId != null) {
      String containerIdStr = containerId.toString();
      if (getComponentInstanceData().containsKey(containerIdStr)) {
        getComponentInstanceData().remove(containerIdStr);
        log.info("Removing container specific data for {}", containerIdStr);
        publishComponentInstanceData();
      }

      if (this.allocatedPorts.containsKey(containerIdStr)) {
        Map<String, String> portsByContainerId = getAllocatedPorts(containerIdStr);
        this.allocatedPorts.remove(containerIdStr);
        // free up the allocations from global as well
        // if multiple containers allocate global ports then last one
        // wins and similarly first one removes it - its not supported anyway
        for(String portName : portsByContainerId.keySet()) {
          getAllocatedPorts().remove(portName);
        }

      }

      String componentName = null;
      synchronized (this.componentStatuses) {
        for (String label : getComponentStatuses().keySet()) {
          if (label.startsWith(containerIdStr)) {
            componentName = getRoleName(label);
            log.info("Removing component status for label {}", label);
            getComponentStatuses().remove(label);
          }
        }
      }

      tags.releaseTag(componentName, containerIdStr);

      synchronized (this.containerExportsMap) {
        Set<String> containerExportSets = containerExportsMap.get(containerIdStr);
        if (containerExportSets != null) {
          for (String containerExportStr : containerExportSets) {
            String[] parts = containerExportStr.split(":");
            Map<String, List<ExportEntry>> exportGroup = getCurrentExports(parts[0]);
            List<ExportEntry> exports = exportGroup.get(parts[1]);
            List<ExportEntry> exportToRemove = new ArrayList<ExportEntry>();
            for (ExportEntry export : exports) {
              if (containerIdStr.equals(export.getContainerId())) {
                exportToRemove.add(export);
              }
            }
            exports.removeAll(exportToRemove);
          }
          log.info("Removing container exports for {}", containerIdStr);
          containerExportsMap.remove(containerIdStr);
        }
      }
    }
  }

  /**
   * Reads and sets the heartbeat monitoring interval. If bad value is provided then log it and set to default.
   *
   * @param instanceDefinition
   */
  private void readAndSetHeartbeatMonitoringInterval(AggregateConf instanceDefinition) {
    String hbMonitorInterval = instanceDefinition.getAppConfOperations().
        getGlobalOptions().getOption(AgentKeys.HEARTBEAT_MONITOR_INTERVAL,
                                     Integer.toString(DEFAULT_HEARTBEAT_MONITOR_INTERVAL));
    try {
      setHeartbeatMonitorInterval(Integer.parseInt(hbMonitorInterval));
    } catch (NumberFormatException e) {
      log.warn(
          "Bad value {} for {}. Defaulting to ",
          hbMonitorInterval,
          HEARTBEAT_MONITOR_INTERVAL,
          DEFAULT_HEARTBEAT_MONITOR_INTERVAL);
    }
  }

  /**
   * Reads and sets the heartbeat monitoring interval. If bad value is provided then log it and set to default.
   *
   * @param instanceDefinition
   */
  private void initializeAgentDebugCommands(AggregateConf instanceDefinition) {
    String launchParameterStr = instanceDefinition.getAppConfOperations().
        getGlobalOptions().getOption(AgentKeys.AGENT_INSTANCE_DEBUG_DATA, "");
    agentLaunchParameter = new AgentLaunchParameter(launchParameterStr);
  }

  @VisibleForTesting
  protected Map<String, ExportEntry> getLogFolderExports() {
    return logFolderExports;
  }

  @VisibleForTesting
  protected Map<String, ExportEntry> getWorkFolderExports() {
    return workFolderExports;
  }

  @VisibleForTesting
  protected Metainfo getMetaInfo() {
    return this.metaInfo;
  }

  @VisibleForTesting
  protected Map<String, ComponentInstanceState> getComponentStatuses() {
    return componentStatuses;
  }

  @VisibleForTesting
  protected Metainfo getApplicationMetainfo(SliderFileSystem fileSystem,
      String appDef, boolean addonPackage) throws IOException,
      BadConfigException {
    return AgentUtils.getApplicationMetainfo(fileSystem, appDef, addonPackage);
  }

  @VisibleForTesting
  protected Metainfo getApplicationMetainfo(SliderFileSystem fileSystem,
      String appDef) throws IOException, BadConfigException {
    return getApplicationMetainfo(fileSystem, appDef, false);
  }

  @VisibleForTesting
  protected void setHeartbeatMonitorInterval(int heartbeatMonitorInterval) {
    this.heartbeatMonitorInterval = heartbeatMonitorInterval;
  }

  public void setInUpgradeMode(boolean inUpgradeMode) {
    this.isInUpgradeMode = inUpgradeMode;
  }

  public void addUpgradeContainers(Set<String> upgradeContainers) {
    this.upgradeContainers.addAll(upgradeContainers);
  }

  public void setAppStopInitiated(boolean appStopInitiated) {
    this.appStopInitiated = appStopInitiated;
  }

  /**
   * Read all default configs
   *
   * @param fileSystem fs
   * @param appDef app default path
   * @param metainfo metadata
   *
   * @return configuration maps
   * 
   * @throws IOException
   */
  protected Map<String, DefaultConfig> initializeDefaultConfigs(SliderFileSystem fileSystem,
                                                                String appDef, Metainfo metainfo) throws IOException {
    Map<String, DefaultConfig> defaultConfigMap = new HashMap<>();
    if (SliderUtils.isNotEmpty(metainfo.getApplication().getConfigFiles())) {
      for (ConfigFile configFile : metainfo.getApplication().getConfigFiles()) {
        DefaultConfig config = null;
        try {
          config = AgentUtils.getDefaultConfig(fileSystem, appDef, configFile.getDictionaryName() + ".xml");
        } catch (IOException e) {
          log.warn("Default config file not found. Only the config as input during create will be applied for {}",
                   configFile.getDictionaryName());
        }
        if (config != null) {
          defaultConfigMap.put(configFile.getDictionaryName(), config);
        }
      }
    }

    return defaultConfigMap;
  }

  protected Map<String, DefaultConfig> getDefaultConfigs() {
    return defaultConfigs;
  }

  private int getHeartbeatMonitorInterval() {
    return this.heartbeatMonitorInterval;
  }

  private String getClusterName() {
    if (SliderUtils.isUnset(clusterName)) {
      clusterName = getAmState().getInternalsSnapshot().get(OptionKeys.APPLICATION_NAME);
    }
    return clusterName;
  }

  /**
   * Publish a named property bag that may contain name-value pairs for app configurations such as hbase-site
   *
   * @param name
   * @param description
   * @param entries
   */
  protected void publishApplicationInstanceData(String name, String description,
                                                Iterable<Map.Entry<String, String>> entries) {
    PublishedConfiguration pubconf = new PublishedConfiguration();
    pubconf.description = description;
    pubconf.putValues(entries);
    log.info("publishing {}", pubconf);
    getAmState().getPublishedSliderConfigurations().put(name, pubconf);
  }

  /**
   * Get a list of all hosts for all role/container per role
   *
   * @return the map of role->node
   */
  protected Map<String, Map<String, ClusterNode>> getRoleClusterNodeMapping() {
    return amState.getRoleClusterNodeMapping();
  }

  private String getContainerLabel(Container container, String role, String group) {
    if (role.equals(group)) {
      return container.getId().toString() + LABEL_MAKER + role;
    } else {
      return container.getId().toString() + LABEL_MAKER + role + LABEL_MAKER +
          group;
    }
  }

  protected String getClusterInfoPropertyValue(String name) {
    StateAccessForProviders accessor = getAmState();
    assert accessor.isApplicationLive();
    ClusterDescription description = accessor.getClusterStatus();
    return description.getInfo(name);
  }

  protected String getClusterOptionPropertyValue(String name)
      throws BadConfigException {
    StateAccessForProviders accessor = getAmState();
    assert accessor.isApplicationLive();
    ClusterDescription description = accessor.getClusterStatus();
    return description.getMandatoryOption(name);
  }

  /**
   * Lost heartbeat from the container - release it and ask for a replacement (async operation)
   *
   * @param label
   * @param containerId
   */
  protected void lostContainer(
      String label,
      ContainerId containerId) {
    getComponentStatuses().remove(label);
    getQueueAccess().put(new ProviderReportedContainerLoss(containerId));
  }

  /**
   * Build the provider status, can be empty
   *
   * @return the provider status - map of entries to add to the info section
   */
  public Map<String, String> buildProviderStatus() {
    Map<String, String> stats = new HashMap<String, String>();
    return stats;
  }


  /**
   * Format the folder locations and publish in the registry service
   *
   * @param folders
   * @param containerId
   * @param hostFqdn
   * @param componentName
   */
  protected void publishFolderPaths(
      Map<String, String> folders, String containerId, String componentName, String hostFqdn) {
    Date now = new Date();
    for (Map.Entry<String, String> entry : folders.entrySet()) {
      ExportEntry exportEntry = new ExportEntry();
      exportEntry.setValue(String.format(HOST_FOLDER_FORMAT, hostFqdn, entry.getValue()));
      exportEntry.setContainerId(containerId);
      exportEntry.setLevel(COMPONENT_TAG);
      exportEntry.setTag(componentName);
      exportEntry.setUpdatedTime(now.toString());
      if (entry.getKey().equals("AGENT_LOG_ROOT")) {
        synchronized (logFolderExports) {
          getLogFolderExports().put(containerId, exportEntry);
        }
      } else {
        synchronized (workFolderExports) {
          getWorkFolderExports().put(containerId, exportEntry);
        }
      }
      log.info("Updating log and pwd folders for container {}", containerId);
    }

    PublishedExports exports = new PublishedExports(CONTAINER_LOGS_TAG);
    exports.setUpdated(now.getTime());
    synchronized (logFolderExports) {
      updateExportsFromList(exports, getLogFolderExports());
    }
    getAmState().getPublishedExportsSet().put(CONTAINER_LOGS_TAG, exports);

    exports = new PublishedExports(CONTAINER_PWDS_TAG);
    exports.setUpdated(now.getTime());
    synchronized (workFolderExports) {
      updateExportsFromList(exports, getWorkFolderExports());
    }
    getAmState().getPublishedExportsSet().put(CONTAINER_PWDS_TAG, exports);
  }

  /**
   * Update the export data from the map
   * @param exports
   * @param folderExports
   */
  private void updateExportsFromList(PublishedExports exports, Map<String, ExportEntry> folderExports) {
    Map<String, List<ExportEntry>> perComponentList = new HashMap<String, List<ExportEntry>>();
    for(Map.Entry<String, ExportEntry> logEntry : folderExports.entrySet())
    {
      String componentName = logEntry.getValue().getTag();
      if (!perComponentList.containsKey(componentName)) {
        perComponentList.put(componentName, new ArrayList<ExportEntry>());
      }
      perComponentList.get(componentName).add(logEntry.getValue());
    }
    exports.putValues(perComponentList.entrySet());
  }


  /**
   * Process return status for component instances
   *
   * @param heartBeat
   * @param componentStatus
   */
  protected void publishConfigAndExportGroups(HeartBeat heartBeat,
      ComponentInstanceState componentStatus, String componentGroup) {
    List<ComponentStatus> statuses = heartBeat.getComponentStatus();
    if (statuses != null && !statuses.isEmpty()) {
      log.info("Processing {} status reports.", statuses.size());
      for (ComponentStatus status : statuses) {
        log.info("Status report: {}", status.toString());

        if (status.getConfigs() != null) {
          Application application = getMetaInfo().getApplication();

          if (canAnyMasterPublishConfig() == false || canPublishConfig(componentGroup)) {
            // If no Master can explicitly publish then publish if its a master
            // Otherwise, wait till the master that can publish is ready

            Set<String> exportedConfigs = new HashSet();
            String exportedConfigsStr = application.getExportedConfigs();
            boolean exportedAllConfigs = exportedConfigsStr == null || exportedConfigsStr.isEmpty();
            if (!exportedAllConfigs) {
              for (String exportedConfig : exportedConfigsStr.split(",")) {
                if (exportedConfig.trim().length() > 0) {
                  exportedConfigs.add(exportedConfig.trim());
                }
              }
            }

            for (String key : status.getConfigs().keySet()) {
              if ((!exportedAllConfigs && exportedConfigs.contains(key)) ||
                  exportedAllConfigs) {
                Map<String, String> configs = status.getConfigs().get(key);
                publishApplicationInstanceData(key, key, configs.entrySet());
              }
            }
          }

          List<ExportGroup> appExportGroups = application.getExportGroups();
          boolean hasExportGroups = SliderUtils.isNotEmpty(appExportGroups);

          Set<String> appExports = new HashSet();
          String appExportsStr = getApplicationComponent(componentGroup).getAppExports();
          if (SliderUtils.isSet(appExportsStr)) {
            for (String appExport : appExportsStr.split(",")) {
              if (!appExport.trim().isEmpty()) {
                appExports.add(appExport.trim());
              }
            }
          }

          if (hasExportGroups && !appExports.isEmpty()) {
            String configKeyFormat = "${site.%s.%s}";
            String hostKeyFormat = "${%s_HOST}";

            // publish export groups if any
            Map<String, String> replaceTokens = new HashMap<String, String>();
            for (Map.Entry<String, Map<String, ClusterNode>> entry : getRoleClusterNodeMapping().entrySet()) {
              String hostName = getHostsList(entry.getValue().values(), true).iterator().next();
              replaceTokens.put(String.format(hostKeyFormat, entry.getKey().toUpperCase(Locale.ENGLISH)), hostName);
            }

            for (String key : status.getConfigs().keySet()) {
              Map<String, String> configs = status.getConfigs().get(key);
              for (String configKey : configs.keySet()) {
                String lookupKey = String.format(configKeyFormat, key, configKey);
                replaceTokens.put(lookupKey, configs.get(configKey));
              }
            }

            Set<String> modifiedGroups = new HashSet<String>();
            for (ExportGroup exportGroup : appExportGroups) {
              List<Export> exports = exportGroup.getExports();
              if (SliderUtils.isNotEmpty(exports)) {
                String exportGroupName = exportGroup.getName();
                ConcurrentHashMap<String, List<ExportEntry>> map =
                    (ConcurrentHashMap<String, List<ExportEntry>>)getCurrentExports(exportGroupName);
                for (Export export : exports) {
                  if (canBeExported(exportGroupName, export.getName(), appExports)) {
                    String value = export.getValue();
                    // replace host names
                    for (String token : replaceTokens.keySet()) {
                      if (value.contains(token)) {
                        value = value.replace(token, replaceTokens.get(token));
                      }
                    }
                    ExportEntry entry = new ExportEntry();
                    entry.setLevel(APPLICATION_TAG);
                    entry.setValue(value);
                    entry.setUpdatedTime(new Date().toString());
                    // over-write, app exports are singletons
                    map.put(export.getName(), new ArrayList(Arrays.asList(entry)));
                    log.info("Preparing to publish. Key {} and Value {}", export.getName(), value);
                  }
                }
                modifiedGroups.add(exportGroupName);
              }
            }
            publishModifiedExportGroups(modifiedGroups);
          }

          log.info("Received and processed config for {}", heartBeat.getHostname());
          componentStatus.setConfigReported(true);

        }
      }
    }
  }

  private boolean canBeExported(String exportGroupName, String name, Set<String> appExports) {
    return appExports.contains(String.format("%s-%s", exportGroupName, name));
  }

  protected Map<String, List<ExportEntry>> getCurrentExports(String groupName) {
    if (!this.exportGroups.containsKey(groupName)) {
      synchronized (this.exportGroups) {
        if (!this.exportGroups.containsKey(groupName)) {
          this.exportGroups.put(groupName, new ConcurrentHashMap<String, List<ExportEntry>>());
        }
      }
    }

    return this.exportGroups.get(groupName);
  }

  private void publishModifiedExportGroups(Set<String> modifiedGroups) {
    for (String groupName : modifiedGroups) {
      Map<String, List<ExportEntry>> entries = this.exportGroups.get(groupName);

      // Publish in old format for the time being
      Map<String, String> simpleEntries = new HashMap<String, String>();
      for (Map.Entry<String, List<ExportEntry>> entry : entries.entrySet()) {
        List<ExportEntry> exports = entry.getValue();
        if (SliderUtils.isNotEmpty(exports)) {
          // there is no support for multiple exports per name - so extract only the first one
          simpleEntries.put(entry.getKey(), entry.getValue().get(0).getValue());
        }
      }
      publishApplicationInstanceData(groupName, groupName, simpleEntries.entrySet());

      PublishedExports exports = new PublishedExports(groupName);
      exports.setUpdated(new Date().getTime());
      exports.putValues(entries.entrySet());
      getAmState().getPublishedExportsSet().put(groupName, exports);
    }
  }

  /** Publish component instance specific data if the component demands it */
  protected void processAndPublishComponentSpecificData(Map<String, String> ports,
                                                        String containerId,
                                                        String hostFqdn,
                                                        String componentGroup) {
    String portVarFormat = "${site.%s}";
    String hostNamePattern = "${THIS_HOST}";
    Map<String, String> toPublish = new HashMap<String, String>();

    Application application = getMetaInfo().getApplication();
    for (Component component : application.getComponents()) {
      if (component.getName().equals(componentGroup)) {
        if (component.getComponentExports().size() > 0) {

          for (ComponentExport export : component.getComponentExports()) {
            String templateToExport = export.getValue();
            for (String portName : ports.keySet()) {
              boolean publishData = false;
              String portValPattern = String.format(portVarFormat, portName);
              if (templateToExport.contains(portValPattern)) {
                templateToExport = templateToExport.replace(portValPattern, ports.get(portName));
                publishData = true;
              }
              if (templateToExport.contains(hostNamePattern)) {
                templateToExport = templateToExport.replace(hostNamePattern, hostFqdn);
                publishData = true;
              }
              if (publishData) {
                toPublish.put(export.getName(), templateToExport);
                log.info("Publishing {} for name {} and container {}",
                         templateToExport, export.getName(), containerId);
              }
            }
          }
        }
      }
    }

    if (toPublish.size() > 0) {
      Map<String, String> perContainerData = null;
      if (!getComponentInstanceData().containsKey(containerId)) {
        perContainerData = new ConcurrentHashMap<String, String>();
      } else {
        perContainerData = getComponentInstanceData().get(containerId);
      }
      perContainerData.putAll(toPublish);
      getComponentInstanceData().put(containerId, perContainerData);
      publishComponentInstanceData();
    }
  }

  /** Publish component instance specific data if the component demands it */
  protected void processAndPublishComponentSpecificExports(Map<String, String> ports,
                                                           String containerId,
                                                           String hostFqdn,
                                                           String compName,
                                                           String compGroup) {
    String portVarFormat = "${site.%s}";
    String hostNamePattern = "${" + compGroup + "_HOST}";

    List<ExportGroup> appExportGroups = getMetaInfo().getApplication().getExportGroups();
    Component component = getMetaInfo().getApplicationComponent(compGroup);
    if (component != null && SliderUtils.isSet(component.getCompExports())
        && SliderUtils.isNotEmpty(appExportGroups)) {

      Set<String> compExports = new HashSet();
      String compExportsStr = component.getCompExports();
      for (String compExport : compExportsStr.split(",")) {
        if (!compExport.trim().isEmpty()) {
          compExports.add(compExport.trim());
        }
      }

      Date now = new Date();
      Set<String> modifiedGroups = new HashSet<String>();
      for (ExportGroup exportGroup : appExportGroups) {
        List<Export> exports = exportGroup.getExports();
        if (SliderUtils.isNotEmpty(exports)) {
          String exportGroupName = exportGroup.getName();
          ConcurrentHashMap<String, List<ExportEntry>> map =
              (ConcurrentHashMap<String, List<ExportEntry>>) getCurrentExports(exportGroupName);
          for (Export export : exports) {
            if (canBeExported(exportGroupName, export.getName(), compExports)) {
              log.info("Attempting to publish {} of group {} for component type {}",
                       export.getName(), exportGroupName, compName);
              String templateToExport = export.getValue();
              for (String portName : ports.keySet()) {
                boolean publishData = false;
                String portValPattern = String.format(portVarFormat, portName);
                if (templateToExport.contains(portValPattern)) {
                  templateToExport = templateToExport.replace(portValPattern, ports.get(portName));
                  publishData = true;
                }
                if (templateToExport.contains(hostNamePattern)) {
                  templateToExport = templateToExport.replace(hostNamePattern, hostFqdn);
                  publishData = true;
                }
                if (publishData) {
                  ExportEntry entryToAdd = new ExportEntry();
                  entryToAdd.setLevel(COMPONENT_TAG);
                  entryToAdd.setValue(templateToExport);
                  entryToAdd.setUpdatedTime(now.toString());
                  entryToAdd.setContainerId(containerId);
                  entryToAdd.setTag(tags.getTag(compName, containerId));

                  List<ExportEntry> existingList =
                      map.putIfAbsent(export.getName(), new CopyOnWriteArrayList(Arrays.asList(entryToAdd)));

                  // in-place edit, no lock needed
                  if (existingList != null) {
                    boolean updatedInPlace = false;
                    for (ExportEntry entry : existingList) {
                      if (containerId.toLowerCase(Locale.ENGLISH)
                                     .equals(entry.getContainerId())) {
                        entryToAdd.setValue(templateToExport);
                        entryToAdd.setUpdatedTime(now.toString());
                        updatedInPlace = true;
                      }
                    }
                    if (!updatedInPlace) {
                      existingList.add(entryToAdd);
                    }
                  }

                  log.info("Publishing {} for name {} and container {}",
                           templateToExport, export.getName(), containerId);
                  modifiedGroups.add(exportGroupName);
                  synchronized (containerExportsMap) {
                    if (!containerExportsMap.containsKey(containerId)) {
                      containerExportsMap.put(containerId, new HashSet<String>());
                    }
                    Set<String> containerExportMaps = containerExportsMap.get(containerId);
                    containerExportMaps.add(String.format("%s:%s", exportGroupName, export.getName()));
                  }
                }
              }
            }
          }
        }
      }
      publishModifiedExportGroups(modifiedGroups);
    }
  }

  private void publishComponentInstanceData() {
    Map<String, String> dataToPublish = new HashMap<String, String>();
    for (String container : getComponentInstanceData().keySet()) {
      for (String prop : getComponentInstanceData().get(container).keySet()) {
        dataToPublish.put(
            container + "." + prop, getComponentInstanceData().get(container).get(prop));
      }
    }
    publishApplicationInstanceData(COMPONENT_DATA_TAG, COMPONENT_DATA_TAG, dataToPublish.entrySet());
  }

  /**
   * Return Component based on group
   *
   * @param roleGroup component group
   *
   * @return the component entry or null for no match
   */
  protected Component getApplicationComponent(String roleGroup) {
    return getMetaInfo().getApplicationComponent(roleGroup);
  }

  /**
   * Extract script path from the application metainfo
   *
   * @param roleGroup component group
   * @return the script path or null for no match
   */
  protected CommandScript getScriptPathForMasterPackage(String roleGroup) {
    Component component = getApplicationComponent(roleGroup);
    if (component != null) {
      return component.getCommandScript();
    }
    return null;
  }

  /**
   * Is the role of type MASTER
   *
   * @param roleGroup component group
   *
   * @return true if the role category is MASTER
   */
  protected boolean isMaster(String roleGroup) {
    Component component = getApplicationComponent(roleGroup);
    if (component != null) {
      if (component.getCategory().equals("MASTER")) {
        return true;
      }
    }
    return false;
  }

  /**
   * Can the role publish configuration
   *
   * @param roleGroup component group
   *
   * @return true if it can be pubished
   */
  protected boolean canPublishConfig(String roleGroup) {
    Component component = getApplicationComponent(roleGroup);
    if (component != null) {
      return Boolean.TRUE.toString().equals(component.getPublishConfig());
    }
    return false;
  }

  /**
   * Checks if the role is marked auto-restart
   *
   * @param roleGroup component group
   *
   * @return true if it is auto-restart
   */
  protected boolean isMarkedAutoRestart(String roleGroup) {
    Component component = getApplicationComponent(roleGroup);
    if (component != null) {
      return component.getAutoStartOnFailureBoolean();
    }
    return false;
  }

  /**
   * Can any master publish config explicitly, if not a random master is used
   *
   * @return true if the condition holds
   */
  protected boolean canAnyMasterPublishConfig() {
    if (canAnyMasterPublish == null) {
      Application application = getMetaInfo().getApplication();
      if (application == null) {
        log.error("Malformed app definition: Expect application as root element in the metainfo.xml");
      } else {
        for (Component component : application.getComponents()) {
          if (Boolean.TRUE.toString().equals(component.getPublishConfig()) &&
              component.getCategory().equals("MASTER")) {
            canAnyMasterPublish = true;
          }
        }
      }
    }

    if (canAnyMasterPublish == null) {
      canAnyMasterPublish = false;
    }
    return canAnyMasterPublish;
  }

  private String getRoleName(String label) {
    int index1 = label.indexOf(LABEL_MAKER);
    int index2 = label.lastIndexOf(LABEL_MAKER);
    if (index1 == index2) {
      return label.substring(index1 + LABEL_MAKER.length());
    } else {
      return label.substring(index1 + LABEL_MAKER.length(), index2);
    }
  }

  private String getRoleGroup(String label) {
    return label.substring(label.lastIndexOf(LABEL_MAKER) + LABEL_MAKER.length());
  }

  private String getContainerId(String label) {
    return label.substring(0, label.indexOf(LABEL_MAKER));
  }

  /**
   * Add install command to the heartbeat response
   *
   * @param roleName
   * @param roleGroup
   * @param containerId
   * @param response
   * @param scriptPath
   * @param pkg
   *          when this field is null, it indicates the command is for the
   *          master package; while not null, for the package named by this
   *          field
   * @throws SliderException
   */
  @VisibleForTesting
  protected void addInstallCommand(String roleName,
                                   String roleGroup,
                                   String containerId,
                                   HeartBeatResponse response,
                                   String scriptPath,
                                   ComponentCommand compCmd,
                                   long timeout,
                                   String pkg)
      throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();

    ExecutionCommand cmd = new ExecutionCommand(AgentCommandType.EXECUTION_COMMAND);
    prepareExecutionCommand(cmd);
    String clusterName = getClusterName();
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(Command.INSTALL.toString());
    cmd.setServiceName(clusterName);
    cmd.setComponentName(roleName);
    cmd.setRole(roleName);
    cmd.setPkg(pkg);
    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(JAVA_HOME, appConf.getGlobalOptions().getOption(JAVA_HOME, getJDKDir()));
    hostLevelParams.put(PACKAGE_LIST, getPackageList());
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    Map<String, Map<String, String>> configurations =
        buildCommandConfigurations(appConf, containerId, roleName, roleGroup);
    cmd.setConfigurations(configurations);
    Map<String, Map<String, String>> componentConfigurations = buildComponentConfigurations(appConf);
    cmd.setComponentConfigurations(componentConfigurations);
    
    if (SliderUtils.isSet(scriptPath)) {
      cmd.setCommandParams(commandParametersSet(scriptPath, timeout, false));
    } else {
      // assume it to be default shell command
      ComponentCommand effectiveCommand = compCmd;
      if (effectiveCommand == null) {
        effectiveCommand = ComponentCommand.getDefaultComponentCommand("INSTALL");
      }
      cmd.setCommandParams(commandParametersSet(effectiveCommand, timeout, false));
      configurations.get("global").put("exec_cmd", effectiveCommand.getExec());
    }

    cmd.setHostname(getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME));

    response.addExecutionCommand(cmd);

    log.debug("command looks like: {} ",  cmd);
  }

  @VisibleForTesting
  protected void addInstallDockerCommand(String roleName,
                                   String roleGroup,
                                   String containerId,
                                   HeartBeatResponse response,
                                   ComponentCommand compCmd,
                                   long timeout)
      throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();

    ExecutionCommand cmd = new ExecutionCommand(AgentCommandType.EXECUTION_COMMAND);
    prepareExecutionCommand(cmd);
    String clusterName = getClusterName();
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(Command.INSTALL.toString());
    cmd.setServiceName(clusterName);
    cmd.setComponentName(roleName);
    cmd.setRole(roleName);
    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(PACKAGE_LIST, getPackageList());
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    Map<String, Map<String, String>> configurations = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    cmd.setConfigurations(configurations);
    Map<String, Map<String, String>> componentConfigurations = buildComponentConfigurations(appConf);
    cmd.setComponentConfigurations(componentConfigurations);
    
    ComponentCommand effectiveCommand = compCmd;
    if (compCmd == null) {
      effectiveCommand = new ComponentCommand();
      effectiveCommand.setName("INSTALL");
      effectiveCommand.setExec("DEFAULT");
    }
    cmd.setCommandParams(setCommandParameters(effectiveCommand, timeout, false));
    configurations.get("global").put("exec_cmd", effectiveCommand.getExec());

    cmd.setHostname(getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME));
    cmd.addContainerDetails(roleGroup, getMetaInfo());

    Map<String, String> dockerConfig = new HashMap<String, String>();
    if(isYarnDockerContainer(roleGroup)){
      //put nothing
      cmd.setYarnDockerMode(true);
    } else {
      dockerConfig.put(
          "docker.command_path",
          getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
              "commandPath"));
      dockerConfig.put("docker.image_name",
          getConfigFromMetaInfo(roleGroup, "image"));
    }
    configurations.put("docker", dockerConfig);

    log.debug("Docker- command: {}", cmd.toString());

    response.addExecutionCommand(cmd);
  }

  private Map<String, String> setCommandParameters(String scriptPath,
      long timeout, boolean recordConfig) {
    Map<String, String> cmdParams = new TreeMap<String, String>();
    cmdParams.put("service_package_folder",
        "${AGENT_WORK_ROOT}/work/app/definition/package");
    cmdParams.put("script", scriptPath);
    cmdParams.put("schema_version", "2.0");
    cmdParams.put("command_timeout", Long.toString(timeout));
    cmdParams.put("script_type", AbstractComponent.TYPE_PYTHON);
    cmdParams.put("record_config", Boolean.toString(recordConfig));
    return cmdParams;
  }

  private Map<String, String> setCommandParameters(ComponentCommand compCmd,
      long timeout, boolean recordConfig) {
    Map<String, String> cmdParams = new TreeMap<String, String>();
    cmdParams.put("service_package_folder",
        "${AGENT_WORK_ROOT}/work/app/definition/package");
    cmdParams.put("command", compCmd.getExec());
    cmdParams.put("schema_version", "2.0");
    cmdParams.put("command_timeout", Long.toString(timeout));
    cmdParams.put("script_type", compCmd.getType());
    cmdParams.put("record_config", Boolean.toString(recordConfig));
    return cmdParams;
  }

  private Map<String, Map<String, String>> buildComponentConfigurations(
      ConfTreeOperations appConf) {
    return appConf.getComponents();
  }

  protected static String getPackageListFromApplication(Application application) {
    String pkgFormatString = "{\"type\":\"%s\",\"name\":\"%s\"}";
    String pkgListFormatString = "[%s]";
    List<String> packages = new ArrayList<>();
    if (application != null) {
      if (application.getPackages().size() > 0) {
        List<Package> appPackages = application.getPackages();
        for (Package appPackage : appPackages) {
          packages.add(String.format(pkgFormatString, appPackage.getType(), appPackage.getName()));
        }
      } else {
        List<OSSpecific> osSpecifics = application.getOSSpecifics();
        if (osSpecifics != null && osSpecifics.size() > 0) {
          for (OSSpecific osSpecific : osSpecifics) {
            if (osSpecific.getOsType().equals("any")) {
              for (OSPackage osPackage : osSpecific.getPackages()) {
                packages.add(String.format(pkgFormatString, osPackage.getType(), osPackage.getName()));
              }
            }
          }
        }
      }
    }

    if (!packages.isEmpty()) {
      return "[" + SliderUtils.join(packages, ",", false) + "]";
    } else {
      return "[]";
    }
  }

  private String getPackageList() {
    return getPackageListFromApplication(getMetaInfo().getApplication());
  }

  private void prepareExecutionCommand(ExecutionCommand cmd) {
    cmd.setTaskId(taskId.incrementAndGet());
    cmd.setCommandId(cmd.getTaskId() + "-1");
  }

  private Map<String, String> commandParametersSet(String scriptPath, long timeout, boolean recordConfig) {
    Map<String, String> cmdParams = new TreeMap<>();
    cmdParams.put("service_package_folder",
                  "${AGENT_WORK_ROOT}/work/app/definition/package");
    cmdParams.put("script", scriptPath);
    cmdParams.put("schema_version", "2.0");
    cmdParams.put("command_timeout", Long.toString(timeout));
    cmdParams.put("script_type", "PYTHON");
    cmdParams.put("record_config", Boolean.toString(recordConfig));
    return cmdParams;
  }

  private Map<String, String> commandParametersSet(ComponentCommand compCmd, long timeout, boolean recordConfig) {
    Map<String, String> cmdParams = new TreeMap<>();
    cmdParams.put("service_package_folder",
                  "${AGENT_WORK_ROOT}/work/app/definition/package");
    cmdParams.put("command", compCmd.getExec());
    cmdParams.put("schema_version", "2.0");
    cmdParams.put("command_timeout", Long.toString(timeout));
    cmdParams.put("script_type", compCmd.getType());
    cmdParams.put("record_config", Boolean.toString(recordConfig));
    return cmdParams;
  }

  @VisibleForTesting
  protected void addStatusCommand(String roleName,
                                  String roleGroup,
                                  String containerId,
                                  HeartBeatResponse response,
                                  String scriptPath,
                                  long timeout)
      throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    if (isDockerContainer(roleGroup) || isYarnDockerContainer(roleGroup)) {
      addStatusDockerCommand(roleName, roleGroup, containerId, response,
          scriptPath, timeout);
      return;
    }

    StatusCommand cmd = new StatusCommand();
    String clusterName = getClusterName();

    cmd.setCommandType(AgentCommandType.STATUS_COMMAND);
    cmd.setComponentName(roleName);
    cmd.setServiceName(clusterName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(StatusCommand.STATUS_COMMAND);

    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(JAVA_HOME, appConf.getGlobalOptions().getOption(JAVA_HOME, getJDKDir()));
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    cmd.setCommandParams(commandParametersSet(scriptPath, timeout, false));

    Map<String, Map<String, String>> configurations = buildCommandConfigurations(appConf, containerId, roleName, roleGroup);

    cmd.setConfigurations(configurations);

    response.addStatusCommand(cmd);
  }

  @VisibleForTesting
  protected void addStatusDockerCommand(String roleName,
                                  String roleGroup,
                                  String containerId,
                                  HeartBeatResponse response,
                                  String scriptPath,
                                  long timeout)
      throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();

    StatusCommand cmd = new StatusCommand();
    String clusterName = getClusterName();

    cmd.setCommandType(AgentCommandType.STATUS_COMMAND);
    cmd.setComponentName(roleName);
    cmd.setServiceName(clusterName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(StatusCommand.STATUS_COMMAND);

    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(JAVA_HOME, appConf.getGlobalOptions().getMandatoryOption(JAVA_HOME));
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    cmd.setCommandParams(setCommandParameters(scriptPath, timeout, false));

    Map<String, Map<String, String>> configurations = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    Map<String, String> dockerConfig = new HashMap<String, String>();
    String statusCommand = getConfigFromMetaInfoWithAppConfigOverriding(roleGroup, "statusCommand");
    if (statusCommand == null) {
      if(isYarnDockerContainer(roleGroup)){
        //should complain the required field is null
        cmd.setYarnDockerMode(true);
      } else {
        statusCommand = "docker top "
            + containerId
            + " | grep \"\"";// default value
      }
    }
    dockerConfig.put("docker.status_command",statusCommand);
    configurations.put("docker", dockerConfig);
    cmd.setConfigurations(configurations);
    log.debug("Docker- status {}", cmd);
    response.addStatusCommand(cmd);
  }

  @VisibleForTesting
  protected void addGetConfigDockerCommand(String roleName, String roleGroup,
      String containerId, HeartBeatResponse response) throws SliderException {
    assert getAmState().isApplicationLive();

    StatusCommand cmd = new StatusCommand();
    String clusterName = getClusterName();

    cmd.setCommandType(AgentCommandType.STATUS_COMMAND);
    cmd.setComponentName(roleName);
    cmd.setServiceName(clusterName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(StatusCommand.GET_CONFIG_COMMAND);
    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    hostLevelParams.put(CONTAINER_ID, containerId);

    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    Map<String, Map<String, String>> configurations = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    Map<String, String> dockerConfig = new HashMap<String, String>();
    String statusCommand = getConfigFromMetaInfoWithAppConfigOverriding(roleGroup, "statusCommand");
    if (statusCommand == null) {
      if(isYarnDockerContainer(roleGroup)){
        //should complain the required field is null
        cmd.setYarnDockerMode(true);
      } else {
        statusCommand = "docker top "
            + containerId
            + " | grep \"\"";// default value
      }
    }
    dockerConfig.put("docker.status_command",statusCommand);
    configurations.put("docker", dockerConfig);

    cmd.setConfigurations(configurations);
    log.debug("Docker- getconfig command {}", cmd);
    
    response.addStatusCommand(cmd);
  }
  
  private String getConfigFromMetaInfoWithAppConfigOverriding(String roleGroup,
      String configName){
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    String containerName = getMetaInfo().getApplicationComponent(roleGroup)
        .getDockerContainers().get(0).getName();
    String composedConfigName = null;
    String appConfigValue = null;
    //if the configName is about port , mount, inputfile, then check differently
    if (configName.equals("containerPort") || configName.equals("hostPort")){
      composedConfigName = containerName + ".ports." + configName;
    } else 
    if (configName.equals("containerMount")
        || configName.equals("hostMount")){
      composedConfigName = containerName + ".mounts." + configName;
    } else
    if (configName.equals("containerPath")
        || configName.equals("fileLocalPath")) {
      composedConfigName = containerName + ".inputFiles." + configName;
    } else {
      composedConfigName = containerName + "." + configName;
    }
    appConfigValue = appConf.getComponentOpt(roleGroup, composedConfigName,
        null);
    log.debug(
        "Docker- value from appconfig component: {} configName: {} value: {}",
        roleGroup, composedConfigName, appConfigValue);
    if (appConfigValue == null) {
      appConfigValue = getConfigFromMetaInfo(roleGroup, configName);
      log.debug(
          "Docker- value from metainfo component: {} configName: {} value: {}",
          roleGroup, configName, appConfigValue);

    }
    return appConfigValue;
  }

  @VisibleForTesting
  protected void addStartDockerCommand(String roleName, String roleGroup,
      String containerId, HeartBeatResponse response,
      ComponentCommand startCommand, long timeout, boolean isMarkedAutoRestart)
      throws
      SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    ConfTreeOperations internalsConf = getAmState().getInternalsSnapshot();

    ExecutionCommand cmd = new ExecutionCommand(AgentCommandType.EXECUTION_COMMAND);
    prepareExecutionCommand(cmd);
    String clusterName = internalsConf.get(OptionKeys.APPLICATION_NAME);
    String hostName = getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME);
    cmd.setHostname(hostName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(Command.START.toString());
    cmd.setServiceName(clusterName);
    cmd.setComponentName(roleName);
    cmd.setRole(roleName);
    Map<String, String> hostLevelParams = new TreeMap<>();
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    Map<String, String> roleParams = new TreeMap<>();
    cmd.setRoleParams(roleParams);
    cmd.getRoleParams().put("auto_restart", Boolean.toString(isMarkedAutoRestart));
    startCommand = new ComponentCommand();
    startCommand.setName("START");
    startCommand.setType("docker");
    startCommand.setExec("exec");
    cmd.setCommandParams(setCommandParameters(startCommand, timeout, true));
    
    Map<String, Map<String, String>> configurations = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    Map<String, Map<String, String>> componentConfigurations = buildComponentConfigurations(appConf);
    cmd.setComponentConfigurations(componentConfigurations);
    
    Map<String, String> dockerConfig = new HashMap<String, String>();
    if (isYarnDockerContainer(roleGroup)) {
      dockerConfig.put(
          "docker.startCommand",
          getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
              "start_command"));
      cmd.setYarnDockerMode(true);
    } else {
      dockerConfig.put(
        "docker.command_path",
        getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
            "commandPath"));

      dockerConfig.put("docker.image_name",
          getConfigFromMetaInfo(roleGroup, "image"));
      // options should always have -d
      String options = getConfigFromMetaInfoWithAppConfigOverriding(
          roleGroup, "options");
      if(options != null && !options.isEmpty()){
        options = options + " -d";
      } else {
        options = "-d";
      }
      dockerConfig.put("docker.options", options);
      // options should always have -d
      dockerConfig.put(
          "docker.containerPort",
          getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
              "containerPort"));
      dockerConfig
          .put(
              "docker.hostPort",
              getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
                  "hostPort"));
  
      dockerConfig.put(
          "docker.mounting_directory",
          getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
              "containerMount"));
      dockerConfig
          .put(
              "docker.host_mounting_directory",
              getConfigFromMetaInfoWithAppConfigOverriding(roleGroup,
                  "hostMount"));
  
      dockerConfig.put("docker.additional_param",
          getConfigFromMetaInfoWithAppConfigOverriding(roleGroup, "additionalParam"));
  
      dockerConfig.put("docker.input_file.mount_path", getConfigFromMetaInfo(
          roleGroup, "containerPath"));
    }

    String lifetime = getConfigFromMetaInfoWithAppConfigOverriding(
        roleGroup, "lifetime");
    dockerConfig.put("docker.lifetime", lifetime);
    configurations.put("docker", dockerConfig);
    String statusCommand = getConfigFromMetaInfoWithAppConfigOverriding(
        roleGroup, "statusCommand");
    if (statusCommand == null) {
      if(isYarnDockerContainer(roleGroup)){
        //should complain the required field is null
      } else {
        statusCommand = "docker top "
          + containerId + " | grep \"\"";
      }
    }
    dockerConfig.put("docker.status_command",statusCommand);
    
    cmd.setConfigurations(configurations);
   // configurations.get("global").put("exec_cmd", startCommand.getExec());
    cmd.addContainerDetails(roleGroup, getMetaInfo());

    log.info("Docker- command: {}", cmd.toString());

    response.addExecutionCommand(cmd);
  }

  private String getConfigFromMetaInfo(String roleGroup, String configName) {
    String result = null;

    List<DockerContainer> containers = getMetaInfo().getApplicationComponent(
        roleGroup).getDockerContainers();// to support multi container per
                                             // component later
    log.debug("Docker- containers metainfo: {}", containers.toString());
    if (containers.size() > 0) {
      DockerContainer container = containers.get(0);
      switch (configName) {
      case "start_command":
        result = container.getStartCommand();
        break;
      case "image":
        result = container.getImage();
        break;
      case "network":
        if (container.getNetwork() == null || container.getNetwork().isEmpty()) {
          result = "none";
        } else {
          result = container.getNetwork();
        }
        break;
      case "useNetworkScript":
        if (container.getUseNetworkScript() == null || container.getUseNetworkScript().isEmpty()) {
          result = "yes";
        } else {
          result = container.getUseNetworkScript();
        }
        break;
      case "statusCommand":
        result = container.getStatusCommand();
        break;
      case "commandPath":
        result = container.getCommandPath();
        break;
      case "options":
        result = container.getOptions();
        break;
      case "containerPort":
        result = container.getPorts().size() > 0 ? container.getPorts().get(0)
            .getContainerPort() : null;// to support
        // multi port
        // later
        break;
      case "hostPort":
        result = container.getPorts().size() > 0 ? container.getPorts().get(0)
            .getHostPort() : null;// to support multi
        // port later
        break;
      case "containerMount":
        result = container.getMounts().size() > 0 ? container.getMounts()
            .get(0).getContainerMount() : null;// to support
        // multi port
        // later
        break;
      case "hostMount":
        result = container.getMounts().size() > 0 ? container.getMounts()
            .get(0).getHostMount() : null;// to support multi
        // port later
        break;
      case "additionalParam":
        result = container.getAdditionalParam();// to support multi port later
        break;
      case "runPriviledgedContainer":
        if (container.getRunPrivilegedContainer() == null) {
          result = "false";
        } else {
          result = container.getRunPrivilegedContainer();
        }
        break;
      default:
        break;
      }
    }
    log.debug("Docker- component: {} configName: {} value: {}", roleGroup, configName, result);
    return result;
  }

  @VisibleForTesting
  protected void addGetConfigCommand(String roleName, String roleGroup,
      String containerId, HeartBeatResponse response) throws SliderException {
    assert getAmState().isApplicationLive();

    StatusCommand cmd = new StatusCommand();
    String clusterName = getClusterName();

    cmd.setCommandType(AgentCommandType.STATUS_COMMAND);
    cmd.setComponentName(roleName);
    cmd.setServiceName(clusterName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(StatusCommand.GET_CONFIG_COMMAND);
    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    hostLevelParams.put(CONTAINER_ID, containerId);

    response.addStatusCommand(cmd);
  }

  @VisibleForTesting
  protected void addStartCommand(String roleName, String roleGroup, String containerId,
                                 HeartBeatResponse response,
                                 String scriptPath, ComponentCommand startCommand,
                                 ComponentCommand stopCommand,
                                 long timeout, boolean isMarkedAutoRestart)
      throws
      SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    ConfTreeOperations internalsConf = getAmState().getInternalsSnapshot();

    ExecutionCommand cmd = new ExecutionCommand(AgentCommandType.EXECUTION_COMMAND);
    prepareExecutionCommand(cmd);
    String clusterName = internalsConf.get(OptionKeys.APPLICATION_NAME);
    String hostName = getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME);
    cmd.setHostname(hostName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(Command.START.toString());
    cmd.setServiceName(clusterName);
    cmd.setComponentName(roleName);
    cmd.setRole(roleName);
    Map<String, String> hostLevelParams = new TreeMap<>();
    hostLevelParams.put(JAVA_HOME, appConf.getGlobalOptions().getOption(JAVA_HOME, getJDKDir()));
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);

    Map<String, String> roleParams = new TreeMap<>();
    cmd.setRoleParams(roleParams);
    cmd.getRoleParams().put("auto_restart", Boolean.toString(isMarkedAutoRestart));

    Map<String, Map<String, String>> configurations = buildCommandConfigurations(appConf, containerId, roleName, roleGroup);
    cmd.setConfigurations(configurations);
    Map<String, Map<String, String>> componentConfigurations = buildComponentConfigurations(appConf);
    cmd.setComponentConfigurations(componentConfigurations);
    
    if (SliderUtils.isSet(scriptPath)) {
      cmd.setCommandParams(commandParametersSet(scriptPath, timeout, true));
    } else {
      if (startCommand == null) {
        throw new SliderException("Expected START command not found for component " + roleName);
      }
      cmd.setCommandParams(commandParametersSet(startCommand, timeout, true));
      configurations.get("global").put("exec_cmd", startCommand.getExec());
    }

    response.addExecutionCommand(cmd);

    log.debug("command looks like: {}", cmd);
    // With start command, the corresponding command for graceful stop needs to
    // be sent. This will be used when a particular container is lost as per RM,
    // but then the agent is still running and heart-beating to the Slider AM.
    ExecutionCommand cmdStop = new ExecutionCommand(
        AgentCommandType.EXECUTION_COMMAND);
    cmdStop.setTaskId(taskId.get());
    cmdStop.setCommandId(cmdStop.getTaskId() + "-1");
    cmdStop.setHostname(hostName);
    cmdStop.setClusterName(clusterName);
    cmdStop.setRoleCommand(Command.STOP.toString());
    cmdStop.setServiceName(clusterName);
    cmdStop.setComponentName(roleName);
    cmdStop.setRole(roleName);
    Map<String, String> hostLevelParamsStop = new TreeMap<String, String>();
    hostLevelParamsStop.put(JAVA_HOME, appConf.getGlobalOptions()
        .getOption(JAVA_HOME, ""));
    hostLevelParamsStop.put(CONTAINER_ID, containerId);
    cmdStop.setHostLevelParams(hostLevelParamsStop);

    Map<String, String> roleParamsStop = new TreeMap<String, String>();
    cmdStop.setRoleParams(roleParamsStop);
    cmdStop.getRoleParams().put("auto_restart",
                                Boolean.toString(isMarkedAutoRestart));

    if (SliderUtils.isSet(scriptPath)) {
      cmdStop.setCommandParams(commandParametersSet(scriptPath, timeout, true));
    } else {
      if (stopCommand == null) {
        stopCommand = ComponentCommand.getDefaultComponentCommand("STOP");
      }
      cmd.setCommandParams(commandParametersSet(stopCommand, timeout, true));
      configurations.get("global").put("exec_cmd", startCommand.getExec());
    }


    Map<String, Map<String, String>> configurationsStop = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    cmdStop.setConfigurations(configurationsStop);
    response.addExecutionCommand(cmdStop);
  }

  @VisibleForTesting
  protected void addUpgradeCommand(String roleName, String roleGroup, String containerId,
      HeartBeatResponse response, String scriptPath, long timeout)
      throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    ConfTreeOperations internalsConf = getAmState().getInternalsSnapshot();

    ExecutionCommand cmd = new ExecutionCommand(
        AgentCommandType.EXECUTION_COMMAND);
    prepareExecutionCommand(cmd);
    String clusterName = internalsConf.get(OptionKeys.APPLICATION_NAME);
    String hostName = getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME);
    cmd.setHostname(hostName);
    cmd.setClusterName(clusterName);
    cmd.setRoleCommand(Command.UPGRADE.toString());
    cmd.setServiceName(clusterName);
    cmd.setComponentName(roleName);
    cmd.setRole(roleName);
    Map<String, String> hostLevelParams = new TreeMap<String, String>();
    hostLevelParams.put(JAVA_HOME, appConf.getGlobalOptions()
        .getMandatoryOption(JAVA_HOME));
    hostLevelParams.put(CONTAINER_ID, containerId);
    cmd.setHostLevelParams(hostLevelParams);
    cmd.setCommandParams(commandParametersSet(scriptPath, timeout, true));

    Map<String, Map<String, String>> configurations = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    cmd.setConfigurations(configurations);
    response.addExecutionCommand(cmd);
  }
    
  protected void addStopCommand(String roleName, String roleGroup, String containerId,
      HeartBeatResponse response, String scriptPath, long timeout,
      boolean isInUpgradeMode) throws SliderException {
    assert getAmState().isApplicationLive();
    ConfTreeOperations appConf = getAmState().getAppConfSnapshot();
    ConfTreeOperations internalsConf = getAmState().getInternalsSnapshot();

    ExecutionCommand cmdStop = new ExecutionCommand(
        AgentCommandType.EXECUTION_COMMAND);
    cmdStop.setTaskId(taskId.get());
    cmdStop.setCommandId(cmdStop.getTaskId() + "-1");
    String clusterName = internalsConf.get(OptionKeys.APPLICATION_NAME);
    String hostName = getClusterInfoPropertyValue(StatusKeys.INFO_AM_HOSTNAME);
    cmdStop.setHostname(hostName);
    cmdStop.setClusterName(clusterName);
    // Upgrade stop is differentiated by passing a transformed role command -
    // UPGRADE_STOP
    cmdStop.setRoleCommand(Command.transform(Command.STOP, isInUpgradeMode));
    cmdStop.setServiceName(clusterName);
    cmdStop.setComponentName(roleName);
    cmdStop.setRole(roleName);
    Map<String, String> hostLevelParamsStop = new TreeMap<String, String>();
    hostLevelParamsStop.put(JAVA_HOME, appConf.getGlobalOptions()
        .getMandatoryOption(JAVA_HOME));
    hostLevelParamsStop.put(CONTAINER_ID, containerId);
    cmdStop.setHostLevelParams(hostLevelParamsStop);
    cmdStop.setCommandParams(commandParametersSet(scriptPath, timeout, true));

    Map<String, Map<String, String>> configurationsStop = buildCommandConfigurations(
        appConf, containerId, roleName, roleGroup);
    cmdStop.setConfigurations(configurationsStop);
    response.addExecutionCommand(cmdStop);
  }

  protected static String getJDKDir() {
    File javaHome = new File(System.getProperty("java.home")).getParentFile();
    File jdkDirectory = null;
    if (javaHome.getName().contains("jdk")) {
      jdkDirectory = javaHome;
    }
    if (jdkDirectory != null) {
      return jdkDirectory.getAbsolutePath();
    } else {
      return "";
    }
  }

  protected Map<String, String> getAllocatedPorts() {
    return getAllocatedPorts(SHARED_PORT_TAG);
  }

  protected Map<String, Map<String, String>> getComponentInstanceData() {
    return this.componentInstanceData;
  }

  protected Map<String, String> getAllocatedPorts(String containerId) {
    if (!this.allocatedPorts.containsKey(containerId)) {
      synchronized (this.allocatedPorts) {
        if (!this.allocatedPorts.containsKey(containerId)) {
          this.allocatedPorts.put(containerId,
                                  new ConcurrentHashMap<String, String>());
        }
      }
    }
    return this.allocatedPorts.get(containerId);
  }

  private Map<String, Map<String, String>> buildCommandConfigurations(
      ConfTreeOperations appConf, String containerId, String roleName, String roleGroup)
      throws SliderException {

    Map<String, Map<String, String>> configurations =
        new TreeMap<String, Map<String, String>>();
    Map<String, String> tokens = getStandardTokenMap(appConf, roleName, roleGroup);
    tokens.put("${CONTAINER_ID}", containerId);

    Set<String> configs = new HashSet<String>();
    configs.addAll(getApplicationConfigurationTypes(roleGroup));
    configs.addAll(getSystemConfigurationsRequested(appConf));

    for (String configType : configs) {
      addNamedConfiguration(configType, appConf.getGlobalOptions().options,
                            configurations, tokens, containerId, roleName);
      if (appConf.getComponent(roleGroup) != null) {
        addNamedConfiguration(configType, appConf.getComponent(roleGroup).options,
            configurations, tokens, containerId, roleName);
      }
    }

    //do a final replacement of re-used configs
    dereferenceAllConfigs(configurations);

    return configurations;
  }

  protected void dereferenceAllConfigs(Map<String, Map<String, String>> configurations) {
    Map<String, String> allConfigs = new HashMap<String, String>();
    String lookupFormat = "${@//site/%s/%s}";
    for (String configType : configurations.keySet()) {
      Map<String, String> configBucket = configurations.get(configType);
      for (String configName : configBucket.keySet()) {
        allConfigs.put(String.format(lookupFormat, configType, configName), configBucket.get(configName));
      }
    }

    for (String configType : configurations.keySet()) {
      Map<String, String> configBucket = configurations.get(configType);
      for (Map.Entry<String, String> entry: configBucket.entrySet()) {
        String configName = entry.getKey();
        String configValue = entry.getValue();
        for (String lookUpKey : allConfigs.keySet()) {
          if (configValue != null && configValue.contains(lookUpKey)) {
            configValue = configValue.replace(lookUpKey, allConfigs.get(lookUpKey));
          }
        }
        configBucket.put(configName, configValue);
      }
    }
  }

  private Map<String, String> getStandardTokenMap(ConfTreeOperations appConf,
      String componentName, String componentGroup) throws SliderException {
    Map<String, String> tokens = new HashMap<String, String>();
    String nnuri = appConf.get("site.fs.defaultFS");
    tokens.put("${NN_URI}", nnuri);
    tokens.put("${NN_HOST}", URI.create(nnuri).getHost());
    tokens.put("${ZK_HOST}", appConf.get(OptionKeys.ZOOKEEPER_HOSTS));
    tokens.put("${DEFAULT_ZK_PATH}", appConf.get(OptionKeys.ZOOKEEPER_PATH));
    tokens.put("${DEFAULT_DATA_DIR}", getAmState()
        .getInternalsSnapshot()
        .getGlobalOptions()
        .getMandatoryOption(InternalKeys.INTERNAL_DATA_DIR_PATH));
    tokens.put("${JAVA_HOME}", appConf.get(AgentKeys.JAVA_HOME));
    tokens.put("${COMPONENT_NAME}", componentName);
    if (!componentName.equals(componentGroup) && componentName.startsWith(componentGroup)) {
      tokens.put("${COMPONENT_ID}", componentName.substring(componentGroup.length()));
    }
    return tokens;
  }

  @VisibleForTesting
  protected List<String> getSystemConfigurationsRequested(ConfTreeOperations appConf) {
    List<String> configList = new ArrayList<String>();

    String configTypes = appConf.get(AgentKeys.SYSTEM_CONFIGS);
    if (configTypes != null && configTypes.length() > 0) {
      String[] configs = configTypes.split(",");
      for (String config : configs) {
        configList.add(config.trim());
      }
    }

    return new ArrayList<String>(new HashSet<String>(configList));
  }


  @VisibleForTesting
  protected List<String> getApplicationConfigurationTypes(String roleGroup) {
    List<String> configList = new ArrayList<String>();
    configList.add(GLOBAL_CONFIG_TAG);

    List<ConfigFile> configFiles = getMetaInfo().getApplication().getConfigFiles();
    for (ConfigFile configFile : configFiles) {
      log.info("Expecting config type {}.", configFile.getDictionaryName());
      configList.add(configFile.getDictionaryName());
    }
    for (Component component : getMetaInfo().getApplication().getComponents()) {
      if (!component.getName().equals(roleGroup)) {
        continue;
      }
      if (component.getDockerContainers() == null) {
        continue;
      }
      for (DockerContainer container : component.getDockerContainers()) {
        if (container.getConfigFiles() == null) {
          continue;
        }
        for (ConfigFile configFile : container.getConfigFiles()) {
          log.info("Expecting config type {}.", configFile.getDictionaryName());
          configList.add(configFile.getDictionaryName());
        }
      }
    }

    // remove duplicates.  mostly worried about 'global' being listed
    return new ArrayList<String>(new HashSet<String>(configList));
  }

  private void addNamedConfiguration(String configName, Map<String, String> sourceConfig,
                                     Map<String, Map<String, String>> configurations,
                                     Map<String, String> tokens, String containerId,
                                     String roleName) {
    Map<String, String> config = new HashMap<String, String>();
    if (configName.equals(GLOBAL_CONFIG_TAG)) {
      addDefaultGlobalConfig(config, containerId, roleName);
    }
    // add role hosts to tokens
    addRoleRelatedTokens(tokens);
    providerUtils.propagateSiteOptions(sourceConfig, config, configName, tokens);

    //apply any port updates
    if (!this.getAllocatedPorts().isEmpty()) {
      for (String key : config.keySet()) {
        String value = config.get(key);
        String lookupKey = configName + "." + key;
        if (!value.contains(PER_CONTAINER_TAG)) {
          // If the config property is shared then pass on the already allocated value
          // from any container
          if (this.getAllocatedPorts().containsKey(lookupKey)) {
            config.put(key, getAllocatedPorts().get(lookupKey));
          }
        } else {
          if (this.getAllocatedPorts(containerId).containsKey(lookupKey)) {
            config.put(key, getAllocatedPorts(containerId).get(lookupKey));
          }
        }
      }
    }

    //apply defaults only if the key is not present and value is not empty
    if (getDefaultConfigs().containsKey(configName)) {
      log.info("Adding default configs for type {}.", configName);
      for (PropertyInfo defaultConfigProp : getDefaultConfigs().get(configName).getPropertyInfos()) {
        if (!config.containsKey(defaultConfigProp.getName())) {
          if (!defaultConfigProp.getName().isEmpty() &&
              defaultConfigProp.getValue() != null &&
              !defaultConfigProp.getValue().isEmpty()) {
            config.put(defaultConfigProp.getName(), defaultConfigProp.getValue());
          }
        }
      }
    }

    configurations.put(configName, config);
  }

  protected void addRoleRelatedTokens(Map<String, String> tokens) {
    for (Map.Entry<String, Map<String, ClusterNode>> entry : getRoleClusterNodeMapping().entrySet()) {
      String tokenName = entry.getKey().toUpperCase(Locale.ENGLISH) + "_HOST";
      String hosts = StringUtils.join(",", getHostsList(entry.getValue().values(), true));
      tokens.put("${" + tokenName + "}", hosts);
    }
  }

  private Iterable<String> getHostsList(Collection<ClusterNode> values,
                                        boolean hostOnly) {
    List<String> hosts = new ArrayList<String>();
    for (ClusterNode cn : values) {
      hosts.add(hostOnly ? cn.host : cn.host + "/" + cn.name);
    }

    return hosts;
  }

  private void addDefaultGlobalConfig(Map<String, String> config, String containerId, String roleName) {
    config.put("app_log_dir", "${AGENT_LOG_ROOT}");
    config.put("app_pid_dir", "${AGENT_WORK_ROOT}/app/run");
    config.put("app_install_dir", "${AGENT_WORK_ROOT}/app/install");
    config.put("app_input_conf_dir", "${AGENT_WORK_ROOT}/" + SliderKeys.PROPAGATED_CONF_DIR_NAME);
    config.put("app_container_id", containerId);
    config.put("app_container_tag", tags.getTag(roleName, containerId));

    // add optional parameters only if they are not already provided
    if (!config.containsKey("pid_file")) {
      config.put("pid_file", "${AGENT_WORK_ROOT}/app/run/component.pid");
    }
    if (!config.containsKey("app_root")) {
      config.put("app_root", "${AGENT_WORK_ROOT}/app/install");
    }
  }

  private void buildRoleHostDetails(Map<String, MonitorDetail> details) {
    for (Map.Entry<String, Map<String, ClusterNode>> entry :
        getRoleClusterNodeMapping().entrySet()) {
      details.put(entry.getKey() + " Host(s)/Container(s)",
                  new MonitorDetail(getHostsList(entry.getValue().values(), false).toString(), false));
    }
  }
}
