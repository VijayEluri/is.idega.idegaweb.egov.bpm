package is.idega.idegaweb.egov.bpm.servlet;

import is.idega.idegaweb.egov.bpm.cases.board.BoardCasesManagerImpl;
import is.idega.idegaweb.egov.cases.business.BoardCasesManager;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;

import com.idega.presentation.IWContext;
import com.idega.servlet.filter.BaseFilter;
import com.idega.util.StringUtil;
import com.idega.util.expression.ELUtil;

public class BPMTaskRedirector extends BaseFilter implements Filter {
	
	@Autowired
	private BoardCasesManager casesManager;
	
	public void init(FilterConfig arg0) throws ServletException {
	}
	
	public void destroy() {
	}

	public void doFilter(ServletRequest srequest, ServletResponse sresponse, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest request = (HttpServletRequest) srequest;
		HttpServletResponse response = (HttpServletResponse) sresponse;
		
		if (!canRedirect(request)) {
			chain.doFilter(srequest, sresponse);
			return;
		}
		
		String newUrl = getNewRedirectURL(request, response);
		if (StringUtil.isEmpty(newUrl)) {
			Logger.getLogger(getClass().getName()).warning("Couldn't create uri to redirect to task viewer");
			chain.doFilter(srequest, sresponse);
			return;
		}
		response.sendRedirect(newUrl);
	}

	private boolean canRedirect(HttpServletRequest request) {
		@SuppressWarnings("unchecked")
		Map map = request.getParameterMap();
		return map.containsKey(BoardCasesManagerImpl.PROCESS_INSTANCE_ID_PARAMETER) && map.containsKey(BoardCasesManagerImpl.TASK_NAME_PARAMETER);
	}
	
	private String getTaskInstanceId(String processInstanceId, String taskName) {
		if (StringUtil.isEmpty(processInstanceId)) {
			return null;
		}
		
		Long piId = null;
		try {
			piId = Long.valueOf(processInstanceId);
		} catch(NumberFormatException e) {
			e.printStackTrace();
		}
		
		return getCasesManager().getTaskInstanceIdForTask(piId, taskName);
	}
	
	private String getNewRedirectURL(HttpServletRequest request, HttpServletResponse response) {
		IWContext iwc = new IWContext(request, response, request.getSession().getServletContext());
		
		String taskInstanceId = getTaskInstanceId(iwc.getParameter(BoardCasesManagerImpl.PROCESS_INSTANCE_ID_PARAMETER),
				iwc.getParameter(BoardCasesManagerImpl.TASK_NAME_PARAMETER));
		if (StringUtil.isEmpty(taskInstanceId)) {
			return null;
		}
		
		String caseId = iwc.getParameter(BoardCasesManagerImpl.CASE_ID_PARAMETER);
		if (StringUtil.isEmpty(caseId)) {
			return null;
		}
		String backPage = iwc.getParameter(BoardCasesManagerImpl.BACK_PAGE_PARAMETER);
		
		return getCasesManager().getLinkToTheTask(iwc, caseId, taskInstanceId, StringUtil.isEmpty(backPage) ? null : backPage);
	}

	public BoardCasesManager getCasesManager() {
		if (casesManager == null) {
			ELUtil.getInstance().autowire(this);
		}
		return casesManager;
	}

	public void setCasesManager(BoardCasesManager casesManager) {
		this.casesManager = casesManager;
	}

}
