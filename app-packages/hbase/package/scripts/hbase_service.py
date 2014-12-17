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

from resource_management import *

def hbase_service(
  name,
  action = 'start'): # 'start' or 'stop' or 'status'
    
    import params
  
    role = name
    cmd = format("{daemon_script} --config {conf_dir}")
    pid_file = format("{pid_dir}/hbase-{hbase_user}-{role}.pid")
    
    daemon_cmd = None
    no_op_test = None
    
    if action == 'start':
      daemon_cmd = format("env HBASE_IDENT_STRING={hbase_user} {cmd} start {role}")
      if name == 'rest':
        daemon_cmd = format("{daemon_cmd} -p {rest_port}")
      elif name == 'thrift':
        queue = ""
        if not thrift_queue == "":
          queue = " -q {thrift_queue}"
        workers = ""
        if not thrift_workers == "":
          workers = " -w {thrift_workers}"
        compact = ""
        if not thrift_compact == "":
          compact = " -c"
        framed = ""
        if not thrift_framed == "":
          framed = " -f"
        daemon_cmd = format("{daemon_cmd} -p {thrift_port}" + queue + workers + compact + framed)
      elif name == 'thrift2':
        daemon_cmd = format("{daemon_cmd} -p {thrift2_port}")
      no_op_test = format("ls {pid_file} >/dev/null 2>&1 && ps `cat {pid_file}` >/dev/null 2>&1")
    elif action == 'stop':
      daemon_cmd = format("env HBASE_IDENT_STRING={hbase_user} {cmd} stop {role} && rm -f {pid_file}")

    if daemon_cmd is not None:
      Execute ( daemon_cmd,
        not_if = no_op_test
      )
