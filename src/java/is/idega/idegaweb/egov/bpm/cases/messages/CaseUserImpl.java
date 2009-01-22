package is.idega.idegaweb.egov.bpm.cases.messages;

import is.idega.idegaweb.egov.cases.presentation.OpenCases;

import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.idega.block.process.presentation.UserCases;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.core.builder.business.BuilderService;
import com.idega.core.builder.business.BuilderServiceFactory;
import com.idega.core.contact.data.Email;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.exe.ProcessInstanceW;
import com.idega.jbpm.identity.BPMUser;
import com.idega.jbpm.identity.BPMUserFactory;
import com.idega.jbpm.identity.Role;
import com.idega.jbpm.identity.UserPersonalData;
import com.idega.jbpm.rights.Right;
import com.idega.presentation.IWContext;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.URIParam;
import com.idega.util.URIUtil;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.5 $
 * 
 *          Last modified: $Date: 2009/01/22 11:18:19 $ by $Author: civilis $
 */
public class CaseUserImpl {

	private User user;
	private IWContext iwc;
	private ProcessInstanceW processInstanceW;
	private BPMUserFactory bpmUserFactory;
	private BPMFactory bpmFactory;

	public CaseUserImpl(User user, ProcessInstanceW processInstanceW) {
		this.user = user;
		this.processInstanceW = processInstanceW;
	}

	public CaseUserImpl(User user, ProcessInstanceW processInstanceW,
			IWContext iwc) {
		this.user = user;
		this.processInstanceW = processInstanceW;
		this.iwc = iwc;
	}

	public User getUser() {
		return user;
	}

	/**
	 * 
	 * if the user has login: if the user is handler -> link to opencases else
	 * -> link to usercases else link to case view using this user as bpm user
	 * (see participant invitation)
	 * 
	 * @return
	 */
	public String getUrlToTheCase() {
		
		final IWApplicationContext iwac = IWMainApplication.getDefaultIWApplicationContext();

		UserBusiness userBusiness = getUserBusiness(iwac);
		User user = getUser();
		
		boolean userHasLogin;
		
		try {
			userHasLogin = userBusiness.hasUserLogin(user);
			
		} catch (RemoteException e) {
			throw new IBORuntimeException(e);
		}
		
		String fullUrl;

		if (userHasLogin) {
			
			if (getProcessInstanceW().hasRight(Right.processHandler, user)) {
//				handler, get link to opencases
				fullUrl = getOpenCasesUrl(iwac, user);
			} else {
//				not handler, get link to usercases
				fullUrl = getUserCasesUrl(iwac, user);
			}
			
			final URIUtil uriUtil = new URIUtil(fullUrl);
			uriUtil.setParameter(BPMUser.processInstanceIdParam, String.valueOf(getProcessInstanceW().getProcessInstanceId()));
			fullUrl = uriUtil.getUri();

		} else {
//			hasn't login, get link to case view
			
			UserPersonalData upd = new UserPersonalData();
			
			try {
				Email email = user.getUsersEmail();
				
				if(email != null) {
				
					upd.setUserEmail(email.getEmailAddress());
				}
				
			} catch (RemoteException e) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING, "Exception while resolving user ("+user.getPrimaryKey()+") email");
			}
            
            upd.setFullName(user.getName());
            upd.setUserType(BPMUser.USER_TYPE_NATURAL);
            upd.setPersonalId(user.getPersonalID());
            
            List<Role> roles = getBpmFactory().getRolesManager().getUserRoles(getProcessInstanceW().getProcessInstanceId(), user);
            
            if(roles != null && !roles.isEmpty()) {
            
//            	here we could find the most permissive role of the user, or add all roles for the bpm user (better way)
            	Role role = roles.iterator().next();
				
				BPMUser bpmUser = getBpmUserFactory().createBPMUser(upd, role, getProcessInstanceW().getProcessInstanceId());

				fullUrl = getAssetsUrl(iwac, user);
				
				final URIUtil uriUtil = new URIUtil(fullUrl);
				List<URIParam> params = bpmUser.getParamsForBPMUserLink();
				
				for (URIParam param : params) {
	                uriUtil.setParameter(param.getParamName(), param.getParamValue());
                }
				
				fullUrl = uriUtil.getUri();
            } else {
            	fullUrl = null;
            	Logger.getLogger(getClass().getName()).log(Level.WARNING, "No roles resolved for the user with id="+user.getPrimaryKey()+" and process instance id = "+getProcessInstanceW().getProcessInstanceId()+", skipping creating url to the case");
            }
		}

		return fullUrl;
	}

	ProcessInstanceW getProcessInstanceW() {
		return processInstanceW;
	}

	public void setIwc(IWContext iwc) {
		this.iwc = iwc;
	}

	protected UserBusiness getUserBusiness(IWApplicationContext iwac) {
		try {
			return (UserBusiness) IBOLookup.getServiceInstance(iwac,
					UserBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	private String getAssetsUrl(IWApplicationContext iwac, User currentUser) {
		return getBuilderService(iwac).getFullPageUrlByPageType(currentUser, BPMUser.defaultAssetsViewPageType, true);
	}
	
	private String getOpenCasesUrl(IWApplicationContext iwac, User user) {
		return getBuilderService(iwac).getFullPageUrlByPageType(user, OpenCases.pageType, true);
	}
	
	private String getUserCasesUrl(IWApplicationContext iwac, User currentUser) {
		return getBuilderService(iwac).getFullPageUrlByPageType(currentUser, UserCases.pageType, true);
	}
	
	private BuilderService getBuilderService(IWApplicationContext iwc) {
		try {
			return BuilderServiceFactory.getBuilderService(iwc);
			
		} catch (RemoteException e) {
			throw new IBORuntimeException(e);
		}
	}

	public void setBpmUserFactory(BPMUserFactory bpmUserFactory) {
		this.bpmUserFactory = bpmUserFactory;
	}

	BPMUserFactory getBpmUserFactory() {
		return bpmUserFactory;
	}

	BPMFactory getBpmFactory() {
		return bpmFactory;
	}

	public void setBpmFactory(BPMFactory bpmFactory) {
		this.bpmFactory = bpmFactory;
	}
}