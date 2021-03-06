package is.idega.idegaweb.egov.bpm.cases.exe;

import is.idega.idegaweb.egov.application.business.ApplicationBusiness;
import is.idega.idegaweb.egov.application.data.Application;
import is.idega.idegaweb.egov.application.data.ApplicationHome;
import is.idega.idegaweb.egov.bpm.application.AppSupportsManager;
import is.idega.idegaweb.egov.bpm.application.AppSupportsManagerFactory;
import is.idega.idegaweb.egov.bpm.cases.CasesBPMProcessConstants;
import is.idega.idegaweb.egov.bpm.cases.CasesStatusMapperHandler;
import is.idega.idegaweb.egov.bpm.cases.manager.BPMCasesRetrievalManagerImpl;
import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.FinderException;
import javax.servlet.ServletContext;

import org.jbpm.JbpmContext;
import org.jbpm.JbpmException;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.idega.block.process.data.CaseStatus;
import com.idega.bpm.BPMConstants;
import com.idega.bpm.exe.DefaultBPMProcessDefinitionW;
import com.idega.bpm.xformsview.XFormsView;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.data.IDOLookup;
import com.idega.data.IDOLookupException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.CaseTypesProcDefBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.JbpmCallback;
import com.idega.jbpm.exe.ProcessConstants;
import com.idega.jbpm.view.View;
import com.idega.jbpm.view.ViewSubmission;
import com.idega.presentation.IWContext;
import com.idega.presentation.PresentationObject;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreUtil;
import com.idega.util.IWTimestamp;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.datastructures.map.MapUtil;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.52 $ Last modified: $Date: 2009/06/30 13:17:35 $ by $Author: valdas $
 */
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Service(CasesBPMProcessDefinitionW.SPRING_BEAN_IDENTIFIER)
public class CasesBPMProcessDefinitionW extends DefaultBPMProcessDefinitionW {

	public static final String SPRING_BEAN_IDENTIFIER = "casesPDW";

	@Autowired
	private CasesBPMDAO casesBPMDAO;

	@Autowired
	@Qualifier(CaseIdentifier.QUALIFIER)
	private CaseIdentifier caseIdentifier;

	@Autowired
	private CasesStatusMapperHandler casesStatusMapperHandler;
	@Autowired
	private AppSupportsManagerFactory appSupportsManagerFactory;

	@Transactional(readOnly = false)
	@Override
	public Long startProcess(final ViewSubmission viewSubmission) {
		final Long processDefinitionId = viewSubmission.getProcessDefinitionId();

		if (!processDefinitionId.equals(getProcessDefinitionId()))
			throw new IllegalArgumentException("View submission was for different process definition id than tried to submit to");

		final ProcessDefinition pd = getProcessDefinition();

		final String procDefName = pd.getName();
		getLogger().info("Starting process for process definition id = " + processDefinitionId + ", process definition name: " + procDefName);

		Map<String, String> parameters = viewSubmission.resolveParameters();

		getLogger().finer("Params " + parameters);

		final Integer userId = parameters.containsKey(CasesBPMProcessConstants.userIdActionVariableName) ?
				Integer.parseInt(parameters.get(CasesBPMProcessConstants.userIdActionVariableName)) : null;

		final String caseStatusKey = parameters.containsKey(CasesBPMProcessConstants.caseStatusVariableName) ?
				parameters.get(CasesBPMProcessConstants.caseStatusVariableName) : null;

		final Integer caseIdentifierNumber = Integer.parseInt(parameters.get(CasesBPMProcessConstants.caseIdentifierNumberParam));
		final String caseIdentifier = parameters.get(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER);
		final String realCaseCreationDate = parameters.get(CasesBPMProcessConstants.caseCreationDateParam);

		final Date caseCreated = StringUtil.isEmpty(realCaseCreationDate) ?
				new Timestamp(System.currentTimeMillis()) :
				new IWTimestamp(realCaseCreationDate).getTimestamp();

		final Map<String, Object> variables = new HashMap<String, Object>();
		Long piId = getBpmContext().execute(new JbpmCallback() {
			@Override
			public Long doInJbpm(JbpmContext context) throws JbpmException {
				try {
					ProcessInstance pi = new ProcessInstance(pd);
					TaskInstance ti = pi.getTaskMgmtInstance().createStartTaskInstance();

					View view = getBpmFactory().getView(viewSubmission.getViewId(), viewSubmission.getViewType(), false);

					// binding view to task instance
					view.getViewToTask().bind(view, ti);

					getLogger().info("New process instance created for the process " + procDefName);

					pi.setStart(new Date());

					IWApplicationContext iwac = getIWAC();
					IWMainApplication iwma = iwac.getIWMainApplication();

					UserBusiness userBusiness = getUserBusiness(iwac);
					User user = userId == null ? null : userBusiness.getUser(userId);

					CasesBusiness casesBusiness = getCasesBusiness(iwac);

					CaseTypesProcDefBind bind = getCasesBPMDAO().find(CaseTypesProcDefBind.class, procDefName);
					Long caseCategoryId = bind.getCasesCategoryId();
					Long caseTypeId = bind.getCasesTypeId();

					IWResourceBundle iwrb = null;
					GeneralCase genCase = null;
					try {
						iwrb = casesBusiness.getIWResourceBundleForUser(user, null, iwma.getBundle(PresentationObject.CORE_IW_BUNDLE_IDENTIFIER));
						genCase = casesBusiness.storeGeneralCase(
						            user,
						            caseCategoryId,
						            caseTypeId,
						            null,
						            null,
						            "This is simple cases-jbpm-formbuilder integration example.",
						            null,
						            BPMCasesRetrievalManagerImpl.caseHandlerType,
						            false,
						            iwrb,
						            false, caseIdentifier, true, caseStatusKey, new IWTimestamp(caseCreated).getTimestamp()
						);
					} catch (Exception e) {
						String message = "Error creating case for BPM process: " + pi.getId() + ". User: " + user + ", case category ID: " +
								caseCategoryId + ", case type ID: "	+ caseTypeId + ", resource bunlde: " + iwrb + ", case identifier: " +
								caseIdentifier + ", case status key: " + caseStatusKey;
						getLogger().log(Level.SEVERE, message, e);
						CoreUtil.sendExceptionNotification(message, e);
						throw new RuntimeException(message, e);
					}

					getLogger().info("Case (id=" + genCase.getPrimaryKey() + ") created for process instance " + pi.getId());

					pi.setStart(caseCreated);

					Map<String, Object> caseData = new HashMap<String, Object>();
					caseData.put(CasesBPMProcessConstants.caseIdVariableName, genCase.getPrimaryKey().toString());
					caseData.put(CasesBPMProcessConstants.caseTypeNameVariableName, genCase.getCaseType().getName());
					caseData.put(CasesBPMProcessConstants.caseCategoryNameVariableName, genCase.getCaseCategory().getName());
					caseData.put(CasesBPMProcessConstants.caseStatusVariableName, genCase.getCaseStatus().getStatus());
					caseData.put(CasesBPMProcessConstants.caseStatusClosedVariableName, casesBusiness.getCaseStatusReady().getStatus());
					caseData.put(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER, caseIdentifier);

					Collection<CaseStatus> allStatuses = casesBusiness.getCaseStatuses();

					CasesStatusMapperHandler casesStatusMapper = getCasesStatusMapperHandler();

					for (CaseStatus caseStatus : allStatuses)
						caseData.put(casesStatusMapper.getStatusVariableNameFromStatusCode(caseStatus.getStatus()), caseStatus.getStatus());

					final Locale dateLocale;
					IWContext iwc = CoreUtil.getIWContext();
					dateLocale = iwc == null ? userBusiness.getUsersPreferredLocale(user) : iwc.getCurrentLocale();
					IWTimestamp created = new IWTimestamp(genCase.getCreated());
					caseData.put(CasesBPMProcessConstants.caseCreatedDateVariableName, created.getLocaleDateAndTime(dateLocale, IWTimestamp.SHORT,
							IWTimestamp.SHORT));

					CaseProcInstBind piBind = new CaseProcInstBind();
					piBind.setCaseId(new Integer(genCase.getPrimaryKey().toString()));
					piBind.setProcInstId(pi.getId());
					piBind.setCaseIdentierID(caseIdentifierNumber);

					piBind.setDateCreated(caseCreated);
					piBind.setCaseIdentifier(caseIdentifier);
					getCasesBPMDAO().persist(piBind);
					getLogger().info("Bind was created: process instance ID=" + pi.getId() + ", case ID=" + genCase.getPrimaryKey());

					pi.getContextInstance().setVariables(caseData);
					getLogger().info("Variables were set: " + caseData);

					variables.putAll(viewSubmission.resolveVariables());
					submitVariablesAndProceedProcess(ti, variables, true);

					if (variables != null && variables.containsKey(BPMConstants.PUBLIC_PROCESS)) {
						Object publicProcess = variables.get(BPMConstants.PUBLIC_PROCESS);
						if (Boolean.valueOf(publicProcess.toString())) {
							genCase.setAsAnonymous(Boolean.TRUE);
							genCase.store();
						}
					}

					getLogger().info("Variables were submitted and a process proceeded");

					return pi.getId();
				} catch (JbpmException e) {
					throw e;
				} catch (RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});

		try {
			getLogger().info("Process was created: " + piId);
			return piId;
		} finally {
			notifyAboutNewProcess(procDefName, piId, variables);
		}
	}

	@Override
	@Transactional(readOnly = false)
	public View loadInitView(final Integer initiatorId) {
		try {
			return getBpmContext().execute(new JbpmCallback() {

				@Override
				public Object doInJbpm(JbpmContext context) throws JbpmException {
					Long processDefinitionId = getProcessDefinitionId();
					ProcessDefinition pd = getProcessDefinition();

					Long startTaskId = pd.getTaskMgmtDefinition().getStartTask().getId();

					List<String> preferred = new ArrayList<String>(1);
					preferred.add(XFormsView.VIEW_TYPE);
					View view = getBpmFactory().getViewByTask(startTaskId, true, preferred);
					view.takeView();

					// we don't know yet the task instance id, so we store the
					// view id
					// and type, to resolve later in start process. Only then we
					// will
					// bind view with task instance

					String caseIdentifierQualifier = IWMainApplication.getDefaultIWMainApplication()
							.getSettings().getProperty("case_identifier_qualifier", CaseIdentifier.QUALIFIER);
					Object[] identifiers = getCaseIdentifier(caseIdentifierQualifier).generateNewCaseIdentifier();
					Integer identifierNumber = (Integer) identifiers[0];
					String identifier = (String) identifiers[1];

					IWTimestamp realCreationDate = new IWTimestamp();
					String realCreationDateString = realCreationDate.toString();
					Map<String, String> parameters = new HashMap<String, String>(7);

					parameters.put(ProcessConstants.START_PROCESS, ProcessConstants.START_PROCESS);
					parameters.put(ProcessConstants.PROCESS_DEFINITION_ID, String.valueOf(processDefinitionId));
					parameters.put(ProcessConstants.VIEW_ID, view.getViewId());
					parameters.put(ProcessConstants.VIEW_TYPE, view.getViewType());

					if (initiatorId != null)
						parameters.put(CasesBPMProcessConstants.userIdActionVariableName, initiatorId.toString());

					parameters.put(CasesBPMProcessConstants.caseIdentifierNumberParam, String.valueOf(identifierNumber));
					parameters.put(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER, String.valueOf(identifier));
					parameters.put(CasesBPMProcessConstants.caseCreationDateParam, realCreationDateString);

					view.populateParameters(parameters);

					HashMap<String, Object> vars = new HashMap<String, Object>(1);
					vars.put(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER, identifier);

					view.populateVariables(vars);

					// --

					return view;
				}
			});
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<String> getRolesCanStartProcess(Object context) {
		final Integer applicationId = new Integer(context.toString());

		AppSupportsManager appSupportsManager = getAppSupportsManagerFactory().getAppSupportsManager(applicationId, getProcessDefinition().getName());

		List<String> rolesCanStartProcess = appSupportsManager.getRolesCanStartProcess();
		return rolesCanStartProcess;
	}

	/**
	 * sets roles, whose users can start process (and see application).
	 *
	 * @param rolesKeys
	 *            - idega roles keys (<b>not</b> process roles)
	 * @param processContext
	 *            - some context depending implementation, e.g., roles can start process using
	 *            applications - then context will be application id
	 */
	@Override
	@Transactional(readOnly = false)
	public void setRolesCanStartProcess(List<String> rolesKeys, Object processContext) {
		if (rolesKeys == null)
			rolesKeys = Collections.emptyList();

		final Integer applicationId = new Integer(processContext.toString());

		AppSupportsManager appSupportsManager = getAppSupportsManagerFactory().getAppSupportsManager(applicationId, getProcessDefinition().getName());
		appSupportsManager.updateRolesCanStartProcess(rolesKeys);
	}

	protected CasesBusiness getCasesBusiness(IWApplicationContext iwac) {
		try {
			return IBOLookup.getServiceInstance(iwac,
			    CasesBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	protected UserBusiness getUserBusiness(IWApplicationContext iwac) {
		try {
			return IBOLookup.getServiceInstance(iwac, UserBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	private IWApplicationContext getIWAC() {
		IWContext iwc = CoreUtil.getIWContext();
		return iwc == null ? IWMainApplication.getDefaultIWApplicationContext() : iwc;
	}

	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}

	private CaseIdentifier getCaseIdentifier(String qualifier) {
		ServletContext sc = getIWAC().getIWMainApplication().getServletContext();
		@SuppressWarnings("unchecked")
		Map<String, ? extends CaseIdentifier> identifierGenerators = WebApplicationContextUtils.getWebApplicationContext(sc)
			.getBeansOfType(CaseIdentifier.class);

		if (MapUtil.isEmpty(identifierGenerators)) {
			getLogger().warning("There are no beans (type of '"+CaseIdentifier.class+"') to generate case identifier!");
			return caseIdentifier;
		} else if (identifierGenerators.values().size() == 1) {
			return caseIdentifier;
		}

		if (StringUtil.isEmpty(qualifier)) {
			return identifierGenerators.values().iterator().next();
		}

		for (CaseIdentifier identifierGenerator: identifierGenerators.values()) {
			Qualifier qualifierAnnotation = identifierGenerator.getClass().getAnnotation(Qualifier.class);
			if (qualifierAnnotation != null && qualifier.equals(qualifierAnnotation.value())) {
				getLogger().info("Using identifier generator: " + identifierGenerator.getClass());
				return identifierGenerator;
			}
		}

		return caseIdentifier;
	}

	@Override
	public String getProcessName(Locale locale) {
		if (locale == null) {
			return null;
		}

		return getProcessName(getProcessDefinitionId(), locale);
	}

	//	TODO: make caching?
	@Transactional(readOnly = true)
	public String getProcessName(final Long processDefinitionId, final Locale locale) {
		if (processDefinitionId == null) {
			return null;
		}

		return getBpmContext().execute(new JbpmCallback() {

			@Override
			public Object doInJbpm(JbpmContext context) throws JbpmException {
				ProcessDefinition pd = context.getGraphSession().getProcessDefinition(processDefinitionId);
				try {
					return getProcessDefinitionLocalizedName(pd, locale, (ApplicationHome) IDOLookup.getHome(Application.class));
				} catch (IDOLookupException e) {
					e.printStackTrace();
					return null;
				}
			}
		});
	}

	private String getProcessDefinitionLocalizedName(ProcessDefinition pd, Locale locale, ApplicationHome appHome) {
		if (pd == null || locale == null || appHome == null) {
			return null;
		}

		Collection<Application> apps = null;
		try {
			apps = appHome.findAllByApplicationUrl(pd.getName());
		} catch (FinderException e) {
			e.printStackTrace();
		}
		if (ListUtil.isEmpty(apps)) {
			Logger.getLogger(this.getClass().getName()).log(Level.WARNING, "Didn't find any application by URL: " + pd.getName() + ", returning standard name!");
			return pd.getName();
		}

		ApplicationBusiness applicationBusiness = null;
		try {
			applicationBusiness = IBOLookup.getServiceInstance(IWMainApplication.getDefaultIWApplicationContext(),
					ApplicationBusiness.class);
		} catch (IBOLookupException e) {
			e.printStackTrace();
		}
		if (applicationBusiness == null) {
			return pd.getName();
		}

		return applicationBusiness.getApplicationName(apps.iterator().next(), locale);
	}

	//	TODO: make caching?
	@Transactional(readOnly = true)
	public String getProcessName(String processName, Locale locale) {
		ProcessDefinition pd = getBpmFactory().getBPMDAO().findLatestProcessDefinition(processName);
		if (pd == null) {
			return null;
		}

		try {
			return getProcessDefinitionLocalizedName(pd, locale, (ApplicationHome) IDOLookup.getHome(Application.class));
		} catch (IDOLookupException e) {
			e.printStackTrace();
		}

		return null;
	}

	public CasesStatusMapperHandler getCasesStatusMapperHandler() {
		return casesStatusMapperHandler;
	}

	AppSupportsManagerFactory getAppSupportsManagerFactory() {
		return appSupportsManagerFactory;
	}
}