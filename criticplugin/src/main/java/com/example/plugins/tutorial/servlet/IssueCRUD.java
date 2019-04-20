package com.example.plugins.tutorial.servlet;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.bc.project.ProjectService;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.attachment.CreateAttachmentParamsBean;
import com.atlassian.jira.issue.AttachmentManager;
import com.atlassian.jira.jql.builder.JqlClauseBuilder;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.web.util.AttachmentException;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import com.atlassian.templaterenderer.TemplateRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.io.FileUtils;

import java.net.URL;
import java.net.HttpURLConnection;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.util.Iterator;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Date;

@Scanned
public class IssueCRUD extends HttpServlet {
    private static final Logger log = LoggerFactory.getLogger(IssueCRUD.class);

    @JiraImport
    private IssueService issueService;
    @JiraImport
    private ProjectService projectService;
    @JiraImport
    private SearchService searchService;
    @JiraImport
    private TemplateRenderer templateRenderer;
    @JiraImport
    private JiraAuthenticationContext authenticationContext;
    @JiraImport
    private ConstantsManager constantsManager;
    @JiraImport
    private AttachmentManager attachmentManager;

    private static final String LIST_ISSUES_TEMPLATE = "/templates/list.vm";
    private static final String NEW_ISSUE_TEMPLATE = "/templates/new.vm";
    private static final String EDIT_ISSUE_TEMPLATE = "/templates/edit.vm";

    public IssueCRUD(IssueService issueService, ProjectService projectService,
                     SearchService searchService,
                     TemplateRenderer templateRenderer,
                     JiraAuthenticationContext authenticationContext,
                     ConstantsManager constantsManager,
                     AttachmentManager attachmentManager) {
        this.issueService = issueService;
        this.projectService = projectService;
        this.searchService = searchService;
        this.templateRenderer = templateRenderer;
        this.authenticationContext = authenticationContext;
        this.constantsManager = constantsManager;
        this.attachmentManager = attachmentManager;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = Optional.ofNullable(req.getParameter("actionType")).orElse("");
        Collection<IssueType> issueTypes = constantsManager.getRegularIssueTypeObjects();
        Map<String, Object> context = new HashMap<>();
        resp.setContentType("text/html;charset=utf-8");
        switch (action) {
            case "new":
                context.put("issueTypes", issueTypes);
                templateRenderer.render(NEW_ISSUE_TEMPLATE, context, resp.getWriter());
                break;
            case "edit":
                IssueService.IssueResult issueResult = issueService.getIssue(authenticationContext.getLoggedInUser(),
                        req.getParameter("key"));
                context.put("issue", issueResult.getIssue());
                context.put("issueTypes", issueTypes);
                templateRenderer.render(EDIT_ISSUE_TEMPLATE, context, resp.getWriter());
                break;
            default:
                List<Issue> issues = getIssues();
                context.put("issues", issues);
                context.put("issueTypes", issueTypes);
                templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
        }

    }

    /**
     * Retrieve issues using simple JQL query project="DEMO"
     * Pagination is set to unlimited
     *
     * @return List of issues
     */
    private List<Issue> getIssues() {

        ApplicationUser user = authenticationContext.getLoggedInUser();
        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
        Query query = jqlClauseBuilder.project("DEMO").buildQuery();
        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();

        SearchResults searchResults = null;
        try {
            searchResults = searchService.search(user, query, pagerFilter);
        } catch (SearchException e) {
            e.printStackTrace();
        }
        return searchResults != null ? searchResults.getIssues() : null;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String actionType = req.getParameter("actionType");

        switch (actionType) {
            case "edit":
                handleIssueEdit(req, resp);
                break;
            case "new":
                handleIssueCreation(req, resp);
                break;
            default:
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleIssueEdit(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        ApplicationUser user = authenticationContext.getLoggedInUser();

        Map<String, Object> context = new HashMap<>();

        IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        issueInputParameters.setSummary(req.getParameter("summary"))
                .setDescription(req.getParameter("description"));

        MutableIssue issue = issueService.getIssue(user, req.getParameter("key")).getIssue();

        IssueService.UpdateValidationResult result =
                issueService.validateUpdate(user, issue.getId(), issueInputParameters);

        if (result.getErrorCollection().hasAnyErrors()) {
            context.put("issue", issue);
            context.put("errors", result.getErrorCollection().getErrors());
            resp.setContentType("text/html;charset=utf-8");
            templateRenderer.render(EDIT_ISSUE_TEMPLATE, context, resp.getWriter());
        } else {
            issueService.update(user, result);
            resp.sendRedirect("issuecrud");
        }
    }

    // Makes GET call to the provided url and, if successful, stores the result in a string.
    public String getJSON(String url, int timeout) {
        HttpURLConnection c = null;
        int status = 0;
        try {
            // Make URL object using provided url string, verifying that the URL is properly formatted.
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            // Make connection.
            c.connect();
            status = c.getResponseCode();

            switch (status) {
                // If the connection is successfully made, the response code will be 200 or 201.
                case 200:
                case 201:
                    // If a 200 or 201 is received, read the data into a String and return it.
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    return sb.toString();
            }
        // Catch and handle any errors that might occur.
        } catch (MalformedURLException ex) {
            //context.put("errors", ex.getMessage());
        } catch (IOException ex) {
            //context.put("errors", ex.getMessage());
        } finally {
            if (c != null) {
                try {
                    // Attempt to disconnect.
                    c.disconnect();
                // Catch and handle any errors that might have occured in disconnecting.
                } catch (Exception ex) {
                    //context.put("errors", ex.getMessage());
                }
            }
        }
        return null;
    }

    public static Map<String, Object> toMap(JSONObject jsonobj) throws JSONException {
        Map<String, Object> map = new HashMap<String, Object>();
        Iterator<String> keys = jsonobj.keys();
        while(keys.hasNext()) {
            String key = keys.next();
            Object value = jsonobj.get(key);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List<Object> toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<Object>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if (value instanceof JSONArray) {
                value = toList((JSONArray) value);
            } else if (value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }

    // Checks if issue for the given id already exists.
    public boolean issueExists(int id) {
        // Get all Issues belonging to the project.
        List<Issue> issues = getIssues();
        Iterator<Issue> issueItr = issues.iterator();
        // Iterate through all issues until there are either no more issues, or a duplicate has been found.
        while(issueItr.hasNext()) {
            Issue issue = issueItr.next();
            String description = issue.getDescription();
            // Parse the description of this Issue to determine if the Issue's ID matches the provided ID parameter.
            if (description.length() > 4) {
                if (description.substring(0, 2).equals("ID")) {
                    String idNum = description.substring(4);
                    if (idNum.contains("D") && !idNum.substring(0, 1).equals("D")) {
                        idNum = idNum.substring(0, idNum.indexOf("D") - 1);
                        // If the ID's are equal, a duplicate has been found.
                        if (idNum.equals(Integer.toString(id))) {
                            // Return true because a duplicate was found.
                            return true;
                        }
                    }
                }
            }
        }
        // Return false because all issues were iterated through and no duplicate was found.
        return false;
    }

    // Formats name for display in the Issue description. For example, turns "app_id" into "App ID"
    public String formatName(String fieldName) {
        // Make a copy of the provided parameter fieldName.
        String newName = fieldName;
        // Replace all underscores with spaces, and capitalize each word separated by an underscore.
        while (newName.contains("_")){
            String firstWord = newName.substring(0, newName.indexOf("_"));
            firstWord = firstWord.substring(0, 1).toUpperCase() + firstWord.substring(1);
            // If a word is ID, capitalize it.
            if (firstWord.equals("Id")) {
                firstWord = "ID";
            }
            String secondWord = "";
            if (newName.substring(newName.indexOf("_")).length() > 1) {
                secondWord = newName.substring(newName.indexOf("_") + 1);
                secondWord = secondWord.substring(0, 1).toUpperCase() + secondWord.substring(1);
                if (secondWord.equals("Id")) {
                    secondWord = "ID";
                }
            }
            newName = firstWord + " " + secondWord;
        }
        // If a word is ID, capitalize it.
        if (newName.equals("id") || newName.equals("Id")) {
            newName = "ID";
        } else if (newName.length() > 1) {
            newName = newName.substring(0, 1).toUpperCase() + newName.substring(1);
        }
        // Return the new, formatted name.
        return newName;
    }

    // Method to handle Issue creation. Makes all neccessary GET requests to gather relevant data from Critic, and makes
    // Issues for bug reports that do not yet have Issues.
    private void handleIssueCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        Map<String, Object> context = new HashMap<>();
        // Get the App API Token from the interface form submission.
        // String app_api_token = req.getParameter("token");
        String app_api_token = "2PUeap7YiZKucEofuCHRJap5";
        // Call getJSON method and store data string into 'data'.
        String data = getJSON("https://critic.inventiv.io/api/v2/bug_reports?app_api_token=" + app_api_token, 4000);
        // Create a new JSONObject for the string 'data'.
        JSONObject dataObj = new JSONObject(data);
        Project project = projectService.getProjectByKey(user, "DEMO").getProject();
        // If the project is invalid or the Issue Type cannot be found, throw an error and render the list template.
        if (project == null) {
            context.put("errors", Collections.singletonList("Project doesn't exist"));
            templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
            return;
        }
        IssueType taskIssueType = constantsManager.getAllIssueTypeObjects().stream().filter(
                issueType -> issueType.getName().equalsIgnoreCase("task")).findFirst().orElse(null);
        if(taskIssueType == null) {
            context.put("errors", Collections.singletonList("Can't find Task issue type"));
            templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
            return;
        }
        IssueInputParameters issueInputParameters = null;
        Date myDate = new Date();
        // For each page of bug reports, create detailed Issues for each bug report on the page.
        for (int currentPage = dataObj.getInt("current_page"); currentPage <= dataObj.getInt("total_pages"); currentPage++) {
            JSONObject pageDataObj = null;
            String pageData = null;
            if (currentPage == 1) {
                pageData = data;
                pageDataObj = dataObj;
            } else {
                // If there is a page greater than 1, make a new GET call to retrieve the bug reports on that page.
                pageData = getJSON("https://critic.inventiv.io/api/v2/bug_reports?app_api_token="
                        + app_api_token + "&page=" + currentPage, 4000);
                pageDataObj = new JSONObject(pageData);
            }
            // Store all of the bug reports onto the page into the 'bugReports' object.
            JSONArray bugReports = pageDataObj.getJSONArray("bug_reports");
            for (int reportNum = 0; reportNum < pageDataObj.getInt("count"); reportNum++) {
                JSONObject report = bugReports.getJSONObject(reportNum);
                // Check if the Issue for this bug report already exists using the issueExists method.
                if (!issueExists(report.getInt("id"))) {
                    // Make another GET request to get additional details about the bug report.
                    String details = getJSON("https://critic.inventiv.io/api/v2/bug_reports/" + report.getInt("id")
                            + "?app_api_token=" + app_api_token, 4000);
                    JSONObject bugDetails = new JSONObject(details);
                    issueInputParameters = issueService.newIssueInputParameters();
                    // Populate the description field with bug report ID and other relevant info.
                    String description = "ID: " + report.getInt("id")
                            + "\nDescription: " + report.getString("description")
                            + "\nCreated at: " + report.getString("created_at")
                            + "\nUpdated at: " + report.getString("updated_at")
                            + "\nLink to Critic Web Portal: https://critic.inventiv.io/bug_reports/" + report.getInt("id");
                    if (!report.isNull("steps_to_reproduce")) {
                        description = description + "\nSteps to Reproduce: " + report.get("steps_to_reproduce");
                    }
                    if (!report.isNull("user_identifier")) {
                        description = description + "\nUser Identifier: " + report.get("user_identifier");
                    }
                    // Parse metadata and add to description.
                    if (!report.isNull("metadata")) {
                        JSONObject metadata = report.getJSONObject("metadata");
                        JSONArray metadataNames = metadata.names();
                        boolean header = false;
                        for (int metadataNum = 0; metadataNum < metadata.length(); metadataNum++) {
                            String fieldName = metadataNames.getString(metadataNum);
                            // Format name of metadata field to be readable.
                            String newName = formatName(fieldName);
                            if (!header) {
                                header = true;
                                description = description + "\n\nMetadata:";
                            }
                            description = description + "\n" + newName + ": " + metadata.get(fieldName);
                        }
                    }
                    // If app version data is requested, append app version data to description.
                    if (req.getParameterValues("app_version_data").length == 2) {
                        if (!bugDetails.isNull("app_version")) {
                            JSONObject appVersion = bugDetails.getJSONObject("app_version");
                            JSONArray appVersionNames = appVersion.names();
                            boolean header = false;
                            for (int appVersionNum = 0; appVersionNum < appVersion.length(); appVersionNum++) {
                                String fieldName = appVersionNames.getString(appVersionNum);
                                String newName = formatName(fieldName);
                                if (!header) {
                                    header = true;
                                    description = description + "\n\nApp Version Data:";
                                }
                                description = description + "\n" + newName + ": " + appVersion.get(fieldName);
                            }
                        }
                    }
                    // If device data is requested, append device data to description.
                    if (req.getParameterValues("device_data").length == 2) {
                        if (!bugDetails.isNull("device")) {
                            JSONObject device = bugDetails.getJSONObject("device");
                            JSONArray deviceNames = device.names();
                            boolean header = false;
                            for (int deviceNum = 0; deviceNum < device.length(); deviceNum++) {
                                String fieldName = deviceNames.getString(deviceNum);
                                String newName = formatName(fieldName);
                                if (!header) {
                                    header = true;
                                    description = description + "\n\nDevice Data:";
                                }
                                description = description + "\n" + newName + ": " + device.get(fieldName);
                            }
                        }
                    }
                    // Set all Issue attributes.
                    issueInputParameters.setSummary(report.getString("description"))
                            .setDescription(description)
                            .setAssigneeId(user.getName())
                            .setReporterId(user.getName())
                            .setProjectId(project.getId())
                            .setIssueTypeId(req.getParameter("type"))
                            .setPriorityId(req.getParameter("priority"));
                    // Validate that the issue is formatted correctly.
                    IssueService.CreateValidationResult result = issueService.validateCreate(user, issueInputParameters);
                    // If formatted incorrectly, return an error and do not create the issue.
                    if (result.getErrorCollection().hasAnyErrors()) {
                        List<Issue> issues = getIssues();
                        context.put("issues", issues);
                        context.put("errors", result.getErrorCollection().getErrors());
                        resp.setContentType("text/html;charset=utf-8");
                        templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
                    // If formatted correctly:
                    } else {
                        // Create the issue.
                        IssueService.IssueResult issue = issueService.create(user, result);
                        JSONArray attachments = bugDetails.getJSONArray("attachments");
                        // Iterate through all attachments.
                        for (int attachmentNum = 0; attachmentNum < attachments.length(); attachmentNum++) {
                            JSONObject attachmentInfo = attachments.getJSONObject(attachmentNum);
                            // Download attachment data into File object.
                            File attachmentLoc = new File(attachmentInfo.getString("file_file_name"));
                            URL attachmentUrl = new URL("https:" + attachmentInfo.getString("file_url"));
                            FileUtils.copyURLToFile(attachmentUrl, attachmentLoc);
                            // Store attachment data as well as relevant attributes into CreateAttachmentParamsBean object.
                            CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean(attachmentLoc,
                                    attachmentInfo.getString("file_file_name"),
                                    attachmentInfo.getString("file_content_type"),
                                    user, issue.getIssue(), false, false, null,
                                    //new Date(attachmentInfo.getString("file_updated_at")), true);
                                    myDate, true);
                            try {
                                // Attach the attachment.
                                attachmentManager.createAttachment(bean);
                            // Handle errors.
                            } catch (AttachmentException ex) {
                                //context.put("errors", ex.getMessage());
                            }
                        }
                    }
                }
            }
        }
        // Redirect back to the list page.
        resp.sendRedirect("issuecrud");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        String respStr;
        IssueService.IssueResult issueResult = issueService.getIssue(user, req.getParameter("key"));
        if (issueResult.isValid()) {
            IssueService.DeleteValidationResult result = issueService.validateDelete(user, issueResult.getIssue().getId());
            if (result.getErrorCollection().hasAnyErrors()) {
                respStr = "{ \"success\": \"false\", error: \"" + result.getErrorCollection().getErrors().get(0) + "\" }";
            } else {
                issueService.delete(user, result);
                respStr = "{ \"success\" : \"true\" }";
            }
        } else {
            respStr = "{ \"success\" : \"false\", error: \"Couldn't find issue\"}";
        }
        resp.setContentType("application/json;charset=utf-8");
        resp.getWriter().write(respStr);
    }
}