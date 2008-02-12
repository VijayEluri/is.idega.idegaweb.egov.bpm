package is.idega.idegaweb.egov.bpm;

import com.idega.idegaweb.IWBundle;
import com.idega.idegaweb.IWBundleStartable;

/**
 * 
 * @author <a href="civilis@idega.com">Vytautas Čivilis</a>
 * @version $Revision: 1.2 $
 *
 * Last modified: $Date: 2008/02/12 14:37:23 $ by $Author: civilis $
 *
 */
public class IWBundleStarter implements IWBundleStartable {
	
	public static final String IW_BUNDLE_IDENTIFIER = "is.idega.idegaweb.egov.bpm";

	public void start(IWBundle starterBundle) {
		
		EgovBPMViewManager viewManager = EgovBPMViewManager.getInstance(starterBundle.getApplication());
		viewManager.initializeStandardNodes(starterBundle);
	}

	public void stop(IWBundle starterBundle) {
	}
}