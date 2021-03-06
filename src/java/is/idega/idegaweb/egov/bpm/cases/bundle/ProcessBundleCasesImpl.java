package is.idega.idegaweb.egov.bpm.cases.bundle;

import is.idega.idegaweb.egov.cases.business.CasesBusiness;
import is.idega.idegaweb.egov.cases.data.CaseCategory;
import is.idega.idegaweb.egov.cases.data.CaseType;

import java.util.Collection;
import java.util.Locale;

import javax.faces.context.FacesContext;

import org.jbpm.graph.def.ProcessDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.business.IBOLookup;
import com.idega.business.IBOLookupException;
import com.idega.business.IBORuntimeException;
import com.idega.core.localisation.business.ICLocaleBusiness;
import com.idega.idegaweb.IWApplicationContext;
import com.idega.idegaweb.IWMainApplication;
import com.idega.idegaweb.egov.bpm.data.CaseTypesProcDefBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.jbpm.bundle.ProcessBundleDefaultImpl;
import com.idega.user.business.GroupBusiness;
import com.idega.user.data.Group;

/**
 *
 * @author <a href="civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.1 $
 *
 *          Last modified: $Date: 2009/01/25 15:45:46 $ by $Author: civilis $
 *
 */
@Scope("prototype")
@Service("casesBPMProcessBundle")
public class ProcessBundleCasesImpl extends ProcessBundleDefaultImpl {

	public static final String defaultCaseTypeName = "BPM";
	public static final String defaultCaseCategoryName = "BPM";
	public static final String defaultCaseHandlersGroupName = "BPM Cases Handlers";

	private CasesBPMDAO casesBPMDAO;

	@Override
	public void configure(ProcessDefinition pd) {

		super.configure(pd);
		assignToDefaultCaseTypes(pd);
	}

	protected void assignToDefaultCaseTypes(ProcessDefinition pd) {

		CaseTypesProcDefBind ctpd = getCasesBPMDAO()
				.getCaseTypesProcDefBindByPDName(pd.getName());

		if (ctpd == null) {

			try {
				String caseCategoryName = defaultCaseCategoryName;
				String caseTypeName = defaultCaseTypeName;
				String caseHandlersGroupName = defaultCaseHandlersGroupName;

				CasesBusiness casesBusiness = getCasesBusiness();
				Collection<CaseCategory> caseCategories = casesBusiness
						.getCaseCategoriesByName(caseCategoryName);
				Collection<CaseType> caseTypes = casesBusiness
						.getCaseTypesByName(caseTypeName);

				CaseCategory caseCategory;
				CaseType caseType;

				if (caseCategories == null || caseCategories.isEmpty()) {

					GroupBusiness groupBusiness = getGroupBusiness();

					Collection<Group> caseHandlersGroups = groupBusiness
							.getGroupsByGroupName(caseHandlersGroupName);
					Group caseHandlersGroup;

					if (caseHandlersGroups == null
							|| caseHandlersGroups.isEmpty()) {

						caseHandlersGroup = groupBusiness.createGroup(
								caseHandlersGroupName,
								"Default bpm cases handlers group");
					} else
						caseHandlersGroup = caseHandlersGroups.iterator()
								.next();

					int localeId = ICLocaleBusiness
							.getLocaleId(new Locale("en"));
					caseCategory = casesBusiness.storeCaseCategory(null, null,
							caseCategoryName, "Default bpm case category",
							caseHandlersGroup, localeId, -1);
				} else {
					caseCategory = caseCategories.iterator().next();
				}

				if (caseTypes == null || caseTypes.isEmpty()) {

					caseType = casesBusiness.storeCaseType(null, caseTypeName,
							"Default bpm case type", -1);

				} else {
					caseType = caseTypes.iterator().next();
				}

				CaseTypesProcDefBind bind = new CaseTypesProcDefBind();
				bind.setCasesCategoryId(new Long(caseCategory.getPrimaryKey()
						.toString()));
				bind.setCasesTypeId(new Long(caseType.getPrimaryKey()
						.toString()));
				bind.setProcessDefinitionName(pd.getName());
				getCasesBPMDAO().persist(bind);

			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	protected CasesBusiness getCasesBusiness() {

		try {
			FacesContext fctx = FacesContext.getCurrentInstance();
			IWApplicationContext iwac;

			if (fctx == null)
				iwac = IWMainApplication.getDefaultIWApplicationContext();
			else
				iwac = IWMainApplication.getIWMainApplication(fctx)
						.getIWApplicationContext();

			return (CasesBusiness) IBOLookup.getServiceInstance(iwac,
					CasesBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	protected GroupBusiness getGroupBusiness() {

		try {
			FacesContext fctx = FacesContext.getCurrentInstance();
			IWApplicationContext iwac;

			if (fctx == null)
				iwac = IWMainApplication.getDefaultIWApplicationContext();
			else
				iwac = IWMainApplication.getIWMainApplication(fctx)
						.getIWApplicationContext();

			return (GroupBusiness) IBOLookup.getServiceInstance(iwac,
					GroupBusiness.class);
		} catch (IBOLookupException ile) {
			throw new IBORuntimeException(ile);
		}
	}

	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	@Autowired
	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}
}