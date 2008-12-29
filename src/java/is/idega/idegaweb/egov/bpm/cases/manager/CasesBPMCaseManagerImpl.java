package is.idega.idegaweb.egov.bpm.cases.manager;

import is.idega.idegaweb.egov.application.business.ApplicationBusiness;
import is.idega.idegaweb.egov.application.data.Application;
import is.idega.idegaweb.egov.application.data.ApplicationHome;
import is.idega.idegaweb.egov.bpm.cases.CasesBPMProcessConstants;
import is.idega.idegaweb.egov.bpm.cases.presentation.UICasesBPMAssets;
import is.idega.idegaweb.egov.bpm.cases.presentation.beans.CasesBPMAssetsState;
import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;
import is.idega.idegaweb.egov.cases.presentation.ClosedCases;
import is.idega.idegaweb.egov.cases.presentation.MyCases;
import is.idega.idegaweb.egov.cases.presentation.OpenCases;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.FinderException;
import javax.faces.component.UIComponent;
import javax.faces.component.UIViewRoot;

import org.jbpm.JbpmContext;
import org.jbpm.JbpmException;
import org.jbpm.db.GraphSession;
import org.jbpm.graph.def.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.idega.block.process.business.CaseManager;
import com.idega.block.process.data.Case;
import com.idega.block.process.presentation.UserCases;
import com.idega.block.process.presentation.beans.CaseManagerState;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.core.builder.data.ICPage;
import com.idega.core.builder.data.ICPageHome;
import com.idega.core.persistence.Param;
import com.idega.data.IDOLookup;
import com.idega.data.IDOLookupException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.CaseTypesProcDefBind;
import com.idega.idegaweb.egov.bpm.data.ProcessUserBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.BPMContext;
import com.idega.jbpm.JbpmCallback;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.identity.RolesManager;
import com.idega.presentation.IWContext;
import com.idega.presentation.text.Link;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreUtil;
import com.idega.util.ListUtil;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;
import com.idega.webface.WFUtil;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.22 $
 *
 * Last modified: $Date: 2008/12/29 03:27:09 $ by $Author: arunas $
 */
@Scope("singleton")
@Service(CasesBPMCaseManagerImpl.beanIdentifier)
@Transactional(readOnly = true)
public class CasesBPMCaseManagerImpl implements CaseManager {

	public static final String PARAMETER_PROCESS_INSTANCE_PK = "pr_inst_pk";
	
	@Autowired private CasesBPMDAO casesBPMDAO;
	@Autowired private BPMContext bpmContext;
	@Autowired private BPMFactory bpmFactory;
	
	static final String beanIdentifier = "casesBPMCaseHandler";
	public static final String caseHandlerType = "CasesBPM";

	public String getBeanIdentifier() {
		return beanIdentifier;
	}

	public String getType() {
		return caseHandlerType;
	}
	
	public String getProcessIdentifier(Case theCase) {
		final Long piId = getProcessInstanceId(theCase);
		if (piId == null) {
			return null;
		}
		
	//	System.out.println("_________PROCESS INSTANCE ID++++="+piId);
		
		return getBpmContext().execute(new JbpmCallback() {

			public Object doInJbpm(JbpmContext context) throws JbpmException {
				return (String)context.getProcessInstance(piId).getContextInstance().getVariable(CasesBPMProcessConstants.caseIdentifier);
			}
		});
	}
	
	public Long getProcessInstanceId(Case theCase) {
		Integer caseId = theCase.getPrimaryKey() instanceof Integer ? (Integer)theCase.getPrimaryKey() : Integer.valueOf((theCase.getPrimaryKey().toString()));
		
		CaseProcInstBind cpi = getCasesBPMDAO().getCaseProcInstBindByCaseId(caseId);
		
		return cpi == null ? null : cpi.getProcInstId();
	}
	
	public Long getProcessDefinitionId(Case theCase) {
		ProcessDefinition processDefinition = getProcessDefinition(theCase);
		return processDefinition == null ? null : processDefinition.getId();
	}
	
	public String getProcessDefinitionName(Case theCase) {
		ProcessDefinition processDefinition = getProcessDefinition(theCase);
		return processDefinition == null ? null : processDefinition.getName();
	}
	
	private ProcessDefinition getProcessDefinition(Case theCase) {
		final Long piId = getProcessInstanceId(theCase);
		if (piId == null) {
			return null;
		}
		
		return getBpmContext().execute(new JbpmCallback() {

			public Object doInJbpm(JbpmContext context) throws JbpmException {
				return context.getProcessInstance(piId).getProcessDefinition();
			}
		});
	}

	public List<Link> getCaseLinks(Case theCase, String casesComponentType) {
		
		throw new UnsupportedOperationException("Implement with correct pages if needed");
		
		/*
		FacesContext ctx = FacesContext.getCurrentInstance();
		IWContext iwc = IWContext.getIWContext(ctx);
		IWMainApplication iwma = IWMainApplication.getIWMainApplication(ctx);
		IWBundle bundle = iwma.getBundle(IWBundleStarter.IW_BUNDLE_IDENTIFIER);
		
		List<Link> links = new ArrayList<Link>();
		
//		tmp solution
		String pageType;
		
		if("temp_usercases".equals(casesComponentType)) {
			
			pageType = user_assets_page_type;
		} else
			pageType = handler_assets_page_type;
		
		String pageUri = getPageUri(iwc, pageType);
		
		Link link2 = new Link(bundle.getImage("images/folder-exec-16x16.png", bundle.getLocalizedString("openBPMProcess", "Open BPM process")), pageUri);
		link2.addParameter(CasesProcessor.PARAMETER_CASE_PK, theCase.getPrimaryKey().toString());
		
		links.add(link2);
		*/
		
		/*
		if(true)
			return links;
		
		Link link = new Link(bundle.getImage("images/folder-exec-16x16.png", bundle.getLocalizedString("openBPMProcess", "Open BPM process")));
		
		link.addParameter(PARAMETER_PROCESS_INSTANCE_PK, String.valueOf(theCase.getCaseManagerType()));
		link.addParameter(CasesProcessor.PARAMETER_CASE_PK, theCase.getPrimaryKey().toString());
		//link.addParameter(CasesProcessor.PARAMETER_ACTION, CasesProcessor.SHOW_CASE_HANDLER);
		link.addParameter(PARAMETER_ACTION, ACTION_OPEN_PROCESS);
		
		links.add(link);
		*/

//		return links;
	}

	public UIComponent getView(IWContext iwc, Case theCase, String caseProcessorType) {
		CasesBPMAssetsState stateBean = (CasesBPMAssetsState) WFUtil.getBeanInstance(CasesBPMAssetsState.beanIdentifier);
		stateBean.setDisplayPropertyForStyleAttribute(Boolean.FALSE);
		stateBean.setStandAloneComponent(Boolean.FALSE);
		
		CaseManagerState managerState = ELUtil.getInstance().getBean(CaseManagerState.beanIdentifier);
		
		if(!UserCases.TYPE.equals(caseProcessorType))
			managerState.setFullView(true);
		else
			managerState.setFullView(false);
		
		UICasesBPMAssets casesAssets = (UICasesBPMAssets)iwc.getApplication().createComponent(UICasesBPMAssets.COMPONENT_TYPE);
		UIViewRoot viewRoot = iwc.getViewRoot();
		if (viewRoot != null) {
			casesAssets.setId(viewRoot.createUniqueId());
		}
		
		casesAssets.setUsePdfDownloadColumn(stateBean.getUsePDFDownloadColumn() == null ? false : stateBean.getUsePDFDownloadColumn());
		casesAssets.setAllowPDFSigning(stateBean.getAllowPDFSigning() == null ? false : stateBean.getAllowPDFSigning());
		casesAssets.setHideEmptySection(stateBean.getHideEmptySection() == null ? false : stateBean.getHideEmptySection());
		
		if (theCase != null) {
			Integer caseId = null;
			try {
				caseId = Integer.valueOf(theCase.getPrimaryKey().toString());
			} catch(NumberFormatException e) {
				e.printStackTrace();
			}
			if (caseId != null) {
				casesAssets.setCaseId(caseId);
			}
		}
		
		return casesAssets;
	}

	public Collection<? extends Case> getCases(User user, String casesComponentType) {
		
		IWContext iwc = IWContext.getCurrentInstance();
		
		RolesManager rolesManager = getBpmFactory().getRolesManager();
		
//		TODO: couldn't we optimize this?
		List<Long> processInstancesIds = rolesManager.getProcessInstancesIdsForUser(iwc, user, true);
		
		Collection<GeneralCase> cases;
		
		if(processInstancesIds != null && !processInstancesIds.isEmpty()) {
			
			try {
				List<Integer> casesIds;
				
				if(OpenCases.TYPE.equals(casesComponentType)) {
					
 					casesIds = getCasesBPMDAO().getResultList(CaseProcInstBind.getCaseIdsByProcessInstanceIdsProcessInstanceNotEnded, Integer.class,
							new Param(CaseProcInstBind.procInstIdProp, processInstancesIds)
					);
				
				} else if(ClosedCases.TYPE.equals(casesComponentType)) {
					
					casesIds = getCasesBPMDAO().getResultList(CaseProcInstBind.getCaseIdsByProcessInstanceIdsProcessInstanceEnded, Integer.class,
							new Param(CaseProcInstBind.procInstIdProp, processInstancesIds)
					);
					
				} else if(MyCases.TYPE.equals(casesComponentType)) {
					
					casesIds = getCasesBPMDAO().getResultList(CaseProcInstBind.getCaseIdsByProcessInstanceIdsAndProcessUserStatus, Integer.class,
							new Param(CaseProcInstBind.procInstIdProp, processInstancesIds),
							new Param(ProcessUserBind.statusProp, ProcessUserBind.Status.PROCESS_WATCHED.toString())
					);
					
				} else
					casesIds = null;
				
				if(casesIds != null && !casesIds.isEmpty()) {
				
					cases = getCasesBusiness(iwc).getGeneralCaseHome().findAllByIds(casesIds);
					
				} else
					cases = null;
				
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			
		} else
			cases = null;
		
		return cases == null ? new ArrayList<GeneralCase>(0) : cases;
	}
	
	public CasesBusiness getCasesBusiness(IWContext iwc) {
		
		try {
			return (CasesBusiness)IBOLookup.getServiceInstance(iwc, CasesBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	public UserBusiness getUserBusiness(IWContext iwc) {
		
		try {
			return (UserBusiness)IBOLookup.getServiceInstance(iwc, UserBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}
	
	/*
	protected String composeFullUrl(IWContext iwc, Token token) {
		
		String serverURL = iwc.getServerURL();
		String pageUri = getPageUri(iwc);
		
		final URIUtil uriUtil = new URIUtil(pageUri);
		uriUtil.setParameter(tokenParam, String.valueOf(token.getId()));
		pageUri = uriUtil.getUri();
		
		serverURL = serverURL.endsWith(CoreConstants.SLASH) ? serverURL.substring(0, serverURL.length()-1) : serverURL;
		
		String fullURL = new StringBuilder(serverURL)
		.append(pageUri.startsWith(CoreConstants.SLASH) ? CoreConstants.EMPTY : CoreConstants.SLASH)
		.append(pageUri)
		.toString();
		
		return fullURL;
	}
	*/
	
	private final HashMap<String, String> uris = new HashMap<String, String>(2);
	
	protected synchronized String getPageUri(IWApplicationContext iwac, String pageType) {
		
		if(uris.containsKey(pageType))
			return uris.get(pageType);
		
		Collection<ICPage> icpages = getPages(pageType);
		
		ICPage icPage = null;
		
		if(icpages == null || icpages.isEmpty()) {
			
//			TODO: create egov bpm page, as not found
			throw new RuntimeException("No page found by page type: "+pageType);			
		}
		
		if(icPage == null)
			icPage = icpages.iterator().next();
		
		String uri = icPage.getDefaultPageURI();
		
		if(!uri.startsWith("/pages"))
			uri = "/pages"+uri;
		
		String ruri = iwac.getIWMainApplication().getTranslatedURIWithContext(uri);
		uris.put(pageType, ruri);
		
		return ruri;
	}
	
	protected Collection<ICPage> getPages(String pageSubType) {
		
		try {
			ICPageHome home = (ICPageHome) IDOLookup.getHome(ICPage.class);
			@SuppressWarnings("unchecked")
			Collection<ICPage> icpages = home.findBySubType(pageSubType, false);
			
			return icpages;
			
		} catch (Exception e) {
			throw new RuntimeException("Exception while resolving icpages by subType: "+pageSubType, e);
		}
	}

	public BPMContext getBpmContext() {
		return bpmContext;
	}

	public void setBpmContext(BPMContext bpmContext) {
		this.bpmContext = bpmContext;
	}

	public BPMFactory getBpmFactory() {
		return bpmFactory;
	}

	public void setBpmFactory(BPMFactory bpmFactory) {
		this.bpmFactory = bpmFactory;
	}

	public Map<Long, String> getAllCaseProcessDefinitionsWithName() {
		List<ProcessDefinition> allProcesses = getAllProcessDefinitions();
		if (ListUtil.isEmpty(allProcesses)) {
			return null;
		}

		Map<Long, String> processes = new HashMap<Long, String>();
		
		Locale locale = null;
		IWContext iwc = CoreUtil.getIWContext();
		if (iwc != null) {
			locale = iwc.getCurrentLocale();
		}
		if (locale == null) {
			locale = Locale.ENGLISH;
		}
		
		ApplicationHome appHome = null;
		try {
			appHome = (ApplicationHome) IDOLookup.getHome(Application.class);
		} catch (IDOLookupException e) {
			e.printStackTrace();
		}
		if (appHome == null) {
			return null;
		}
		
		String localizedName = null;
		for (ProcessDefinition pd: allProcesses) {
			localizedName = getProcessDefinitionLocalizedName(pd, locale, appHome);
			localizedName = StringUtil.isEmpty(localizedName) ? pd.getName() : localizedName;
			
			processes.put(Long.valueOf(pd.getId()), localizedName);
		}
		return processes;
	}
	
	public List<Long> getAllCaseProcessDefinitions() {
		List<ProcessDefinition> allProcesses = getAllProcessDefinitions();
		if (ListUtil.isEmpty(allProcesses)) {
			return null;
		}
		
		List<Long> ids = new ArrayList<Long>();
		for (ProcessDefinition pd: allProcesses) {
			ids.add(Long.valueOf(pd.getId()));
		}
		
		return ids;
	}
	
	//	TODO: use case type!
	private List<ProcessDefinition> getAllProcessDefinitions() {
		final List<CaseTypesProcDefBind> casesProcesses = getCasesBPMDAO().getAllCaseTypes();
		if (ListUtil.isEmpty(casesProcesses)) {
			return null;
		}
		
		return getBpmContext().execute(new JbpmCallback() {

			public Object doInJbpm(JbpmContext context) throws JbpmException {
				
				ProcessDefinition pd = null;
				List<ProcessDefinition> caseProcessDefinitions = new ArrayList<ProcessDefinition>();
				
				GraphSession graphSession = context.getGraphSession();
				
				for (CaseTypesProcDefBind caseTypesProcDefBind : casesProcesses) {
					pd = graphSession.findLatestProcessDefinition(caseTypesProcDefBind.getProcessDefinitionName());
					
					if (pd != null && !caseProcessDefinitions.contains(pd)) {
						caseProcessDefinitions.add(pd);
					}
				}
					
				return caseProcessDefinitions;
			}
		});
	}
	
//	TODO: move this code to processdefinition wrapper
	public String getProcessName(final Long processDefinitionId, final Locale locale) {
		
		if (processDefinitionId == null) {
			return null;
		}
		
		return getBpmContext().execute(new JbpmCallback() {

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
			applicationBusiness = (ApplicationBusiness) IBOLookup.getServiceInstance(IWMainApplication.getDefaultIWApplicationContext(),
																															ApplicationBusiness.class);
		} catch (IBOLookupException e) {
			e.printStackTrace();
		}
		if (applicationBusiness == null) {
			return pd.getName();
		}
		
		return applicationBusiness.getApplicationName(apps.iterator().next(), locale);
	}

	public Long getLatestProcessDefinitionIdByProcessName(String name) {
		ProcessDefinition pd = getBpmFactory().getBPMDAO().findLatestProcessDefinition(name);
		return pd == null ? null : pd.getId();
	}
}