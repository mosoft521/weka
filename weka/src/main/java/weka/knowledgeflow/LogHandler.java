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
 *    LogHandler.java
 *    Copyright (C) 2015 University of Waikato, Hamilton, New Zealand
 *
 */

package weka.knowledgeflow;

import weka.core.OptionHandler;
import weka.core.Utils;
import weka.gui.Logger;
import weka.knowledgeflow.steps.Step;
import weka.knowledgeflow.steps.WekaAlgorithmWrapper;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Class that wraps a {@code weka.gui.Logger} and filters log messages
 * according to the set logging level.
 *
 * @author Mark Hall (mhall{[at]}pentaho{[dot]}com)
 * @version $Revision: $
 */
public class LogHandler {

  protected String m_statusMessagePrefix = "";

  protected Logger m_log;

  protected boolean m_status;

  protected LoggingLevel m_levelToLogAt = LoggingLevel.BASIC;

  public LogHandler(Step source) {
    m_status = true;
    String prefix = (source != null ? source.getName() : "Unknown") + "$";

    prefix += (source != null ? source.hashCode() : 1) + "|";
    if (source instanceof WekaAlgorithmWrapper) {
      Object wrappedAlgo =
        ((WekaAlgorithmWrapper) source).getWrappedAlgorithm();
      if (wrappedAlgo instanceof OptionHandler) {
        prefix +=
          Utils.joinOptions(((OptionHandler) wrappedAlgo).getOptions()) + "|";
      }
    }

    m_statusMessagePrefix = prefix;
    if (source != null) {
      m_log = ((StepManagerImpl) source.getStepManager()).getLog();
      setLoggingLevel(((StepManagerImpl) source.getStepManager())
        .getLoggingLevel());
    }
  }

  public LogHandler(Logger log) {
    this(log, true);
  }

  public LogHandler(Logger log, boolean status) {
    m_log = log;
    m_status = status;
  }

  public static String stackTraceToString(Throwable throwable) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    throwable.printStackTrace(pw);

    return sw.toString();
  }

  public void setLogger(Logger log) {
    m_log = log;
  }

  public Logger getLog() {
    return m_log;
  }

  public LoggingLevel getLoggingLevel() {
    return m_levelToLogAt;
  }

  public void setLoggingLevel(LoggingLevel level) {
    m_levelToLogAt = level;
  }

  public void logLow(String message) {
    log(message, LoggingLevel.LOW);
  }

  public void logBasic(String message) {
    log(message, LoggingLevel.BASIC);
  }

  public void logDetailed(String message) {
    log(message, LoggingLevel.DETAILED);
  }

  public void logDebug(String message) {
    log(message, LoggingLevel.DEBUGGING);
  }

  public void logWarning(String message) {
    log(message, LoggingLevel.WARNING);
    if (m_status) {
      statusMessage( "WARNING: " + message );
    }
  }

  public void logError(String message, Exception cause) {
    log(message, LoggingLevel.ERROR, cause);
    if (m_status) {
      statusMessage( "ERROR: " + message );
    }
  }

  public void statusMessage(String message) {
    if (m_log != null) {
      m_log.statusMessage(statusMessagePrefix() + message);
    }
  }

  public void log(String message, LoggingLevel messageLevel) {
    log(message, messageLevel, null);
  }

  protected void
    log(String message, LoggingLevel messageLevel, Throwable cause) {
    if (messageLevel == LoggingLevel.WARNING
      || messageLevel == LoggingLevel.ERROR
      || messageLevel.ordinal() <= m_levelToLogAt.ordinal()) {
      String logMessage =
        "[" + messageLevel.toString() + "] " + statusMessagePrefix() + message;
      if (cause != null) {
        logMessage += "\n" + stackTraceToString(cause);
      }
      if (m_log != null) {
        m_log.logMessage(logMessage);
        if (messageLevel == LoggingLevel.ERROR
          || messageLevel == LoggingLevel.WARNING) {
          statusMessage(messageLevel.toString() + " (see log for details)");
        }
      } else {
        System.err.println(logMessage);
      }
    }
  }

  private String statusMessagePrefix() {
    return m_statusMessagePrefix;
  }
}
