/*
 * Copyright (C) 2015-2016 Denis Forveille titou10.titou10@gmail.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.titou10.jtb.script;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.jms.JMSException;
import javax.jms.Message;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.titou10.jtb.config.ConfigManager;
import org.titou10.jtb.jms.model.JTBConnection;
import org.titou10.jtb.jms.model.JTBDestination;
import org.titou10.jtb.jms.model.JTBMessage;
import org.titou10.jtb.jms.model.JTBMessageTemplate;
import org.titou10.jtb.jms.model.JTBSession;
import org.titou10.jtb.jms.model.JTBSessionClientType;
import org.titou10.jtb.script.ScriptStepResult.ExectionActionCode;
import org.titou10.jtb.script.gen.DataFile;
import org.titou10.jtb.script.gen.GlobalVariable;
import org.titou10.jtb.script.gen.Script;
import org.titou10.jtb.script.gen.Step;
import org.titou10.jtb.script.gen.StepKind;
import org.titou10.jtb.template.TemplatesUtils;
import org.titou10.jtb.util.Constants;
import org.titou10.jtb.util.Utils;
import org.titou10.jtb.variable.VariablesUtils;
import org.titou10.jtb.variable.gen.Variable;

/**
 * Script Execution Engine
 * 
 * @author Denis Forveille
 *
 */
public class ScriptExecutionEngine {

   private static final Logger log                  = LoggerFactory.getLogger(ScriptExecutionEngine.class);

   private static final String MAX_MESSAGES_REACHED = "MAX_MESSAGES_REACHED";

   private IEventBroker        eventBroker;

   private ConfigManager       cm;
   private Script              script;

   private boolean             clearLogsBeforeExecution;
   private int                 nbMessagePost;
   private int                 nbMessageMax;

   public ScriptExecutionEngine(IEventBroker eventBroker, ConfigManager cm, Script script) {
      this.script = script;
      this.cm = cm;
      this.eventBroker = eventBroker;

      this.clearLogsBeforeExecution = cm.getPreferenceStore().getBoolean(Constants.PREF_CLEAR_LOGS_EXECUTION);
   }

   public void executeScript(final boolean simulation, int nbMessageMax) {
      log.debug("executeScript '{}'. simulation? {}", script.getName(), simulation);

      // BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
      // @Override
      // public void run() {
      // executeScriptInBackground(simulation);
      // }
      // });

      this.nbMessagePost = 0;
      this.nbMessageMax = nbMessageMax == 0 ? Integer.MAX_VALUE : nbMessageMax;

      ProgressMonitorDialog progressDialog = new ProgressMonitorDialogPrimaryModal(Display.getCurrent().getActiveShell());
      try {
         progressDialog.run(true, true, new IRunnableWithProgress() {

            @Override
            public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
               executeScriptInBackground(monitor, simulation);
               monitor.done();
            }
         });
      } catch (InterruptedException e) {
         String msg = e.getMessage();
         if ((msg != null) && (msg.equals(MAX_MESSAGES_REACHED))) {
            log.info("Max messages reached");
            updateLog(ScriptStepResult.createScriptMaxReached(nbMessagePost, simulation));
         } else {
            log.info("Process has been cancelled by user");
            updateLog(ScriptStepResult.createScriptCancelled(nbMessagePost, simulation));
         }
         return;
      } catch (InvocationTargetException e) {
         Throwable t = Utils.getCause(e);
         log.error("Exception occured ", t);
         updateLog(ScriptStepResult.createValidationExceptionFail(ExectionActionCode.SCRIPT, "An unexpected problem occured", t));
         return;
      }
   }

   private void executeScriptInBackground(IProgressMonitor monitor, boolean simulation) throws InterruptedException {
      log.debug("executeScriptInBackground '{}'. simulation? {}", script.getName(), simulation);

      // Clear logs is the option is set in preferences
      if (clearLogsBeforeExecution) {
         eventBroker.send(Constants.EVENT_CLEAR_EXECUTION_LOG, "noUse");
      }

      Random r = new Random(System.nanoTime());

      List<Step> steps = script.getStep();
      List<GlobalVariable> globalVariables = script.getGlobalVariable();

      updateLog(ScriptStepResult.createScriptStart(simulation));

      // Create runtime objects from steps
      int totalWork = 6;
      List<RuntimeStep> runtimeSteps = new ArrayList<>(steps.size());
      for (Step step : steps) {
         runtimeSteps.add(new RuntimeStep(step));
         totalWork += step.getIterations(); // bad? does not take into account data files...
      }

      if (simulation) {
         monitor.beginTask("Executing Script (Simulation)", totalWork);
      } else {
         monitor.beginTask("Executing Script", totalWork);
      }

      // Gather templates used in the script and validate their existence
      try {
         monitor.subTask("Validating Templates...");
         List<IFile> allTemplates = TemplatesUtils.getAllTemplatesIFiles(cm.getTemplateFolder());
         for (RuntimeStep runtimeStep : runtimeSteps) {
            Step step = runtimeStep.getStep();
            if (step.getKind() == StepKind.REGULAR) {

               if (step.isFolder()) {
                  // Get IFolder from the templateName
                  IFolder templateBaseFolder = cm.getTemplateFolder();
                  IFolder templateFolder = templateBaseFolder.getFolder(step.getTemplateName());
                  if (templateFolder.exists()) {
                     // Read all templates from folder if the templateName is a template folder name...
                     List<IFile> templatesInFolder = TemplatesUtils.getAllTemplatesIFiles(templateFolder);
                     for (IFile iFile : templatesInFolder) {
                        runtimeStep.addJTBMessageTemplate(TemplatesUtils.readTemplate(iFile),
                                                          step.getTemplateName() + "/" + iFile.getName());
                     }
                  } else {
                     updateLog(ScriptStepResult.createValidationTemplateFail(step.getTemplateName()));
                     return;
                  }

               } else {
                  // Validate and read Template
                  String templateName = step.getTemplateName();
                  JTBMessageTemplate t = null;
                  for (IFile iFile : allTemplates) {
                     String iFileName = "/" + iFile.getProjectRelativePath().removeFirstSegments(1).toPortableString();
                     if (iFileName.equals(templateName)) {
                        t = TemplatesUtils.readTemplate(iFile);
                        break;
                     }
                  }
                  if (t == null) {
                     updateLog(ScriptStepResult.createValidationTemplateFail(templateName));
                     return;
                  }
                  runtimeStep.addJTBMessageTemplate(t, templateName);
               }
            }
         }

         monitor.worked(1);
         if (monitor.isCanceled()) {
            monitor.done();
            throw new InterruptedException();
         }

      } catch (Exception e) {
         updateLog(ScriptStepResult.createValidationExceptionFail(ExectionActionCode.TEMPLATE,
                                                                  "A problem occured while validating templates",
                                                                  e));
         return;
      }

      // Gather sessions used in the script and validate their existence
      monitor.subTask("Validating Sessions...");
      Map<String, JTBSession> jtbSessionsUsed = new HashMap<>(steps.size());
      for (RuntimeStep runtimeStep : runtimeSteps) {
         Step step = runtimeStep.getStep();
         if (step.getKind() == StepKind.REGULAR) {
            String sessionName = step.getSessionName();
            JTBSession jtbSession = jtbSessionsUsed.get(sessionName);
            if (jtbSession == null) {
               jtbSession = cm.getJTBSessionByName(sessionName);
               if (jtbSession == null) {
                  updateLog(ScriptStepResult.createValidationSessionFail(sessionName));
                  return;
               }
               jtbSessionsUsed.put(sessionName, jtbSession);
               log.debug("Session with name '{}' added to the list of sessions used in the script", sessionName);
            }
            runtimeStep.setJtbConnection(jtbSession.getJTBConnection(JTBSessionClientType.SCRIPT_EXEC));
         }
      }
      monitor.worked(1);
      if (monitor.isCanceled()) {
         monitor.done();
         throw new InterruptedException();
      }

      // Check that global variables still exist
      monitor.subTask("Validating Global Variables...");

      List<Variable> cmVariables = cm.getVariables();
      Map<String, String> globalVariablesValues = new HashMap<>(globalVariables.size());

      loop: for (GlobalVariable globalVariable : globalVariables) {
         for (Variable v : cmVariables) {
            if (v.getName().equals(globalVariable.getName())) {

               // Generate a value for the variable if no defaut is provides
               String val = globalVariable.getConstantValue();
               if (val == null) {
                  globalVariablesValues.put(v.getName(), VariablesUtils.resolveVariable(r, v));
               } else {
                  globalVariablesValues.put(v.getName(), val);
               }

               continue loop;
            }
         }

         // The current variable does not exist
         log.warn("Global Variable '{}' does not exist", globalVariable.getName());
         updateLog(ScriptStepResult.createValidationVariableFail(globalVariable.getName()));
         return;
      }
      monitor.worked(1);
      if (monitor.isCanceled()) {
         monitor.done();
         throw new InterruptedException();
      }

      // Check that data file exist and parse variable names
      monitor.subTask("Validating Data Files...");

      for (RuntimeStep runtimeStep : runtimeSteps) {
         Step step = runtimeStep.getStep();
         if (step.getKind() == StepKind.REGULAR) {
            String variablePrefix = step.getVariablePrefix();
            if (variablePrefix != null) {
               DataFile dataFile = ScriptsUtils.findDataFileByVariablePrefix(script, variablePrefix);
               if (dataFile == null) {
                  log.warn("Data File with variablePrefix '{}' does not exist", variablePrefix);
                  updateLog(ScriptStepResult.createValidationDataFileFail2(variablePrefix));
                  return;
               }
               String fileName = dataFile.getFileName();

               File f = new File(fileName);
               if (!(f.exists())) {
                  // The Data File does not exist
                  log.warn("Data File  with variablePrefix {} has a file Name '{}' does not exist", variablePrefix, fileName);
                  updateLog(ScriptStepResult.createValidationDataFileFail(fileName));
                  return;
               }
               runtimeStep.setDataFile(dataFile);

               String[] varNames = dataFile.getVariableNames().split(","); // TODO Hardcoded...
               log.debug("Variable names {} found in Data File '{}'", varNames, fileName);
               for (int i = 0; i < varNames.length; i++) {
                  String varName = varNames[i];
                  varNames[i] = dataFile.getVariablePrefix() + "." + varName;
               }
               runtimeStep.setVarNames(varNames);
            }
         }
      }

      monitor.worked(1);
      if (monitor.isCanceled()) {
         monitor.done();
         throw new InterruptedException();
      }

      // Connect to sessions if they are not connected
      monitor.subTask("Opening Sessions...");
      for (Entry<String, JTBSession> e : jtbSessionsUsed.entrySet()) {
         String sessionName = e.getKey();
         JTBSession jtbSession = e.getValue();
         JTBConnection jtbConnection = jtbSession.getJTBConnection(JTBSessionClientType.SCRIPT_EXEC);

         updateLog(ScriptStepResult.createSessionConnectStart(sessionName));
         if (jtbConnection.isConnected()) {
            updateLog(ScriptStepResult.createSessionConnectSuccess());
         } else {
            log.debug("Connecting to {}", sessionName);
            try {
               jtbConnection.connectOrDisconnect();
               updateLog(ScriptStepResult.createSessionConnectSuccess());

               // Refresh Session Browser
               eventBroker.send(Constants.EVENT_REFRESH_SESSION_BROWSER, false);

            } catch (Exception e1) {
               updateLog(ScriptStepResult.createSessionConnectFail(sessionName, e1));
               return;
            }
         }
      }
      monitor.worked(1);
      if (monitor.isCanceled()) {
         monitor.done();
         throw new InterruptedException();
      }

      // Resolve Destination Name
      monitor.subTask("Validating Destinations...");
      for (RuntimeStep runtimeStep : runtimeSteps) {
         Step step = runtimeStep.getStep();
         if (step.getKind() == StepKind.REGULAR) {
            JTBConnection jtbConnection = runtimeStep.getJtbConnection();
            JTBDestination jtbDestination = jtbConnection.getJTBDestinationByName(step.getDestinationName());
            if (jtbDestination == null) {
               updateLog(ScriptStepResult.createValidationDestinationFail(step.getDestinationName()));
               return;
            }
            runtimeStep.setJtbDestination(jtbDestination);
         }
      }
      monitor.worked(1);
      if (monitor.isCanceled()) {
         monitor.done();
         throw new InterruptedException();
      }

      // Execute steps
      for (RuntimeStep runtimeStep : runtimeSteps) {
         switch (runtimeStep.getStep().getKind()) {
            case PAUSE:
               executePause(monitor, simulation, runtimeStep);
               break;

            case REGULAR:

               // Parse templates to replace variables names by global variables values
               for (JTBMessageTemplate t : runtimeStep.getJtbMessageTemplates()) {

                  String payload = t.getPayloadText();
                  if (payload != null) {
                     for (Entry<String, String> v : globalVariablesValues.entrySet()) {
                        payload = payload.replaceAll(VariablesUtils.buildVariableReplaceName(v.getKey()), v.getValue());
                     }
                  }

                  t.setPayloadText(payload);
               }

               try {
                  executeRegular(monitor, simulation, runtimeStep);
               } catch (JMSException | IOException e) {
                  updateLog(ScriptStepResult.createStepFail(runtimeStep.getJtbDestination().getName(), e));
                  return;
               }
               break;

            default:
               break;
         }
      }

      updateLog(ScriptStepResult.createScriptSuccess(nbMessagePost, simulation));
   }

   // -------
   // Helpers
   // -------

   private void executeRegular(IProgressMonitor monitor,
                               boolean simulation,
                               RuntimeStep runtimeStep) throws JMSException, InterruptedException, IOException {
      log.debug("executeRegular. Simulation? {}", simulation);

      Map<String, String> dataFileVariables = new HashMap<>();

      int n = 0;
      for (JTBMessageTemplate t : runtimeStep.getJtbMessageTemplates()) {

         // If the dataFile is present, load the lines..
         DataFile dataFile = runtimeStep.getDataFile();
         String templateName = runtimeStep.getTemplateNames().get(n++);
         if (dataFile == null) {
            executeRegular2(monitor, simulation, runtimeStep, t, templateName, dataFileVariables);
         } else {
            String[] varNames = runtimeStep.getVarNames();

            BufferedReader reader = Files.newBufferedReader(Paths.get(dataFile.getFileName()), Charset.defaultCharset());
            String line = null;
            while ((line = reader.readLine()) != null) {
               dataFileVariables.clear();

               // Parse and setup line Variables
               String[] values = line.split(Pattern.quote(dataFile.getDelimiter()));
               String value;
               for (int i = 0; i < varNames.length; i++) {
                  String varName = varNames[i];
                  if (i < values.length) {
                     value = values[i];
                  } else {
                     value = "";
                  }
                  dataFileVariables.put(varName, value);
               }

               // Execute Step
               executeRegular2(monitor, simulation, runtimeStep, t, templateName, dataFileVariables);
            }
         }
      }
   }

   private void executeRegular2(IProgressMonitor monitor,
                                boolean simulation,
                                RuntimeStep runtimeStep,
                                JTBMessageTemplate t,
                                String templateName,
                                Map<String, String> dataFileVariables) throws JMSException, InterruptedException {

      Step step = runtimeStep.getStep();
      JTBConnection jtbConnection = runtimeStep.getJtbConnection();
      JTBDestination jtbDestination = runtimeStep.getJtbDestination();

      for (int i = 0; i < step.getIterations(); i++) {

         monitor.subTask(runtimeStep.toString());

         JTBMessageTemplate jtbMessageTemplate = JTBMessageTemplate.deepClone(t);

         // If we use a data file, replace the dataFileVariables
         if (!(dataFileVariables.isEmpty())) {
            jtbMessageTemplate.setPayloadText(VariablesUtils.replaceDataFileVariables(dataFileVariables,
                                                                                      jtbMessageTemplate.getPayloadText()));
         }

         // Generate local variables for each iteration
         jtbMessageTemplate
                  .setPayloadText(VariablesUtils.replaceTemplateVariables(cm.getVariables(), jtbMessageTemplate.getPayloadText()));

         updateLog(ScriptStepResult.createStepStart(jtbMessageTemplate, templateName));

         // Send Message
         if (!simulation) {
            Message m = jtbConnection.createJMSMessage(jtbMessageTemplate.getJtbMessageType());
            JTBMessage jtbMessage = jtbMessageTemplate.toJTBMessage(jtbDestination, m);
            jtbDestination.getJtbConnection().sendMessage(jtbMessage);
         }

         updateLog(ScriptStepResult.createStepSuccess());

         // Increment nb messages posted
         nbMessagePost++;
         if (nbMessagePost >= nbMessageMax) {
            throw new InterruptedException(MAX_MESSAGES_REACHED);
         }

         // Eventually pause after...
         Integer pause = step.getPauseSecsAfter();
         if ((pause != null) && (pause > 0)) {
            updateLog(ScriptStepResult.createStepPauseStart(pause));

            if (!simulation) {
               try {
                  TimeUnit.SECONDS.sleep(step.getPauseSecsAfter());
               } catch (InterruptedException e) {
                  // NOP
               }
            }
            updateLog(ScriptStepResult.createStepPauseSuccess());
         }

         monitor.worked(1);
         if (monitor.isCanceled()) {
            monitor.done();
            throw new InterruptedException();
         }

      }
   }

   private void executePause(IProgressMonitor monitor, boolean simulation, RuntimeStep runtimeStep) throws InterruptedException {

      monitor.subTask(runtimeStep.toString());

      Step step = runtimeStep.getStep();
      Integer delay = step.getPauseSecsAfter();

      updateLog(ScriptStepResult.createPauseStart(delay));

      log.debug("running pause step.delay : {} seconds", delay);
      if (!simulation) {
         try {
            TimeUnit.SECONDS.sleep(delay);
         } catch (InterruptedException e) {
            // NOP
         }

         monitor.worked(1);
         if (monitor.isCanceled()) {
            monitor.done();
            throw new InterruptedException();
         }

      }
      updateLog(ScriptStepResult.createPauseSuccess());
   }

   private void updateLog(ScriptStepResult ssr) {
      if (ssr.getData() != null) {
         log.debug(ssr.getData().toString());
      }
      eventBroker.send(Constants.EVENT_REFRESH_EXECUTION_LOG, ssr);
   }

   // --------------
   // Helper Classes
   // --------------

   /**
    * A PRIMARY_MODAL ProgressMonitorDialog
    * 
    * @author Denis Forveille
    *
    */
   private class ProgressMonitorDialogPrimaryModal extends ProgressMonitorDialog {
      public ProgressMonitorDialogPrimaryModal(Shell parent) {
         super(parent);
         setShellStyle(SWT.TITLE | SWT.PRIMARY_MODAL);
      }
   }
}