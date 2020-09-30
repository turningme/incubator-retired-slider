#!/usr/bin/env python
"""
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

"""

import os, shutil, sys
from resource_management import *


class MyApp_Component(Script):
    def install(self, env):
        self.install_packages(env)
        pass

    def configure(self, env):
        import params
        env.set_params(params)
        resources = format('{app_root}/../resources')
        work_dir = format("{app_root}")
        for resource in os.listdir(resources):
            full_resource_path = os.path.join(resources, resource)
            if os.path.isfile(full_resource_path) :
                Logger.info("Copying %s to %s" % (full_resource_path, work_dir))
                shutil.copy(full_resource_path, work_dir)
            else:
                Logger.info("Ignoring localized file that doesn't  in : %s" % full_resource_path)

    def start(self, env):
        import params
        env.set_params(params)
        self.configure(env)


        lib_dir = format("{app_root}/lib")
        all_jar_files = "$CLASSPATH";
        for jarpath in os.listdir(lib_dir):
            full_jar_path = os.path.join(lib_dir, jarpath)
            if os.path.isfile(full_jar_path) :
                all_jar_files = full_jar_path+":" + all_jar_files

        all_jar_files = all_jar_files + ":" + format("{app_root}/"+"litestats.jar")

        print("ttttt  " + params.app_root)
        config_file_red = format("{app_root}/{config_file_name}")
        print("sssss  " + config_file_red)

        print("bbbbbb " + all_jar_files)

        print("mmmmm" + '{config_file_name}')
        print("nnnnnn" + '{java_heap}')
        class_path_name = all_jar_files
        main_class = "tv.stickyads.redstats.Main"
        process_cmd = format("{java64_home}/bin/java  -Xmx{java_heap}  -Xms{java_heap} -classpath {class_path_name}  {main_class}  --config-file {config_file_red}")

        Execute(process_cmd,
                logoutput=True,
                wait_for_finish=True,
                pid_file=params.pid_file,
                poll_after=5
                )


    def stop(self, env):
         import params
         env.set_params(params)

         Execute(process_cmd,
            logoutput=True,
            wait_for_finish=True,
            pid_file=params.pid_file,
            poll_after = 15
            )

    def status(self, env):
        import params
        env.set_params(params)
        check_process_status(params.pid_file)

if __name__ == "__main__":
    MyApp_Component().execute()
