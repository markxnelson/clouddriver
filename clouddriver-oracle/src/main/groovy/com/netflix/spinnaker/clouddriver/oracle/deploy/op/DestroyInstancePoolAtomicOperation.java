package com.netflix.spinnaker.clouddriver.oracle.deploy.op;

import com.netflix.spinnaker.clouddriver.data.task.Task;
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository;
import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyInstancePoolDescription;
import com.netflix.spinnaker.clouddriver.oracle.provider.view.OracleClusterProvider;
import com.netflix.spinnaker.clouddriver.oracle.service.servergroup.OracleServerGroupService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.oracle.bmc.core.ComputeManagementClient;
import com.oracle.bmc.core.requests.ListInstancePoolsRequest;
import com.oracle.bmc.core.requests.TerminateInstancePoolRequest;
import com.oracle.bmc.core.responses.ListInstancePoolsResponse;
import com.oracle.bmc.core.responses.TerminateInstancePoolResponse;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public class DestroyInstancePoolAtomicOperation implements AtomicOperation<Void> {

  private final DestroyInstancePoolDescription desc;

  private static final String DESTROY_PHASE = "DESTROY_SERVER_GROUP";

  private static Task task() {
    return TaskRepository.threadLocalTask.get();
  }

  @Autowired
  OracleServerGroupService oracleServerGroupService;

  @Autowired
  OracleClusterProvider clusterProvider;

  public DestroyInstancePoolAtomicOperation(DestroyInstancePoolDescription description) {
    this.desc = description;
  }

  @Override
  public Void operate(List priorOutputs) {
    ComputeManagementClient client = desc.getCredentials().getComputeManagementClient();
    String compartmentId = desc.getCredentials().getCompartmentId();
    if (desc.getInstancePoolId() != null) {
      String instancePoolId = desc.getInstancePoolId();
      TerminateInstancePoolRequest termReq =
          TerminateInstancePoolRequest.builder().instancePoolId(instancePoolId).build();
      TerminateInstancePoolResponse termRes = client.terminateInstancePool(termReq);
//      System.out.println("~~~ TerminateInstancePool... " + termRes.getOpcRequestId());
    } else {
      ListInstancePoolsRequest listReq = ListInstancePoolsRequest.builder()
          .compartmentId(compartmentId).build();//TODO: displayName(desc.getServerGroupName())
      String sgName = desc.getServerGroupName();
      ListInstancePoolsResponse listRes = client.listInstancePools(listReq);
      listRes.getItems().forEach(instancePool -> {
        if (sgName != null && sgName.equals(instancePool.getDisplayName())) {
          task().updateStatus(DESTROY_PHASE, "TerminateInstancePoolRequest " + instancePool.getDisplayName());
          TerminateInstancePoolResponse termRes = client.terminateInstancePool(
          TerminateInstancePoolRequest.builder().instancePoolId(instancePool.getId()).build());
          task().updateStatus(DESTROY_PHASE, "TerminateInstancePoolResponse " + termRes.getOpcRequestId());
        }
      });
    }
    return null;
  }
}
