package is.idega.idegaweb.egov.bpm.application;

import is.idega.idegaweb.egov.application.business.ApplicationType;
import is.idega.idegaweb.egov.application.business.ApplicationTypePluggedInEvent;
import is.idega.idegaweb.egov.application.data.Application;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.List;

import javax.faces.application.FacesMessage;

import org.jbpm.JbpmContext;
import org.jbpm.graph.def.ProcessDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import com.idega.core.builder.business.BuilderService;
import com.idega.core.builder.business.BuilderServiceFactory;
import com.idega.core.builder.data.ICPage;
import com.idega.core.builder.data.ICPageHome;
import com.idega.data.IDOLookup;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.egov.bpm.data.AppProcDefBind;
import com.idega.idegaweb.egov.bpm.data.CaseTypesProcDefBind;
import com.idega.idegaweb.egov.bpm.data.dao.AppBPMDAO;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.IdegaJbpmContext;
import com.idega.jbpm.data.dao.BPMDAO;
import com.idega.jbpm.presentation.BPMTaskViewer;
import com.idega.presentation.IWContext;
import com.idega.presentation.ui.DropdownMenu;
import com.idega.util.URIUtil;

/**
 * Interface is meant to be extended by beans, reflecting application type for egov applications
 * 
 * @author <a href="civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.13 $
 *
 * Last modified: $Date: 2008/04/12 01:53:48 $ by $Author: civilis $
 *
 */
public class ApplicationTypeBPM implements ApplicationType, ApplicationContextAware, ApplicationListener {

	private ApplicationContext ctx;
	private BPMDAO bpmBindsDAO;
	private CasesBPMDAO casesBPMDAO;
	private AppBPMDAO appBPMDAO;
	private IdegaJbpmContext idegaJbpmContext;
	public static final String beanIdentifier = "appTypeBPM";
	private static final String appType = "EGOV_BPM";
	private static final String egovBPMPageType = "bpm_app_starter";
	
	public ApplicationTypeHandlerComponent getHandlerComponent() {		
		UIApplicationTypeBPMHandler h = new UIApplicationTypeBPMHandler();
		return h;
	}

	public String getLabel(IWContext iwc) {
		return "EGOV BPM";
	}

	public String getType() {
		return appType;
	}

	public void beforeStore(IWContext iwc, Application app) {
		
		app.setElectronic(true);
	}
	
	public boolean afterStore(IWContext iwc, Application app) {
		
		String procDef = iwc.getParameter(UIApplicationTypeBPMHandler.MENU_PARAM);
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			Long pdId = new Long(procDef);
	
			Integer appId = getAppId(app.getPrimaryKey());
			AppProcDefBind bind = getAppBPMDAO().find(AppProcDefBind.class, appId);
			
			ProcessDefinition pd = ctx.getGraphSession().getProcessDefinition(pdId);
			
			if(bind == null) {
				bind = new AppProcDefBind();
				bind.setApplicationId(appId);
				bind.setProcessDefinitionName(pd.getName());
				getAppBPMDAO().persist(bind);
				
			} else {
				
				if(!bind.getProcessDefinitionName().equals(pd.getName())) {
					
					bind.setProcessDefinitionName(pd.getName());
					getAppBPMDAO().flush();
				}
			}
		} catch(Exception exp) {
			iwc.addMessage(null, new FacesMessage("Exception:" + exp.getMessage()));
			return false;
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
		
		return true;
	}
	
	private Integer getAppId(Object pk) {
		
		if(pk instanceof Integer)
			return (Integer)pk;
		else
			return new Integer(pk.toString());
	}

	public void setApplicationContext(ApplicationContext applicationcontext)
			throws BeansException {
		ctx = applicationcontext;		
	}

	public void onApplicationEvent(ApplicationEvent applicationevent) {
		
		if(applicationevent instanceof ContextRefreshedEvent) {
			
			ApplicationTypePluggedInEvent event = new ApplicationTypePluggedInEvent(this);
			event.setAppTypeBeanIdentifier(beanIdentifier);
			ctx.publishEvent(event);
		}
	}
	
	public BPMDAO getBpmBindsDAO() {
		return bpmBindsDAO;
	}

	@Autowired
	public void setBpmBindsDAO(BPMDAO bpmBindsDAO) {
		this.bpmBindsDAO = bpmBindsDAO;
	}
	
	public void fillMenu(DropdownMenu menu) {
		
		List<CaseTypesProcDefBind> casesProcesses = getCasesBPMDAO().getAllCaseTypes();
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			for (CaseTypesProcDefBind caseTypesProcDefBind : casesProcesses) {
				
				ProcessDefinition pd = ctx.getGraphSession().findLatestProcessDefinition(caseTypesProcDefBind.getProcessDefinitionName());
				menu.addMenuElement(String.valueOf(pd.getId()), pd.getName());
			}
			
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}
	
	public String getSelectedElement(Application app) {
		
		Integer appId = getAppId(app.getPrimaryKey());
		AppProcDefBind bind = getAppBPMDAO().find(AppProcDefBind.class, appId);
		
		if(bind != null) {
			
			JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
			
			try {
				ProcessDefinition pd = ctx.getGraphSession().findLatestProcessDefinition(bind.getProcessDefinitionName());
				return String.valueOf(pd.getId());
				
			} finally {
				getIdegaJbpmContext().closeAndCommit(ctx);
			}
		}
		
		return "-1";
	}

	public AppBPMDAO getAppBPMDAO() {
		return appBPMDAO;
	}

	@Autowired
	public void setAppBPMDAO(AppBPMDAO appBPMDAO) {
		this.appBPMDAO = appBPMDAO;
	}
	
	protected BuilderService getBuilderService(IWApplicationContext iwac) {
		
		try {
			return BuilderServiceFactory.getBuilderService(iwac);
		} catch (RemoteException e) {
			throw new RuntimeException("Failed to resolve builder service", e);
		}
	}

	public String getUrl(IWApplicationContext iwac, Application app) {
		
		Collection<ICPage> icpages = getPages(egovBPMPageType);
		
		ICPage icPage = null;
		
		if(icpages == null || icpages.isEmpty()) {
			
//			TODO: create egov bpm page, as not found
			throw new RuntimeException("No egov bpm page found yet");			
		}
		
		if(icPage == null)
			icPage = icpages.iterator().next();
		
		String uri = icPage.getDefaultPageURI();
		
		if(!uri.startsWith("/pages"))
			uri = "/pages"+uri;
		
		Integer appId = getAppId(app.getPrimaryKey());
		AppProcDefBind appProcDefBind = getAppBPMDAO().find(AppProcDefBind.class, appId);
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			ProcessDefinition pd = ctx.getGraphSession().findLatestProcessDefinition(appProcDefBind.getProcessDefinitionName());
			
			URIUtil uriUtil = new URIUtil(uri);
			uriUtil.setParameter(BPMTaskViewer.PROCESS_DEFINITION_PROPERTY, String.valueOf(pd.getId()));
			uri = uriUtil.getUri();
			return iwac.getIWMainApplication().getTranslatedURIWithContext(uri);
			
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}
	
	public Collection<ICPage> getPages(String pageSubType) {
		
		try {
		
			ICPageHome home = (ICPageHome) IDOLookup.getHome(ICPage.class);
			@SuppressWarnings("unchecked")
			Collection<ICPage> icpages = home.findBySubType(pageSubType, false);
			
			return icpages;
			
		} catch (Exception e) {
			throw new RuntimeException("Exception while resolving icpages by subType: "+pageSubType, e);
		}
	}

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

	@Autowired
	public void setIdegaJbpmContext(IdegaJbpmContext idegaJbpmContext) {
		this.idegaJbpmContext = idegaJbpmContext;
	}
}