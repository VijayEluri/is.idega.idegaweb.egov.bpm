package is.idega.idegaweb.egov.bpm.cases.exe;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.idega.idegaweb.egov.bpm.data.CaseProcInstBind;
import com.idega.idegaweb.egov.bpm.data.dao.CasesBPMDAO;
import com.idega.util.CoreConstants;
import com.idega.util.IWTimestamp;

/**
 * @author <a href="mailto:civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.1 $
 * 
 *          Last modified: $Date: 2008/09/09 13:55:14 $ by $Author: civilis $
 */
@Scope("singleton")
@Service
public class CaseIdentifier {
	
	public static final String IDENTIFIER_PREFIX = "P";
	
	private CaseIdentifierBean lastCaseIdentifierNumber;
	@Autowired private CasesBPMDAO casesBPMDAO;
	
	public synchronized Object[] generateNewCaseIdentifier() {

		IWTimestamp currentTime = new IWTimestamp();
		currentTime.setAsDate();
		
		CaseIdentifierBean scopedCI;
		
		if (lastCaseIdentifierNumber == null || !currentTime.equals(lastCaseIdentifierNumber.time)) {

			lastCaseIdentifierNumber = new CaseIdentifierBean();

			CaseProcInstBind b = getCasesBPMDAO()
					.getCaseProcInstBindLatestByDateQN(new Date());

			if (b != null && b.getDateCreated() != null
					&& b.getCaseIdentierID() != null) {

				lastCaseIdentifierNumber.time = new IWTimestamp(b
						.getDateCreated());
				lastCaseIdentifierNumber.time.setAsDate();
				lastCaseIdentifierNumber.number = b.getCaseIdentierID();
			} else {

				lastCaseIdentifierNumber.time = currentTime;
				lastCaseIdentifierNumber.time.setAsDate();
				lastCaseIdentifierNumber.number = 0;
			}
		}
		
		scopedCI = lastCaseIdentifierNumber;

		String generated = scopedCI.generate();

		return new Object[] { scopedCI.number, generated };
	}
	
	class CaseIdentifierBean {
		
		IWTimestamp time;
		Integer number;
		
		String generate() {
			
			String nr = String.valueOf(++number);
			
			while(nr.length() < 4)
				nr = "0"+nr;
			
			return new StringBuffer(IDENTIFIER_PREFIX)
			.append(CoreConstants.MINUS)
			.append(time.getYear())
			.append(CoreConstants.MINUS)
			.append(time.getMonth() < 10 ? "0"+time.getMonth() : time.getMonth())
			.append(CoreConstants.MINUS)
			.append(time.getDay() < 10 ? "0"+time.getDay() : time.getDay())
			.append(CoreConstants.MINUS)
			.append(nr)
			.toString();
		}
	}

	public CasesBPMDAO getCasesBPMDAO() {
		return casesBPMDAO;
	}

	public void setCasesBPMDAO(CasesBPMDAO casesBPMDAO) {
		this.casesBPMDAO = casesBPMDAO;
	}
}