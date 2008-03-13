package is.idega.idegaweb.egov.bpm.cases;

import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.GeneralCase;

import java.io.Serializable;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.context.FacesContext;

import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.taskmgmt.exe.TaskInstance;

import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.jbpm.IdegaJbpmContext;
import com.idega.presentation.IWContext;
import com.idega.util.CoreConstants;
import com.idega.util.IWTimestamp;


/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.2 $
 *
 * Last modified: $Date: 2008/03/13 12:06:18 $ by $Author: civilis $
 */
public class CasesBPMProcessView {
	
	private IdegaJbpmContext idegaJbpmContext;

	public CasesBPMTaskViewBean getTaskView(long taskInstanceId) {

		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			TaskInstance ti = ctx.getTaskInstance(taskInstanceId);
			
			if(ti == null) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING, "No task instance found for task instance id provided: "+taskInstanceId);
				return new CasesBPMTaskViewBean();
			}
			
			CasesBPMTaskViewBean bean = new CasesBPMTaskViewBean();
			bean.setTaskName(ti.getName());
			bean.setTaskStatus("Unknown");
			bean.setAssignedTo("Unknown");
			bean.setCreatedDate(ti.getCreate());
			return bean;
			
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}
	
	public CasesBPMProcessViewBean getProcessView(long processInstanceId, int caseId) {
		
		JbpmContext ctx = getIdegaJbpmContext().createJbpmContext();
		
		try {
			ProcessInstance pi = ctx.getProcessInstance(processInstanceId);
			
			if(pi == null) {
				Logger.getLogger(getClass().getName()).log(Level.WARNING, "No process instance found for process instance id provided: "+processInstanceId);
				return new CasesBPMProcessViewBean();
			}
			
			String ownerFirstName = (String)pi.getContextInstance().getVariable(CasesBPMProcessConstants.caseOwnerFirstNameVariableName);
			String ownerLastName = (String)pi.getContextInstance().getVariable(CasesBPMProcessConstants.caseOwnerLastNameVariableName);
			String ownerName = new StringBuffer(ownerFirstName == null ? CoreConstants.EMPTY : ownerFirstName)
				.append(ownerFirstName != null && ownerLastName != null ? CoreConstants.SPACE : CoreConstants.EMPTY)
				.append(ownerLastName == null ? CoreConstants.EMPTY : ownerLastName)
				.toString();
			
			IWContext iwc = IWContext.getIWContext(FacesContext.getCurrentInstance());
			
			String processStatus = pi.hasEnded() ? "Ended" : "In progress";
			IWTimestamp time = new IWTimestamp(pi.getStart().toString());
			String createDate = time.getLocaleDate(iwc.getLocale());
			
			String caseCategory;
			String caseType;
			
			try {
				GeneralCase genCase = getCaseBusiness(iwc).getGeneralCase(new Integer(caseId));
				caseCategory = genCase.getCaseCategory().getLocalizedCategoryName(iwc.getLocale());
				caseType = genCase.getCaseType().getName();
				
			} catch (Exception e) {
				caseCategory = null;
				caseType = null;
			}
			
			CasesBPMProcessViewBean bean = new CasesBPMProcessViewBean();
			bean.setProcessName(pi.getProcessDefinition().getName());
			bean.setProcessStatus(processStatus);
			bean.setProcessOwner(ownerName);
			bean.setProcessCreateDate(createDate);
			bean.setCaseCategory(caseCategory);
			bean.setCaseType(caseType);
			return bean;
			
		} finally {
			getIdegaJbpmContext().closeAndCommit(ctx);
		}
	}
	
	public class CasesBPMProcessViewBean implements Serializable {
		
		private static final long serialVersionUID = -1209671586005809408L;
		
		private String processName;
		private String processStatus;
		private String processOwner;
		private String processCreateDate;
		private String caseCategory;
		private String caseType;
		
		public String getProcessOwner() {
			return processOwner;
		}
		public void setProcessOwner(String processOwner) {
			this.processOwner = processOwner;
		}
		public String getProcessCreateDate() {
			return processCreateDate;
		}
		public void setProcessCreateDate(String processCreateDate) {
			this.processCreateDate = processCreateDate;
		}
		public String getProcessName() {
			return processName;
		}
		public void setProcessName(String processName) {
			this.processName = processName;
		}
		public String getProcessStatus() {
			return processStatus;
		}
		public void setProcessStatus(String processStatus) {
			this.processStatus = processStatus;
		}
		public String getCaseCategory() {
			return caseCategory;
		}
		public void setCaseCategory(String caseCategory) {
			this.caseCategory = caseCategory;
		}
		public String getCaseType() {
			return caseType;
		}
		public void setCaseType(String caseType) {
			this.caseType = caseType;
		}
	}
	
	public class CasesBPMTaskViewBean implements Serializable {
		
		private static final long serialVersionUID = -6402627297789228878L;
		
		private String taskName;
		private String taskStatus;
		private String assignedTo;
		private Date createdDate;
		
		public String getTaskName() {
			return taskName;
		}
		public void setTaskName(String taskName) {
			this.taskName = taskName;
		}
		public String getTaskStatus() {
			return taskStatus;
		}
		public void setTaskStatus(String taskStatus) {
			this.taskStatus = taskStatus;
		}
		public String getAssignedTo() {
			return assignedTo;
		}
		public void setAssignedTo(String assignedTo) {
			this.assignedTo = assignedTo;
		}
		public Date getCreatedDate() {
			return createdDate;
		}
		public void setCreatedDate(Date createdDate) {
			this.createdDate = createdDate;
		}
	}

	public IdegaJbpmContext getIdegaJbpmContext() {
		return idegaJbpmContext;
	}

	public void setIdegaJbpmContext(IdegaJbpmContext idegaJbpmContext) {
		this.idegaJbpmContext = idegaJbpmContext;
	}
	
	protected CasesBusiness getCaseBusiness(IWContext iwc) {
		
		try {
			return (CasesBusiness)IBOLookup.getServiceInstance(iwc, CasesBusiness.class);
		}
		catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}
}