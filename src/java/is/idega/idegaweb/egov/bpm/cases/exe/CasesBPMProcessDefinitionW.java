package is.idega.idegaweb.egov.bpm.cases.exe;

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

import com.idega.block.process.data.CaseStatus;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.core.persistence.Param;
import com.idega.data.IDOLookup;
import com.idega.data.IDOLookupException;
import com.idega.data.SimpleQuerier;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.jbpm.BPMConstants;
import com.idega.jbpm.CasesBPMProcessConstants;
import com.idega.jbpm.JbpmCallback;
import com.idega.jbpm.data.CaseProcInstBind;
import com.idega.jbpm.data.CaseTypesProcDefBind;
import com.idega.jbpm.data.ProcessUserBind;
import com.idega.jbpm.data.ProcessUserBind.Status;
import com.idega.jbpm.data.dao.BPMDAO;
import com.idega.jbpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.data.impl.DefaultBPMProcessDefinitionW;
import com.idega.jbpm.exe.ProcessConstants;
import com.idega.jbpm.view.View;
import com.idega.jbpm.view.ViewSubmission;
import com.idega.jbpm.view.XFormsView;
import com.idega.presentation.IWContext;
import com.idega.presentation.PresentationObject;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreUtil;
import com.idega.util.IWTimestamp;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.datastructures.map.MapUtil;
import com.idega.util.expression.ELUtil;

import is.idega.idegaweb.egov.application.ApplicationUtil;
import is.idega.idegaweb.egov.application.business.ApplicationBusiness;
import is.idega.idegaweb.egov.application.business.ApplicationTypesManager;
import is.idega.idegaweb.egov.application.data.Application;
import is.idega.idegaweb.egov.application.data.ApplicationHome;
import is.idega.idegaweb.egov.application.data.dao.ApplicationDAO;
import is.idega.idegaweb.egov.bpm.application.AppSupportsManager;
import is.idega.idegaweb.egov.bpm.application.AppSupportsManagerFactory;
import is.idega.idegaweb.egov.bpm.cases.CasesStatusMapperHandler;
import is.idega.idegaweb.egov.bpm.cases.manager.BPMCasesRetrievalManagerImpl;
import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;

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

	@Autowired
	private BPMDAO bpmDAO;

	@Autowired(required = false)
	private ApplicationDAO applicationDAO;

	@Autowired(required = false)
	private ApplicationTypesManager applicationTypesManager;

	private BPMDAO getBPMDAO() {
		if (bpmDAO == null) {
			ELUtil.getInstance().autowire(this);
		}

		return bpmDAO;
	}

	private ApplicationDAO getApplicationDAO() {
		if (applicationDAO == null) {
			ELUtil.getInstance().autowire(this);
		}

		return applicationDAO;
	}

	@Override
	public Object doPrepareProcess(Map<String, Object> parameters) {
		try {
			IWApplicationContext iwac = getIWAC();
			IWMainApplication iwma = iwac.getIWMainApplication();
			CasesBusiness casesBusiness = IBOLookup.getServiceInstance(iwac, CasesBusiness.class);
			String caseId = null;
			String procDefName = null;
			User author = null;
			String caseStatusKey = null;
			String realCaseCreationDate = null;
			String caseIdentifier = null;
			if (!MapUtil.isEmpty(parameters)) {
				caseId = parameters.containsKey(com.idega.block.process.business.ProcessConstants.CASE_ID) ?
						parameters.get(com.idega.block.process.business.ProcessConstants.CASE_ID).toString() : null;

				procDefName = parameters.containsKey(com.idega.block.process.business.ProcessConstants.ACTIVE_PROCESS_DEFINITION) ?
						parameters.get(com.idega.block.process.business.ProcessConstants.ACTIVE_PROCESS_DEFINITION).toString() : null;

				author = parameters.containsKey(CasesBPMProcessConstants.userIdActionVariableName) ?
						(User) parameters.get(CasesBPMProcessConstants.userIdActionVariableName) : null;

				caseStatusKey = parameters.containsKey(CasesBPMProcessConstants.caseStatusVariableName) ?
						parameters.get(CasesBPMProcessConstants.caseStatusVariableName).toString() : null;

				realCaseCreationDate = parameters.containsKey(CasesBPMProcessConstants.caseCreationDateParam) ?
						parameters.get(CasesBPMProcessConstants.caseCreationDateParam).toString() : null;

				caseIdentifier = parameters.containsKey(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER) ?
						parameters.get(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER).toString() : null;
			}
			return getCase(caseId, procDefName, casesBusiness, author, iwma, caseStatusKey, realCaseCreationDate, caseIdentifier);
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error preparing process by parameters: " + parameters, e);
		}
		return null;
	}

	private GeneralCase getCase(String caseId, String procDefName, CasesBusiness casesBusiness, User author, IWMainApplication iwma,
			String caseStatusKey, String realCaseCreationDate, String caseIdentifier) {
		IWResourceBundle iwrb = null;
		GeneralCase genCase = null;
		if (!StringUtil.isEmpty(caseId)) {
			try {
				genCase = casesBusiness.getGeneralCase(Integer.valueOf(caseId));
			} catch (Exception e) {
				getLogger().log(Level.WARNING, "Error finding case by ID: " + caseId, e);
			}
		}
		if (genCase != null)
			return genCase;

		Long caseCategoryId = null;
		Long caseTypeId = null;
		try {
			CaseTypesProcDefBind bind = getCasesBPMDAO().find(CaseTypesProcDefBind.class, procDefName);
			caseCategoryId = bind.getCasesCategoryId();
			caseTypeId = bind.getCasesTypeId();

			final Date caseCreated = StringUtil.isEmpty(realCaseCreationDate) ?
					new Timestamp(System.currentTimeMillis()) :
					new IWTimestamp(realCaseCreationDate).getTimestamp();

			iwrb = casesBusiness.getIWResourceBundleForUser(author, null, iwma.getBundle(PresentationObject.CORE_IW_BUNDLE_IDENTIFIER));
			genCase = casesBusiness.storeGeneralCase(
						author,
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
			String message = "Error creating case for BPM process definition: " + procDefName + ". Author: " + author + ", case category ID: " +
					caseCategoryId + ", case type ID: "	+ caseTypeId + ", resource bunlde: " + iwrb + ", case identifier: " +
					caseIdentifier + ", case status key: " + caseStatusKey;
			getLogger().log(Level.SEVERE, message, e);
			CoreUtil.sendExceptionNotification(message, e);
			throw new RuntimeException(message, e);
		}

		return genCase;
	}

	@SuppressWarnings("deprecation")
	@Transactional(readOnly = false)
	private CaseProcInstBind getNewCaseProcBind(org.hibernate.Session session, CaseProcInstBind oldBind, Long newPiId) throws Exception {
		List<ProcessUserBind> usersBinds = getCasesBPMDAO().getResultListByInlineQuery(
				"from " + ProcessUserBind.class.getName() + " ub where ub." + ProcessUserBind.caseProcessBindProp + " = :oldBind",
				ProcessUserBind.class,
				new Param("oldBind", oldBind)
		);
		Map<Integer, Status> data = null;
		if (!ListUtil.isEmpty(usersBinds)) {
			data = new HashMap<Integer, ProcessUserBind.Status>();
			for (ProcessUserBind userBind: usersBinds) {
				data.put(userBind.getUserId(), userBind.getStatus());
				SimpleQuerier.execute("delete from " + ProcessUserBind.TABLE_NAME + " where id_ = " + userBind.getId());
			}
		}

		CaseProcInstBind newBind = new CaseProcInstBind();
		newBind.setCaseId(oldBind.getCaseId());
		newBind.setProcInstId(newPiId);
		newBind.setCaseIdentierID(oldBind.getCaseIdentierID());
		newBind.setDateCreated(oldBind.getDateCreated());
		newBind.setCaseIdentifier(oldBind.getCaseIdentifier());
		session.persist(newBind);
		getLogger().info("Created new bind (" + newBind + ") instead of existing one: " + oldBind);

		String sql = "delete from " + CaseProcInstBind.TABLE_NAME + " where " + CaseProcInstBind.procInstIdColumnName + " = " +
				oldBind.getProcInstId();
		try {
			SimpleQuerier.execute(sql);
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error executing SQL: '" + sql + "'", e);
			return null;
		}

		if (!MapUtil.isEmpty(data)) {
			for (Map.Entry<Integer, Status> userData: data.entrySet()) {
				ProcessUserBind userBind = new ProcessUserBind();
				userBind.setCaseProcessBind(newBind);
				userBind.setUserId(userData.getKey());
				userBind.setStatus(userData.getValue());
				session.persist(userBind);
			}
		}

		return newBind;
	}

	@Transactional(readOnly = false)
	@Override
	public Long startProcess(final ViewSubmission viewSubmission) {
		final Long processDefinitionId = viewSubmission.getProcessDefinitionId();

		if (!processDefinitionId.equals(getProcessDefinitionId()))
			throw new IllegalArgumentException("View submission was for different process definition id than tried to submit to");

		Map<String, String> parameters = viewSubmission.resolveParameters();

		getLogger().finer("Params " + parameters);

		final Integer userId = parameters.containsKey(CasesBPMProcessConstants.userIdActionVariableName) ?
				Integer.parseInt(parameters.get(CasesBPMProcessConstants.userIdActionVariableName)) : null;

		final String caseStatusKey = parameters.containsKey(CasesBPMProcessConstants.caseStatusVariableName) ?
				parameters.get(CasesBPMProcessConstants.caseStatusVariableName) : null;

		final IWApplicationContext iwac = getIWAC();
		final CasesBusiness casesBusiness = getCasesBusiness(iwac);

		GeneralCase theCase = null;
		final String caseId = parameters.get(com.idega.block.process.business.ProcessConstants.CASE_ID);
		if (!StringUtil.isEmpty(caseId)) {
			try {
				theCase = casesBusiness.getGeneralCase(Integer.valueOf(caseId));
			} catch (Exception e) {}
		}

		final Integer caseIdentifierNumber = Integer.parseInt(parameters.get(CasesBPMProcessConstants.caseIdentifierNumberParam));
		final String caseIdentifier = theCase == null ? parameters.get(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER) :
														theCase.getCaseIdentifier();
		final String realCaseCreationDate = theCase == null ?	parameters.get(CasesBPMProcessConstants.caseCreationDateParam) :
																String.valueOf(theCase.getCreated());
		final Map<String, Object> variables = new HashMap<String, Object>();

		Long piId = getBpmContext().execute(new JbpmCallback<Long>() {
			@Override
			public Long doInJbpm(JbpmContext context) throws JbpmException {
				try {
					final ProcessDefinition pd = getProcessDefinition(context);
					String procDefName = pd.getName();
					getLogger().info("Starting process instance for process definition (ID: " + processDefinitionId + ", name: " + procDefName + ")");

					ProcessInstance pi = new ProcessInstance(pd);
					TaskInstance ti = pi.getTaskMgmtInstance().createStartTaskInstance();
					Long piId = pi.getId();
					if (ti == null || ti.getId() <= 0) {
						throw new JbpmException("Task instance for proc. def. (ID: " + processDefinitionId + ") and proc. inst. (ID: "
								+ piId + ") was not created!");
					}

					View view = getBpmFactory().getView(viewSubmission.getViewId(), viewSubmission.getViewType(), false);

					// binding view to task instance
					view.getViewToTask().bind(view, ti);

					getLogger().info("New process instance (ID=" + piId + ") created for the process " + procDefName);

					pi.setStart(new Date());

					IWMainApplication iwma = iwac.getIWMainApplication();

					UserBusiness userBusiness = getUserBusiness(iwac);
					User user = userId == null ? null : userBusiness.getUser(userId);

					GeneralCase genCase = getCase(caseId, procDefName, casesBusiness, user, iwma, caseStatusKey, realCaseCreationDate,
							caseIdentifier);
					getLogger().info("Case (id=" + genCase.getPrimaryKey() + ") created for process instance " + piId);

					Timestamp caseCreated = genCase.getCreated();
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

					for (CaseStatus caseStatus: allStatuses)
						caseData.put(casesStatusMapper.getStatusVariableNameFromStatusCode(caseStatus.getStatus()), caseStatus.getStatus());

					IWContext iwc = CoreUtil.getIWContext();
					Locale dateLocale = iwc == null ? userBusiness.getUsersPreferredLocale(user) : iwc.getCurrentLocale();
					dateLocale = dateLocale == null ? iwma.getDefaultLocale() : dateLocale;
					dateLocale = dateLocale == null ? Locale.ENGLISH : dateLocale;
					IWTimestamp created = new IWTimestamp(genCase.getCreated());
					caseData.put(CasesBPMProcessConstants.caseCreatedDateVariableName, created.getLocaleDateAndTime(dateLocale, IWTimestamp.SHORT,
							IWTimestamp.SHORT));

					Integer caseId = new Integer(genCase.getPrimaryKey().toString());
					CaseProcInstBind piBind = getCasesBPMDAO().getCaseProcInstBindByCaseId(caseId);
					if (piBind == null) {
						piBind = new CaseProcInstBind();
						piBind.setCaseId(caseId);
						piBind.setProcInstId(piId);
						piBind.setCaseIdentierID(caseIdentifierNumber);
						piBind.setDateCreated(caseCreated);
						piBind.setCaseIdentifier(caseIdentifier);
						getCasesBPMDAO().persist(piBind);
						getLogger().info("Bind was created: process instance ID=" + piId + ", case ID=" + caseId);
					} else {
						getNewCaseProcBind(context.getSession(), piBind, piId);
					}

					pi.getContextInstance().setVariables(caseData);

					variables.putAll(viewSubmission.resolveVariables());
					submitVariablesAndProceedProcess(context, ti, variables, true);

					if (variables != null && variables.containsKey(BPMConstants.PUBLIC_PROCESS)) {
						Object publicProcess = variables.get(BPMConstants.PUBLIC_PROCESS);
						if (Boolean.valueOf(publicProcess.toString())) {
							genCase.setAsAnonymous(Boolean.TRUE);
							genCase.store();
						}
					}

					getLogger().info("Variables were submitted and a process instance (ID: " + piId + ") proceeded");

					return piId;
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
			if (piId == null)
				getLogger().warning("Failed to start process for proc. def. ID: " + processDefinitionId);
			else
				getLogger().info("Process was created: " + piId);
			return piId;
		} finally {
			if (piId != null) {
				notifyAboutNewProcess(getBPMDAO().getProcessDefinitionNameByProcessDefinitionId(getProcessDefinitionId()), piId, variables);
			}
		}
	}

	@Override
	public View loadInitView(final Integer initiatorId) {
		return loadInitView(initiatorId, null);
	}

	@Override
	@Transactional(readOnly = false)
	public View loadInitView(final Integer initiatorId, final String externalIdentifier) {
		try {
			return getBpmContext().execute(new JbpmCallback<View>() {

				@Override
				public View doInJbpm(JbpmContext context) throws JbpmException {
					Long processDefinitionId = getProcessDefinitionId();
					ProcessDefinition pd = getProcessDefinition(context);

					try {
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

						Integer identifierNumber = null;
						String identifier = null;
						if (StringUtil.isEmpty(externalIdentifier)) {
							Object[] identifiers = getCaseIdentifier().generateNewCaseIdentifier();
							identifierNumber = (Integer) identifiers[0];
							identifier = (String) identifiers[1];
						} else {
							identifierNumber = getCaseIdentifier().getCaseIdentifierNumber(externalIdentifier);
							identifier = externalIdentifier;
						}

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

						Map<String, Object> vars = new HashMap<String, Object>(1);
						vars.put(com.idega.block.process.business.ProcessConstants.CASE_IDENTIFIER, identifier);

						view.populateVariables(vars);
						return view;
					} catch (Exception e) {
						String message = "Error loading view for proc. def. " + pd + ", ID: " + processDefinitionId;
						getLogger().log(Level.WARNING, message, e);
						throw new RuntimeException(message);
					}
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

	public CaseIdentifier getCaseIdentifier() {
		String qualifier = CaseIdentifier.QUALIFIER;

		return getCaseIdentifier(qualifier);
	}
	private CaseIdentifier getCaseIdentifier(String qualifier) {
		Map<String, CaseIdentifier> identifierGenerators = getBeansOfType(CaseIdentifier.class);

		if (MapUtil.isEmpty(identifierGenerators)) {
			getLogger().warning("There are no beans (type of '" + CaseIdentifier.class.getName() + "') to generate case identifier!");
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

		return getBpmContext().execute(new JbpmCallback<String>() {
			@Override
			public String doInJbpm(JbpmContext context) throws JbpmException {
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

	private ApplicationTypesManager getApplicationTypesManager() {
		if (applicationTypesManager == null) {
			ELUtil.getInstance().autowire(this);
		}
		return applicationTypesManager;
	}

	private Boolean available = null;

	@Override
	public boolean isAvailable(IWContext iwc) {
		if (available != null) {
			return available;
		}

		String name = null;
		try {
			name = getProcessDefinition().getName();
			is.idega.idegaweb.egov.application.data.bean.Application egovApp = getApplicationDAO().findByUri(name);
			available = ApplicationUtil.isAvailabe(iwc, egovApp);
			return available;
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error checking if BPM process (name: " + name + ", ID: " + getProcessDefinitionId() + ") is available", e);
		}
		return true;
	}

	@Override
	public String getNotAvailableLink(IWContext iwc) {
		if (isAvailable(iwc)) {
			return null;
		}

		String name = null;
		try {
			name = getProcessDefinition().getName();
			is.idega.idegaweb.egov.application.data.bean.Application egovApp = getApplicationDAO().findByUri(name);
			return ApplicationUtil.getRedirectUrl(iwc.getIWMainApplication(), iwc, iwc.getRequest(), getApplicationTypesManager(), egovApp, egovApp.getId().toString(), iwc.isLoggedOn());
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "Error getting redirect URL for unavailable BPM process (name: " + name + ", ID: " + getProcessDefinitionId() + ")", e);
		}
		return null;
	}

}