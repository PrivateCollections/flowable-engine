/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.activiti.engine.impl.cmd;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.activiti.engine.common.api.ActivitiException;
import org.activiti.engine.common.api.ActivitiObjectNotFoundException;
import org.activiti.engine.compatibility.Activiti5CompatibilityHandler;
import org.activiti.engine.delegate.event.ActivitiEngineEventType;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.EventSubscriptionEntityManager;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.SignalEventSubscriptionEntity;
import org.activiti.engine.impl.util.Activiti5Util;
import org.activiti.engine.runtime.Execution;

/**
 * @author Joram Barrez
 * @author Tijs Rademakers
 */
public class SignalEventReceivedCmd implements Command<Void> {

  protected final String eventName;
  protected final String executionId;
  protected final Map<String, Object> payload;
  protected final boolean async;
  protected String tenantId;

  public SignalEventReceivedCmd(String eventName, String executionId, Map<String, Object> processVariables, String tenantId) {
    this.eventName = eventName;
    this.executionId = executionId;
    if (processVariables != null) {
      this.payload = new HashMap<String, Object>(processVariables);
      
    } else {
      this.payload = null;
    }
    this.async = false;
    this.tenantId = tenantId;
  }

  public SignalEventReceivedCmd(String eventName, String executionId, boolean async, String tenantId) {
    this.eventName = eventName;
    this.executionId = executionId;
    this.async = async;
    this.payload = null;
    this.tenantId = tenantId;
  }

  public Void execute(CommandContext commandContext) {
    
    List<SignalEventSubscriptionEntity> signalEvents = null;

    EventSubscriptionEntityManager eventSubscriptionEntityManager = commandContext.getEventSubscriptionEntityManager();
    if (executionId == null) {
      signalEvents = eventSubscriptionEntityManager.findSignalEventSubscriptionsByEventName(eventName, tenantId);
    } else {

      ExecutionEntity execution = commandContext.getExecutionEntityManager().findById(executionId);

      if (execution == null) {
        throw new ActivitiObjectNotFoundException("Cannot find execution with id '" + executionId + "'", Execution.class);
      }

      if (execution.isSuspended()) {
        throw new ActivitiException("Cannot throw signal event '" + eventName + "' because execution '" + executionId + "' is suspended");
      }
      
      if (Activiti5Util.isActiviti5ProcessDefinitionId(commandContext, execution.getProcessDefinitionId())) {
        Activiti5CompatibilityHandler activiti5CompatibilityHandler = Activiti5Util.getActiviti5CompatibilityHandler(); 
        activiti5CompatibilityHandler.signalEventReceived(eventName, executionId, payload, async, tenantId);
        return null;
      }

      signalEvents = eventSubscriptionEntityManager.findSignalEventSubscriptionsByNameAndExecution(eventName, executionId);

      if (signalEvents.isEmpty()) {
        throw new ActivitiException("Execution '" + executionId + "' has not subscribed to a signal event with name '" + eventName + "'.");
      }
    }

    for (SignalEventSubscriptionEntity signalEventSubscriptionEntity : signalEvents) {
      // We only throw the event to globally scoped signals.
      // Process instance scoped signals must be thrown within the process itself
      if (signalEventSubscriptionEntity.isGlobalScoped()) {
        
        if (executionId == null && Activiti5Util.isActiviti5ProcessDefinitionId(commandContext, signalEventSubscriptionEntity.getProcessDefinitionId())) {
          Activiti5CompatibilityHandler activiti5CompatibilityHandler = Activiti5Util.getActiviti5CompatibilityHandler(); 
          activiti5CompatibilityHandler.signalEventReceived(signalEventSubscriptionEntity, payload, async);
          
        } else {
          Context.getProcessEngineConfiguration().getEventDispatcher().dispatchEvent(
              ActivitiEventBuilder.createSignalEvent(ActivitiEngineEventType.ACTIVITY_SIGNALED, signalEventSubscriptionEntity.getActivityId(), eventName, 
                  payload, signalEventSubscriptionEntity.getExecutionId(), signalEventSubscriptionEntity.getProcessInstanceId(), 
                  signalEventSubscriptionEntity.getProcessDefinitionId()));
          
          eventSubscriptionEntityManager.eventReceived(signalEventSubscriptionEntity, payload, async);
        }
      }
    }

    return null;
  }

}
