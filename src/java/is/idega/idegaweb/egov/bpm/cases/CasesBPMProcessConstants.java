package is.idega.idegaweb.egov.bpm.cases;

import com.idega.jbpm.artifacts.ProcessArtifactsProvider;

/**
 * 
 * @author <a href="civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.4 $
 *
 * Last modified: $Date: 2008/05/30 08:44:11 $ by $Author: valdas $
 *
 */
public class CasesBPMProcessConstants {
	
	private CasesBPMProcessConstants () {}
	
	public static final String actionTakenVariableName = "string:actionTaken";
	public static final String caseIdVariableName = "string:caseId";
	public static final String caseTypeNameVariableName = "string:caseTypeName";
	public static final String caseCategoryNameVariableName = "string:caseCategoryName";
	public static final String caseCreatedDateVariableName = "string:caseCreatedDateString";
	public static final String caseAllocateToVariableName = "string:allocateTo";
	public static final String casePerformerIdVariableName = "string:performerId";
	public static final String caseStatusVariableName = "string:caseStatus";
	public static final String caseOwnerFirstNameVariableName = "string:ownerFirstName";
	public static final String caseOwnerLastNameVariableName = "string:ownerLastName";
	public static final String caseIdentifier = ProcessArtifactsProvider.CASE_IDENTIFIER;
	
	public static final String caseIdentifierNumberParam = "caseIdentifierNumber";
	public static final String userIdActionVariableName = "userId";
	public static final String caseCategoryIdActionVariableName = "caseCategoryId";
	public static final String caseTypeActionVariableName = "caseType";
}