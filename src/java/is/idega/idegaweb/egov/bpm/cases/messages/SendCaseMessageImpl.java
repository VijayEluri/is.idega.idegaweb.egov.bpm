package is.idega.idegaweb.egov.bpm.cases.messages;

import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;
import is.idega.idegaweb.egov.message.business.CommuneMessageBusiness;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.FinderException;

import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.graph.exe.Token;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.block.process.message.data.Message;
import com.idega.bpm.process.messages.LocalizedMessages;
import com.idega.bpm.process.messages.SendMailMessageImpl;
import com.idega.bpm.process.messages.SendMessageType;
import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.jbpm.exe.ProcessInstanceW;
import com.idega.jbpm.process.business.messages.MessageValueContext;
import com.idega.presentation.IWContext;
import com.idega.user.business.UserBusiness;
import com.idega.user.data.User;
import com.idega.util.CoreConstants;


/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.3 $
 *
 * Last modified: $Date: 2008/09/29 13:22:07 $ by $Author: arunas $
 */
@Scope("singleton")
@SendMessageType("caseMessage")
@Service
public class SendCaseMessageImpl extends SendMailMessageImpl {
	
	public void send(final Object context, final ProcessInstance pi, final LocalizedMessages msgs, final Token tkn) {
		
		final Integer caseId = (Integer)context;
	
		final IWContext iwc = IWContext.getCurrentInstance();
		final IWApplicationContext iwac;
		final IWMainApplication iwma;

		if(iwc != null)
			iwma = IWMainApplication.getIWMainApplication(iwc);
		else
			iwma = IWMainApplication.getDefaultIWMainApplication();
		
		iwac = iwma.getIWApplicationContext();
		
		final CommuneMessageBusiness messageBusiness = getCommuneMessageBusiness(iwac);
		final UserBusiness userBusiness  = getUserBusiness(iwac);
		
		final Locale defaultLocale = iwma.getDefaultLocale();
		
		new Thread(new Runnable() {

			public void run() {
				
				try {
					CasesBusiness casesBusiness = getCasesBusiness(iwac);
					
					final GeneralCase theCase = casesBusiness.getGeneralCase(caseId);
					Collection<User> users = getUsersToSendMessageTo(msgs.getSendToRoles(), pi);
					
					long pid = pi.getId();
					ProcessInstanceW piw = getBpmFactory().getProcessManagerByProcessInstanceId(pid).getProcessInstance(pid);
					
					HashMap<Locale, String[]> unformattedForLocales = new HashMap<Locale, String[]>(5);
					MessageValueContext mvCtx = new MessageValueContext(5);
					
					for (User user : users) {
						
						Locale preferredLocale = userBusiness.getUsersPreferredLocale(user);
						
						if(preferredLocale == null)
							preferredLocale = defaultLocale;
						
						mvCtx.setValue(MessageValueContext.userBean, user);
						mvCtx.setValue(MessageValueContext.piwBean, piw);

						String[] subjNMsg = getFormattedMessage(mvCtx, preferredLocale, msgs, unformattedForLocales, tkn);
						
						String subject = subjNMsg[0];
						String text = subjNMsg[1];
						
						Message message = messageBusiness.createUserMessage(theCase, user, null, null, subject, text, text, null, false, null, false, true);
						message.store();
					}
					
				} catch (RemoteException e) {
					Logger.getLogger(SendCaseMessagesHandler.class.getName()).log(Level.SEVERE, "Exception while sending user message, some messages might be not sent", e);
				} catch (FinderException e) {
					Logger.getLogger(SendCaseMessagesHandler.class.getName()).log(Level.SEVERE, "Exception while sending user message, some messages might be not sent", e);
				}
			}
			
		}).start();
	}
	
	protected CommuneMessageBusiness getCommuneMessageBusiness(IWApplicationContext iwac) {
		try {
			return (CommuneMessageBusiness)IBOLookup.getServiceInstance(iwac, CommuneMessageBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	protected UserBusiness getUserBusiness(IWApplicationContext iwac) {
		try {
			return (UserBusiness)IBOLookup.getServiceInstance(iwac, UserBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	protected CasesBusiness getCasesBusiness(IWApplicationContext iwac) {
		try {
			return (CasesBusiness) IBOLookup.getServiceInstance(iwac, CasesBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
	
	public String getFormattedMessage(String unformattedMessage, String messageValues, Token tkn, MessageValueContext mvCtx) {
		
		return getMessageValueHandler().getFormattedMessage(unformattedMessage, messageValues, tkn, mvCtx);
	}
	
	public Collection<User> getUsersToSendMessageTo(String rolesNamesAggr, ProcessInstance pi) {
		
		Collection<User> allUsers;
		
		if(rolesNamesAggr != null) {
		
			String[] rolesNames = rolesNamesAggr.trim().split(CoreConstants.SPACE);
			
			HashSet<String> rolesNamesSet = new HashSet<String>(rolesNames.length);
			
			for (int i = 0; i < rolesNames.length; i++)
				rolesNamesSet.add(rolesNames[i]);
			
			allUsers = getBpmFactory().getRolesManager().getAllUsersForRoles(rolesNamesSet, pi.getId());
		} else
			allUsers = new ArrayList<User>(0);
		
		return allUsers;
	}
}