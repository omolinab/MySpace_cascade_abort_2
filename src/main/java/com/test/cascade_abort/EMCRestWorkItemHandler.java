package com.test.cascade_abort;


import org.jbpm.process.workitem.rest.RESTWorkItemHandler;
import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import com.test.cascade_abort.EMCRestAPIException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;


public class EMCRestWorkItemHandler extends RESTWorkItemHandler {

    private static final Logger logger = LoggerFactory.getLogger("com.emc.nems");
    private static final String SUCCESS = "2[0-9][0-9]";
    private static final String STATUS = "status";
    private static final String MESSAGE = "message";
    private static final String RESULT = "result";
    private ClassLoader classLoader;
    
    public EMCRestWorkItemHandler() {
    }

	public EMCRestWorkItemHandler(ClassLoader classLoader) {
		super(classLoader, false);
		this.classLoader = classLoader;
	}

	@Override
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		try {
		    logger.debug("Executing the REST API - " + getURL(workItem) + " - with the payload:");
		    logger.debug(getContentDataAsString(workItem.getParameters()));

			super.executeWorkItem(workItem, manager);
			
		} catch (Exception e) {
		    logger.debug("Exception while executing REST API: " + e.getMessage());
		    if (logger.isDebugEnabled()) {
                logErrorStackTrace(e);
            }
			throw new EMCRestAPIException(e.getCause());
		}
	}
	
	@Override
    protected void postProcessResult(String result, String resultClass, String contentType,
            Map<String, Object> results) {
                
		logger.debug("Response from REST API: " + result);
        
        if (!isEmpty(resultClass) && !isEmpty(contentType)) {
            try {

                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> responseBody =
                        mapper.readValue(result, HashMap.class);

                if (responseBody != null) {
                    Object statusValue = responseBody.get(STATUS);
                    Object messageValue = responseBody.get(MESSAGE);

                    logger.debug("statusValue is :" + statusValue);
                    if (statusValue != null && !statusValue.toString().matches(SUCCESS)) {
                        messageValue = (messageValue!=null) ? messageValue : "null";
                        logger.debug("Exception message from back-end is :" + messageValue);
                        throw new RuntimeException("Exception message from back-end is: " + messageValue.toString());
                    } else if (statusValue == null) {
                        logger.debug("FATAL: API response from back-end is missing the status field.");
                        throw new RuntimeException(
                                "FATAL: API response from back-end is missing the status field.");
                    }
                    
                    results.put("StatusCode", statusValue.toString());
                    results.put("Message", messageValue.toString());

                }
                
                Class<?> clazz = Class.forName(resultClass, true, classLoader);
                Object resultObject = responseBody.get(RESULT);
                if (resultObject == null){
                    throw new RuntimeException(
                            "FATAL: API response from back-end is missing the response data object.");
                }

                resultObject = mapper.convertValue(responseBody.get(RESULT), clazz);
                logger.debug("converted into : " + resultObject.getClass());
                results.put(PARAM_RESULT, resultObject);

            } catch (Throwable e) {
                throw new RuntimeException(e.getMessage(),
                                           e);
            }
        } else {

            results.put(PARAM_RESULT, result);
        }
    }

    static boolean isEmpty(String str) {
        if (str == null || str.trim().length() == 0) {
            return true;
        }

        return false;
    }
	
    private void logErrorStackTrace(Exception e) {
        java.io.Writer buffer = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(buffer);
        e.printStackTrace(pw);
        logger.debug(buffer.toString());
    }
	
	private String getContentDataAsString(Map<String, Object> params) throws Exception {
        String EMPTY_REQUEST_PAYLOAD = "The request payload is empty";
        Object content = EMPTY_REQUEST_PAYLOAD;

        if (params.containsKey(PARAM_CONTENT_DATA) && params.get(PARAM_CONTENT_DATA) != null) {
            content = params.get(PARAM_CONTENT_DATA);
            if (!(content instanceof String)) {
                 ObjectMapper mapper = new ObjectMapper();
                 DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ENGLISH);
                 df.setTimeZone(TimeZone.getTimeZone("Asia/Singapore"));
                 mapper.setDateFormat(df);
                 
                 content = mapper.writeValueAsString(content);
            }
        }

        return (String) content;
    }
    
    private String getURL(WorkItem workItem) {
       return (String) workItem.getParameter("Url");
    }
}