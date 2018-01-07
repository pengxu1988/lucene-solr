/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.cloud;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.solr.core.CoreDescriptor;
import org.apache.solr.core.SolrCore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoveringCoreTermWatcher implements ZkShardTerms.CoreTermWatcher {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final SolrCore solrCore;
  // used to prevent the case when term of other replicas get changed, we won't redo recovery
  // the idea here is with a specific term of a replica, we only do recovery one
  private final AtomicLong lastTermDoRecovery;

  public RecoveringCoreTermWatcher(SolrCore solrCore) {
    this.solrCore = solrCore;
    this.lastTermDoRecovery = new AtomicLong(-1);
  }

  @Override
  public boolean onTermChanged(ZkShardTerms.Terms terms) {
    if (solrCore.isClosed()) {
      return false;
    }
    try {
      String coreNodeName = solrCore.getCoreDescriptor().getCloudDescriptor().getCoreNodeName();
      if (terms.canBecomeLeader(coreNodeName)) return true;
      if (lastTermDoRecovery.get() < terms.getTerm(coreNodeName)) {
        log.info("Start recovery on {} because core's term is less than leader's term", coreNodeName);
        lastTermDoRecovery.set(terms.getTerm(coreNodeName));
        solrCore.getUpdateHandler().getSolrCoreState().doRecovery(solrCore.getCoreContainer(), solrCore.getCoreDescriptor());
      }
    } catch (NullPointerException e) {
      log.info("NPE when getting coreNodeName, hence do not start recovery process");
    }
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RecoveringCoreTermWatcher that = (RecoveringCoreTermWatcher) o;

    return solrCore.equals(that.solrCore);
  }

  @Override
  public int hashCode() {
    return solrCore.hashCode();
  }
}
