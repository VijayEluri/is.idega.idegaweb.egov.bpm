package is.idega.idegaweb.egov.bpm.cases.actionhandlers.participantinvitation;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.context.FacesContext;

import org.apache.commons.validator.EmailValidator;
import org.jboss.jbpm.IWBundleStarter;
import org.jbpm.graph.exe.ExecutionContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.business.IBORuntimeException;
import com.idega.core.builder.business.BuilderService;
import com.idega.core.builder.business.BuilderServiceFactory;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.IWResourceBundle;
import com.idega.jbpm.data.NativeIdentityBind.IdentityType;
import com.idega.jbpm.exe.BPMFactory;
import com.idega.jbpm.identity.BPMUser;
import com.idega.jbpm.identity.BPMUserImpl;
import com.idega.jbpm.identity.Role;
import com.idega.jbpm.identity.RolesManager;
import com.idega.jbpm.identity.permission.RoleScope;
import com.idega.jbpm.process.business.AssignAccountToParticipantHandler;
import com.idega.presentation.IWContext;
import com.idega.util.CoreConstants;
import com.idega.util.SendMail;
import com.idega.util.URIUtil;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.1 $
 *
 * Last modified: $Date: 2008/05/24 10:22:09 $ by $Author: civilis $
 */
@Scope("singleton")
@Service(SendParticipantInvitationMessageHandlerBean.beanIdentifier)
public class SendParticipantInvitationMessageHandlerBean {

	public static final String beanIdentifier = "jbpm_SendParticipantInvitationMessageHandlerBean";
	public static final String participantEmailVarName = "string:participantEmail";
	public static final String messageVarName = "string:message";
	public static final String subjectVarName = "string:subject";
	public static final String fromEmailVarName = "string:fromEmail";
	//private static final String egovBPMPageType = "bpm_registerProcessParticipant";
	public static final String tokenParam = "bpmtkn";
	public static final String participantRoleNameVarName = AssignAccountToParticipantHandler.participantRoleNameVarName;
	
	private RolesManager rolesManager;
	private BPMFactory bpmFactory;
	
	public void send(ExecutionContext ctx) {
		
		long parentPID = ctx.getProcessInstance().getSuperProcessToken().getProcessInstance().getId();
		BPMUser bpmUser = createAndAssignBPMIdentity(ctx);
		
		final IWContext iwc = IWContext.getIWContext(FacesContext.getCurrentInstance());
		IWResourceBundle iwrb = getResourceBundle(iwc);
		
		String recepientEmail = (String)ctx.getVariable(participantEmailVarName);
		
		if(recepientEmail == null || !EmailValidator.getInstance().isValid(recepientEmail)) {
			
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Participant email address provided is not valid: "+recepientEmail);
			return;
		}
		
//		TODO: think about language choice
		String subject = (String)ctx.getVariable(subjectVarName);
		String message = (String)ctx.getVariable(messageVarName);
		String from = (String)ctx.getVariable(fromEmailVarName);
		
		if(subject == null || CoreConstants.EMPTY.equals(subject)) {
			subject = iwrb.getLocalizedString("cases_bpm.case_invitation", "You've been invited to participate in case");
		}
		
		if(message == null) {
			message = CoreConstants.EMPTY;
		}
		
		if(from == null || CoreConstants.EMPTY.equals(from) || !EmailValidator.getInstance().isValid(from)) {
			from = iwc.getApplicationSettings().getProperty(CoreConstants.PROP_SYSTEM_MAIL_FROM_ADDRESS, "staff@idega.is");
		}
		
		String host = iwc.getApplicationSettings().getProperty(CoreConstants.PROP_SYSTEM_SMTP_MAILSERVER, "mail.idega.is");
		
		
		//String fullUrl = getBuilderService(iwc).getFullPageUrlByPageType(iwc, egovBPMPageType);
		String fullUrl = getBuilderService(iwc).getFullPageUrlByPageType(iwc, "bpm_assets_view");
		
		final URIUtil uriUtil = new URIUtil(fullUrl);
		
		uriUtil.setParameter("piId", String.valueOf(parentPID));
		//uriUtil.setParameter(tokenParam, String.valueOf(tokenId));
		uriUtil.setParameter(BPMUserImpl.bpmUsrParam, String.valueOf(bpmUser.getBpmUser().getPrimaryKey().toString()));
		fullUrl = uriUtil.getUri();
		
//		String fullUrl = composeFullUrl(iwc, ctx.getToken());
		
		message += "\n" + iwrb.getLocalizedAndFormattedString("cases_bpm.case_invitation_message", "Follow the link to register and participate in the case : {0}", new Object[] {fullUrl}) ;
		
		try {
			SendMail.send(from, recepientEmail, null, null, host, subject, message);
		} catch (javax.mail.MessagingException me) {
			Logger.getLogger(getClass().getName()).log(Level.SEVERE, "Exception while sending participant invitation message", me);
		}
	}
	
	protected BPMUser createAndAssignBPMIdentity(ExecutionContext ctx) {
		
		String roleName = (String)ctx.getVariable(participantRoleNameVarName);
		
		if(roleName == null) {
			throw new IllegalArgumentException("Role name not found in process variable: "+participantRoleNameVarName);
		}
		
		Role role = new Role();
		role.setRoleName(roleName);
		role.setScope(RoleScope.PI);
		
		ArrayList<Role> rolz = new ArrayList<Role>(1);
		rolz.add(role);
		
		ProcessInstance parentPI = ctx.getProcessInstance().getSuperProcessToken().getProcessInstance();
		long parentProcessInstanceId = parentPI.getId();
		
		BPMUser bpmUser = getBpmFactory().getBpmUserFactory().createBPMUser(roleName, parentProcessInstanceId);
		
		getRolesManager().createProcessRoles(parentPI.getProcessDefinition().getName(), rolz, parentProcessInstanceId);
		//getRolesManager().createTaskRolesPermissionsPIScope(task, rolz, parentProcessInstanceId);
		getRolesManager().createIdentitiesForRoles(rolz, String.valueOf(bpmUser.getBpmUser().getPrimaryKey()), IdentityType.USER, parentProcessInstanceId);
		
		return bpmUser;
	}
	
	protected IWResourceBundle getResourceBundle(IWContext iwc) {
		IWMainApplication app = iwc.getIWMainApplication();
		IWBundle bundle = app.getBundle(IWBundleStarter.IW_BUNDLE_IDENTIFIER);
		
		if(bundle != null) {
			return bundle.getResourceBundle(iwc);
		} else {
			return null;
		}
	}
	
	protected BuilderService getBuilderService(IWApplicationContext iwc) {
		try {
			return BuilderServiceFactory.getBuilderService(iwc);
			
		} catch (RemoteException e) {
			throw new IBORuntimeException(e);
		}
	}

	public RolesManager getRolesManager() {
		return rolesManager;
	}

	@Autowired
	public void setRolesManager(RolesManager rolesManager) {
		this.rolesManager = rolesManager;
	}
	
	public BPMFactory getBpmFactory() {
		return bpmFactory;
	}

	@Autowired
	public void setBpmFactory(BPMFactory bpmFactory) {
		this.bpmFactory = bpmFactory;
	}
}