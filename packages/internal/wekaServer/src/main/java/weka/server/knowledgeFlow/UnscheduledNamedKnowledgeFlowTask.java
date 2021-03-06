/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 *    UnscheduledNamedKnowledgeFlowTask.java
 *    Copyright (C) 2016 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.server.knowledgeFlow;

import weka.core.Environment;
import weka.core.LogHandler;
import weka.experiment.TaskStatusInfo;
import weka.gui.Logger;
import weka.knowledgeflow.Flow;
import weka.knowledgeflow.FlowRunner;
import weka.knowledgeflow.StepManagerImpl;
import weka.knowledgeflow.steps.DataCollector;
import weka.server.Legacy;
import weka.server.NamedTask;
import weka.server.WekaTaskMap;
import weka.server.logging.ServerLogger;

import java.io.File;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An unscheduled knowledge flow task for the new Knowledge Flow implementation
 * 
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class UnscheduledNamedKnowledgeFlowTask implements NamedTask,
  LogHandler, Serializable {

  private static final long serialVersionUID = -3502514606185943633L;

  /** The name of the task */
  protected String m_name;

  /** The flow (in JSON format) to execute */
  protected String m_flowJSON;

  /**
   * True if start points in the flow are to be launched sequentially rather
   * than in parallel
   */
  protected boolean m_sequential;

  /** Log */
  protected transient ServerLogger m_log;

  /**
   * flow parameters (i.e. environment variables set on the client side that
   * need to be set on the server side for the flow)
   */
  protected Map<String, String> m_parameters;

  /** Status info */
  protected TaskStatusInfo m_result = new TaskStatusInfo();

  /** The FlowRunner used to actually execute the flow */
  protected FlowRunner m_fr;

  /** File to persist the results to */
  protected File m_persistedResult;

  /**
   * Constructor
   * 
   * @param name name of this named task
   * @param jsonFlow the flow that will be executed, in JSON format
   * @param sequential true if the start points of the flow should be launched
   *          sequentially
   * @param parameters parameters/environment variable settings to use
   */
  public UnscheduledNamedKnowledgeFlowTask(String name, String jsonFlow,
    boolean sequential, Map<String, String> parameters) {
    m_name = name;
    m_flowJSON = jsonFlow;
    m_sequential = sequential;
    m_parameters = parameters;
  }

  /**
   * Set the log to use
   * 
   * @param log the log to use
   */
  @Override
  public void setLog(Logger log) {
    m_log = (ServerLogger) log;
  }

  /**
   * Get the log
   * 
   * @return the log in use
   */
  @Override
  public Logger getLog() {
    return m_log;
  }

  /**
   * Attempt to stop execution
   */
  @Override
  public void stop() {
    if (m_fr != null) {
      m_fr.stopProcessing();
      m_result.setExecutionStatus(WekaTaskMap.WekaTaskEntry.STOPPED);
    }
  }

  /**
   * Get a map of parameters/environment variable settings
   * 
   * @return parameters/env vars
   */
  public Map<String, String> getParameters() {
    return m_parameters;
  }

  /**
   * Return true if the start points for the flow are to be launched
   * sequentially rather than in parallel
   * 
   * @return true if start points will be launched sequentially
   */
  public boolean getSequentialExecution() {
    return m_sequential;
  }

  /**
   * Get the flow in JSON format
   *
   * @return the flow in JSON format
   */
  public String getFlowJSON() {
    return m_flowJSON;
  }

  /**
   * Set the name of this task
   *
   * @param name the name of this task
   */
  @Override
  public void setName(String name) {
    m_name = name;
  }

  /**
   * Get the name of this task
   *
   * @return the name of this task
   */
  @Override
  public String getName() {
    return m_name;
  }

  /**
   * Free memory
   */
  @Override
  public void freeMemory() {

  }

  /**
   * Persist resources
   */
  @Override
  public void persistResources() {

  }

  /**
   * Load resources
   */
  @Override
  public void loadResources() {

  }

  /**
   * Load the result generated by this task
   *
   * @throws Exception if the result is not available
   */
  @Override
  public void loadResult() throws Exception {
    if (m_persistedResult == null || !m_persistedResult.exists()) {
      throw new Exception("Result file seems to have disappeared!");
    }

    m_result.setTaskResult(Legacy.loadResult(m_persistedResult));
  }

  /**
   * Purge results
   */
  @Override
  public void purge() {
    if (m_persistedResult != null && m_persistedResult.exists()) {
      if (!m_persistedResult.delete()) {
        m_persistedResult.deleteOnExit();
      }
    }
  }

  /**
   * Execute this task
   */
  @Override
  public void execute() {
    ObjectOutputStream oos = null;
    try {
      Flow toExecute = Flow.JSONToFlow(m_flowJSON);

      m_fr = new FlowRunner();
      m_fr.setFlow(toExecute);
      m_fr.setLogger(m_log);
      m_fr.setLaunchStartPointsSequentially(m_sequential);
      Environment env = new Environment();
      if (m_parameters != null && m_parameters.size() > 0) {
        m_log.logMessage("Setting parameters for the flow");

        for (String key : m_parameters.keySet()) {
          String value = m_parameters.get(key);
          env.addVariable(key, value);
        }
      }
      m_fr.getExecutionEnvironment().setEnvironmentVariables(env);

      m_result.setExecutionStatus(TaskStatusInfo.PROCESSING);
      m_fr.run();
      m_fr.waitUntilFinished();

      if (m_result.getExecutionStatus() != WekaTaskMap.WekaTaskEntry.STOPPED) {
        Map<String, Object> results = new HashMap<String, Object>();
        List<StepManagerImpl> steps = toExecute.getSteps();
        for (StepManagerImpl smi : steps) {
          if (smi.getManagedStep() instanceof DataCollector) {
            Object data = ((DataCollector) smi.getManagedStep()).retrieveData();
            if (data != null) {
              results.put(smi.getName(), data);
            }
          }
        }
        if (results.size() > 0) {
          m_result.setTaskResult(results);
        }

        try {
          m_persistedResult = Legacy.persistResult(results);

          // successfully saved result - now save memory
          m_result.setTaskResult(null);
        } catch (Exception e) {
          m_persistedResult = null;
        }

        m_result.setExecutionStatus(TaskStatusInfo.FINISHED);
      }

    } catch (Exception ex) {
      m_result.setExecutionStatus(TaskStatusInfo.FAILED);

      // log this
      StringWriter sr = new StringWriter();
      PrintWriter pr = new PrintWriter(sr);
      ex.printStackTrace(pr);
      pr.flush();
      m_log.logMessage(ex.getMessage() + "\n" + sr.getBuffer().toString());
      pr.close();
    } finally {
      m_fr = null;
    }
  }

  /**
   * Get the status of this task
   *
   * @return the status of this task
   */
  @Override
  public TaskStatusInfo getTaskStatus() {
    // set up the status message by pulling current logging info
    StringBuffer temp = new StringBuffer();
    List<String> statusCache = m_log.getStatusCache();
    List<String> logCache = m_log.getLogCache();

    temp.append("@@@ Status messages:\n\n");
    for (String status : statusCache) {
      String entry = status + "\n";
      temp.append(entry);
    }
    temp.append("\n@@@ Log messages:\n\n");
    for (String log : logCache) {
      String entry = log + "\n";
      temp.append(entry);
    }
    m_result.setStatusMessage(temp.toString());

    return m_result;
  }
}
