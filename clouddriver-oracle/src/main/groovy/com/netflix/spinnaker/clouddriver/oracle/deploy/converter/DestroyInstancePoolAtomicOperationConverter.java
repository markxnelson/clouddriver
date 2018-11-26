package com.netflix.spinnaker.clouddriver.oracle.deploy.converter;

import com.netflix.spinnaker.clouddriver.oracle.deploy.description.DestroyInstancePoolDescription;
import com.netflix.spinnaker.clouddriver.oracle.deploy.op.DestroyInstancePoolAtomicOperation;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import com.netflix.spinnaker.clouddriver.security.AbstractAtomicOperationsCredentialsSupport;
import java.util.Map;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@OracleOperation(AtomicOperations.DESTROY_SERVER_GROUP)
@Component("destroyInstancePool")
public class DestroyInstancePoolAtomicOperationConverter extends AbstractAtomicOperationsCredentialsSupport {

  @Override
  public AtomicOperation convertOperation(Map input) {
    log.info("DestroyInstancePoolAtomicOperationConverter convertOperation, input: $input");
    return new DestroyInstancePoolAtomicOperation(convertDescription(input));
  }

  @Override
  public DestroyInstancePoolDescription convertDescription(Map input) {
    return OracleAtomicOperationConverterHelper.convertDescription(input, this, DestroyInstancePoolDescription.class);
  }
}