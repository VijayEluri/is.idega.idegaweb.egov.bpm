package is.idega.idegaweb.egov.bpm.cases;

import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.jbpm.JbpmContext;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.security.AuthorizationService;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.IdegaJbpmContext;
import com.idega.jbpm.def.View;
import com.idega.jbpm.exe.VariablesHandler;
import com.idega.jbpm.exe.impl.AbstractProcessManager;
import com.idega.presentation.IWContext;
import com.idega.presentation.PresentationObject;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;
import com.idega.util.IWTimestamp;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.12 $
 *
 * Last modified: $Date: 2008/03/13 17:00:50 $ by $Author: civilis $
 */
public class CasesBPMProcessManager extends AbstractProcessManager {

	private VariablesHandler variablesHandler;
	private IdegaJbpmContext idegaJbpmContext;
	private CasesBPMDAO casesBPMDAO;
	private AuthorizationService authorizationService;
	
	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	@Autowired
	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}

	public IdegaJbpmContext getIdegaJbpmContext() {
		return idegaJbpmContext;
	}

	public void setIdegaJbpmContext(IdegaJbpmContext idegaJbpmContext) {
		this.idegaJbpmContext = idegaJbpmContext;
	}

	public VariablesHandler getVariablesHandler() {
		return variablesHandler;
	}

	@Autowired
	public void setVariablesHandler(VariablesHandler variablesHandler) {
		this.variablesHandler = variablesHandler;
	}

	public void startProcess(long processDefinitionId, View view) {
		
		Map<String, String> parameters = view.resolveParameters();
		
		int userId = Integer.parseInt(parameters.get(CasesBPMProcessConstants.userIdActionVariableName));
		Long caseCatId = Long.parseLong(parameters.get(CasesBPMProcessConstants.caseCategoryIdActionVariableName));
		Long caseTypeId = Long.parseLong(parameters.get(CasesBPMProcessConstants.caseTypeActionVariableName));
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			
			ProcessDefinition pd = ctx.getGraphSession().getProcessDefinition(processDefinitionId);
			ProcessInstance pi = new ProcessInstance(pd);
			
			IWContext iwc = IWContext.getIWContext(FacesContext.getCurrentInstance());
			User user = getUserBusiness(iwc).getUser(userId);
			IWMainApplication iwma = iwc.getApplicationContext().getIWMainApplication();
			
			GeneralCase genCase = getCasesBusiness(iwc).storeGeneralCase(user, caseCatId, caseTypeId, /*attachment pk*/null, "This is simple cases-jbpm-formbuilder integration example.", "type", CasesBPMCaseHandlerImpl.caseHandlerType, /*isPrivate*/false, getCasesBusiness(iwc).getIWResourceBundleForUser(user, iwc, iwma.getBundle(PresentationObject.CORE_IW_BUNDLE_IDENTIFIER)));
			
			pi.setStart(new Date());
			
			TaskInstance taskInstance = pi.getTaskMgmtInstance().createStartTaskInstance();
			
			Map<String, Object> caseData = new HashMap<String, Object>();
			caseData.put(CasesBPMProcessConstants.caseIdVariableName, genCase.getPrimaryKey().toString());
			caseData.put(CasesBPMProcessConstants.caseTypeNameVariableName, genCase.getCaseType().getName());
			caseData.put(CasesBPMProcessConstants.caseCategoryNameVariableName, genCase.getCaseCategory().getName());
			caseData.put(CasesBPMProcessConstants.caseStatusVariableName, genCase.getCaseStatus().getStatus());
			
			IWTimestamp created = new IWTimestamp(genCase.getCreated());
			caseData.put(CasesBPMProcessConstants.caseCreatedDateVariableName, created.getLocaleDateAndTime(iwc.getCurrentLocale(), IWTimestamp.SHORT, IWTimestamp.SHORT));
			
			getVariablesHandler().submitVariables(caseData, taskInstance.getId(), false);
			submitVariablesAndProceedProcess(taskInstance, view.resolveVariables());
			
			CaseProcInstBind bind = new CaseProcInstBind();
			bind.setCaseId(new Integer(genCase.getPrimaryKey().toString()));
			bind.setProcInstId(pi.getId());
			getCasesBPMDAO().persist(bind);
			
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}
	
	protected void submitVariablesAndProceedProcess(TaskInstance ti, Map<String, Object> variables) {
		
		getVariablesHandler().submitVariables(variables, ti.getId(), true);
    	
    	String actionTaken = (String)ti.getVariable(CasesBPMProcessConstants.actionTakenVariableName);
    	
    	if(actionTaken != null && !CoreConstants.EMPTY.equals(actionTaken) && false)
    		ti.end(actionTaken);
    	else
    		ti.end();
	}
	
	protected CasesBusiness getCasesBusiness(IWApplicationContext iwac) {
		try {
			return (CasesBusiness) IBOLookup.getServiceInstance(iwac, CasesBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	protected UserBusiness getUserBusiness(IWApplicationContext iwac) {
		try {
			return (UserBusiness) IBOLookup.getServiceInstance(iwac, UserBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	public void submitTaskInstance(long taskInstanceId, View view) {
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			TaskInstance taskInstance = ctx.getTaskInstance(taskInstanceId);
	    	submitVariablesAndProceedProcess(taskInstance, view.resolveVariables());
			
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}

	@Override
	public AuthorizationService getAuthorizationService() {
		return authorizationService;
	}

	@Override
	@Autowired
	public void setAuthorizationService(AuthorizationService authorizationService) {
		this.authorizationService = authorizationService;
	}
}