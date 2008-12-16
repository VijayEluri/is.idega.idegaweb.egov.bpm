package is.idega.idegaweb.egov.bpm.cases.exe;

import is.idega.idegaweb.egov.bpm.cases.CasesBPMProcessConstants;
import is.idega.idegaweb.egov.bpm.cases.CasesStatusVariables;
import is.idega.idegaweb.egov.bpm.cases.manager.CasesBPMCaseManagerImpl;
import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jbpm.JbpmContext;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.block.process.business.CaseManager;
import com.idega.block.process.data.CaseStatus;
import com.idega.bpm.exe.DefaultBPMProcessDefinitionW;
import com.idega.bpm.xformsview.XFormsView;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.egov.bpm.data.AppProcBindDefinition;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.CaseTypesProcDefBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.exe.ProcessConstants;
import com.idega.jbpm.view.View;
import com.idega.jbpm.view.ViewSubmission;
import com.idega.presentation.IWContext;
import com.idega.presentation.PresentationObject;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.IWTimestamp;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.25 $
 * 
 *          Last modified: $Date: 2008/12/16 19:55:17 $ by $Author: civilis $
 */
@Scope("prototype")
@Service("casesPDW")
public class CasesBPMProcessDefinitionW extends DefaultBPMProcessDefinitionW {

	@Autowired
	private CasesBPMDAO casesBPMDAO;
	@Autowired
	private CaseIdentifier caseIdentifier;
	@Autowired
	private CaseManager caseManager;

	private static final Logger logger = Logger
			.getLogger(CasesBPMProcessDefinitionW.class.getName());

	@SuppressWarnings("unchecked")
	@Override
	public void startProcess(ViewSubmission viewSubmission) {

		Long processDefinitionId = viewSubmission.getProcessDefinitionId();

		logger.finer("Starting process for process definition id = "
				+ processDefinitionId);

		Map<String, String> parameters = viewSubmission.resolveParameters();

		logger.finer("Params " + parameters);

		final Integer userId;
		if (parameters
				.containsKey(CasesBPMProcessConstants.userIdActionVariableName))
			userId = Integer.parseInt(parameters
					.get(CasesBPMProcessConstants.userIdActionVariableName));
		else
			userId = null;

		Integer caseIdentifierNumber = Integer.parseInt(parameters
				.get(CasesBPMProcessConstants.caseIdentifierNumberParam));
		String caseIdentifier = parameters
				.get(CasesBPMProcessConstants.caseIdentifier);

		JbpmContext ctx = getBpmContext().createJbpmContext();

		try {
			ProcessDefinition pd = ctx.getGraphSession().getProcessDefinition(
					processDefinitionId);
			ProcessInstance pi = new ProcessInstance(pd);
			TaskInstance ti = pi.getTaskMgmtInstance()
					.createStartTaskInstance();

			View view = getBpmFactory().getView(viewSubmission.getViewId(),
					viewSubmission.getViewType(), false);

			// binding view to task instance
			view.getViewToTask().bind(view, ti);

			logger.log(Level.INFO,
					"New process instance created for the process "
							+ pd.getName());
			
			pi.setStart(new Date());

			IWApplicationContext iwac = getIWAC();
			UserBusiness userBusiness = getUserBusiness(iwac);

			final User user;
			if (userId != null)
				user = userBusiness.getUser(userId);
			else
				user = null;

			IWMainApplication iwma = iwac.getIWMainApplication();

			CasesBusiness casesBusiness = getCasesBusiness(iwac);

			CaseTypesProcDefBind bind = getCasesBPMDAO().find(
					CaseTypesProcDefBind.class, pd.getName());
			Long caseCategoryId = bind.getCasesCategoryId();
			Long caseTypeId = bind.getCasesTypeId();

			GeneralCase genCase = casesBusiness
					.storeGeneralCase(
							user,
							caseCategoryId,
							caseTypeId,
							null,
							"This is simple cases-jbpm-formbuilder integration example.",
							null,
							CasesBPMCaseManagerImpl.caseHandlerType,
							false,
							casesBusiness
									.getIWResourceBundleForUser(
											user,
											null,
											iwma
													.getBundle(PresentationObject.CORE_IW_BUNDLE_IDENTIFIER)),
							false);

			logger.log(Level.INFO, "Case (id=" + genCase.getPrimaryKey()
					+ ") created for process instance " + pi.getId());

			pi.setStart(new Date());

			Map<String, Object> caseData = new HashMap<String, Object>();
			caseData.put(CasesBPMProcessConstants.caseIdVariableName, genCase
					.getPrimaryKey().toString());
			caseData.put(CasesBPMProcessConstants.caseTypeNameVariableName,
					genCase.getCaseType().getName());
			caseData.put(CasesBPMProcessConstants.caseCategoryNameVariableName,
					genCase.getCaseCategory().getName());
			caseData.put(CasesBPMProcessConstants.caseStatusVariableName,
					genCase.getCaseStatus().getStatus());
			caseData.put(CasesBPMProcessConstants.caseStatusClosedVariableName,
					casesBusiness.getCaseStatusReady().getStatus());
			caseData.put(CasesBPMProcessConstants.caseIdentifier,
					caseIdentifier);

			Collection<CaseStatus> allStatuses = casesBusiness
					.getCaseStatuses();

			for (CaseStatus caseStatus : allStatuses)
				caseData.put(CasesStatusVariables
						.evaluateStatusVariableName(caseStatus.getStatus()),
						caseStatus.getStatus());

			final Locale dateLocale;

			IWContext iwc = IWContext.getCurrentInstance();

			if (iwc != null)
				dateLocale = iwc.getCurrentLocale();
			else {
				dateLocale = userBusiness.getUsersPreferredLocale(user);
			}

			IWTimestamp created = new IWTimestamp(genCase.getCreated());
			caseData.put(CasesBPMProcessConstants.caseCreatedDateVariableName,
					created.getLocaleDateAndTime(dateLocale, IWTimestamp.SHORT,
							IWTimestamp.SHORT));

			CaseProcInstBind piBind = new CaseProcInstBind();
			piBind.setCaseId(new Integer(genCase.getPrimaryKey().toString()));
			piBind.setProcInstId(pi.getId());
			piBind.setCaseIdentierID(caseIdentifierNumber);
			piBind.setDateCreated(created.getDate());
			getCasesBPMDAO().persist(piBind);

			// TODO: if variables submission and process execution fails here,
			// rollback case proc inst bind

			getVariablesHandler().submitVariables(caseData, ti.getId(), false);
			submitVariablesAndProceedProcess(ti, viewSubmission
					.resolveVariables(), true);

		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			getBpmContext().closeAndCommit(ctx);
		}
	}

	@Override
	public View loadInitView(Integer initiatorId) {

		JbpmContext ctx = getBpmContext().createJbpmContext();

		try {
			Long processDefinitionId = getProcessDefinitionId();
			ProcessDefinition pd = ctx.getGraphSession().getProcessDefinition(
					processDefinitionId);

			Long startTaskId = pd.getTaskMgmtDefinition().getStartTask()
					.getId();

			List<String> preferred = new ArrayList<String>(1);
			preferred.add(XFormsView.VIEW_TYPE);
			View view = getBpmFactory().getViewByTask(startTaskId, true,
					preferred);
			view.takeView();

			// we don't know yet the task instance id, so we store the view id
			// and type, to resolve later in start process. Only then we will
			// bind view with task instance

			Object[] identifiers = getCaseIdentifier()
					.generateNewCaseIdentifier();
			Integer identifierNumber = (Integer) identifiers[0];
			String identifier = (String) identifiers[1];

			Map<String, String> parameters = new HashMap<String, String>(7);

			parameters.put(ProcessConstants.START_PROCESS,
					ProcessConstants.START_PROCESS);
			parameters.put(ProcessConstants.PROCESS_DEFINITION_ID, String
					.valueOf(processDefinitionId));
			parameters.put(ProcessConstants.VIEW_ID, view.getViewId());
			parameters.put(ProcessConstants.VIEW_TYPE, view.getViewType());

			if (initiatorId != null)
				parameters.put(
						CasesBPMProcessConstants.userIdActionVariableName,
						initiatorId.toString());

			parameters.put(CasesBPMProcessConstants.caseIdentifierNumberParam,
					String.valueOf(identifierNumber));
			parameters.put(CasesBPMProcessConstants.caseIdentifier, String
					.valueOf(identifier));

			view.populateParameters(parameters);

			HashMap<String, Object> vars = new HashMap<String, Object>(1);
			vars.put(CasesBPMProcessConstants.caseIdentifier, identifier);

			view.populateVariables(vars);

			// --

			return view;

		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);

		} finally {

			getBpmContext().closeAndCommit(ctx);
		}
	}

	@Override
	public List<String> getRolesCanStartProcess(Object context) {

		Integer appId = new Integer(context.toString());

		ProcessDefinition pd = getProcessDefinition();
		AppProcBindDefinition def = (AppProcBindDefinition) pd
				.getDefinition(AppProcBindDefinition.class);

		final List<String> rolesCanStart;

		if (def != null) {

			rolesCanStart = def.getRolesCanStartProcess(appId);
		} else
			rolesCanStart = null;

		return rolesCanStart;
	}

	/**
	 * sets roles, whose users can start process (and see application).
	 * 
	 * @param roles
	 *            - idega roles keys (<b>not</b> process roles)
	 * @param context
	 *            - some context depending implementation, e.g., roles can start
	 *            process using applications - then context will be application
	 *            id
	 */
	@Override
	public void setRolesCanStartProcess(List<String> roles, Object context) {

		ProcessDefinition pd = getProcessDefinition();
		AppProcBindDefinition def = (AppProcBindDefinition) pd
				.getDefinition(AppProcBindDefinition.class);

		if (def != null || !roles.isEmpty()) {

			logger
					.finer("Will set roles, that can start process for the process (id="
							+ getProcessDefinitionId()
							+ ") name = "
							+ getProcessDefinition().getName());

			if (def == null) {

				def = new AppProcBindDefinition();

				JbpmContext ctx = getBpmContext().createJbpmContext();

				try {
					getBpmContext().saveProcessEntity(def);

					pd = ctx.getGraphSession().getProcessDefinition(
							getProcessDefinitionId());
					pd.addDefinition(def);

				} finally {
					getBpmContext().closeAndCommit(ctx);
				}

				ctx = getBpmContext().createJbpmContext();

				try {
					pd = ctx.getGraphSession().loadProcessDefinition(
							getProcessDefinitionId());
					def = (AppProcBindDefinition) pd
							.getDefinition(AppProcBindDefinition.class);

				} finally {
					getBpmContext().closeAndCommit(ctx);
				}
			}

			Integer appId = new Integer(context.toString());
			def.updateRolesCanStartProcess(appId, roles);
		}
	}

	protected CasesBusiness getCasesBusiness(IWApplicationContext iwac) {
		try {
			return (CasesBusiness) IBOLookup.getServiceInstance(iwac,
					CasesBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	protected UserBusiness getUserBusiness(IWApplicationContext iwac) {
		try {
			return (UserBusiness) IBOLookup.getServiceInstance(iwac,
					UserBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	private IWApplicationContext getIWAC() {

		final IWContext iwc = IWContext.getCurrentInstance();
		final IWApplicationContext iwac;

		if (iwc != null) {

			iwac = iwc;

		} else {
			iwac = IWMainApplication.getDefaultIWApplicationContext();
		}

		return iwac;
	}

	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}

	public CaseIdentifier getCaseIdentifier() {
		return caseIdentifier;
	}

	public void setCaseIdentifier(CaseIdentifier caseIdentifier) {
		this.caseIdentifier = caseIdentifier;
	}

	public CaseManager getCaseManager() {
		return caseManager;
	}

	public void setCaseManager(CaseManager caseManager) {
		this.caseManager = caseManager;
	}

	@Override
	public String getProcessName(Locale locale) {

		if (locale == null) {
			return null;
		}

		return getCaseManager()
				.getProcessName(getProcessDefinitionId(), locale);
	}
}