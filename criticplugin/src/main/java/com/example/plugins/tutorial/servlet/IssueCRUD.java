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

    public String getJSON(String url, int timeout) {
        HttpURLConnection c = null;
        int status = 0;
        try {
            URL u = new URL(url);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("GET");
            c.setRequestProperty("Content-length", "0");
            c.setUseCaches(false);
            c.setAllowUserInteraction(false);
            c.setConnectTimeout(timeout);
            c.setReadTimeout(timeout);
            c.connect();
            status = c.getResponseCode();

            switch (status) {
                case 200:
                case 201:
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line+"\n");
                    }
                    br.close();
                    return sb.toString();
            }
        } catch (MalformedURLException ex) {
        } catch (IOException ex) {
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
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

    public boolean issueExists(int id) {
        List<Issue> issues = getIssues();
        Iterator<Issue> issueItr = issues.iterator();
        boolean found = false;
        while(issueItr.hasNext() && !found) {
            Issue issue = issueItr.next();
            String description = issue.getDescription();
            if (description.length() > 4) {
                if (description.substring(0, 2).equals("ID")) {
                    String idNum = description.substring(4);
                    if (idNum.contains("D") && !idNum.substring(0, 1).equals("D")) {
                        idNum = idNum.substring(0, idNum.indexOf("D") - 1);
                        if (idNum.equals(Integer.toString(id))) {
                            found = true;
                        }
                    }
                }
            }
        }
        return found;
    }

    private void handleIssueCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();
        Map<String, Object> context = new HashMap<>();
        String app_api_token = "2PUeap7YiZKucEofuCHRJap5";
        String data = getJSON("https://critic.inventiv.io/api/v2/bug_reports?app_api_token=" + app_api_token, 4000);
        JSONObject dataObj = new JSONObject(data);
        Project project = projectService.getProjectByKey(user, "DEMO").getProject();
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
        for (int currentPage = dataObj.getInt("current_page"); currentPage <= dataObj.getInt("total_pages"); currentPage++) {
            JSONObject pageDataObj = null;
            String pageData = null;
            if (currentPage == 1) {
                pageData = data;
                pageDataObj = dataObj;
            } else {
                pageData = getJSON("https://critic.inventiv.io/api/v2/bug_reports?app_api_token="
                        + app_api_token + "&page=" + currentPage, 4000);
                pageDataObj = new JSONObject(pageData);
            }
            JSONArray bugReports = pageDataObj.getJSONArray("bug_reports");
            for (int reportNum = 0; reportNum < pageDataObj.getInt("count"); reportNum++) {
                JSONObject report = bugReports.getJSONObject(reportNum);
                if (!issueExists(report.getInt("id"))) {
                    String details = getJSON("https://critic.inventiv.io/api/v2/bug_reports/" + report.getInt("id")
                            + "?app_api_token=" + app_api_token, 4000);
                    JSONObject bugDetails = new JSONObject(details);
                    issueInputParameters = issueService.newIssueInputParameters();
                    String description = "ID: " + report.getInt("id")
                            + "\nDescription: " + report.getString("description")
                            + "\nCreated at: " + report.getString("created_at")
                            + "\nUpdated at: " + report.getString("updated_at");
                    if (!report.isNull("steps_to_reproduce")) {
                        description = description + "\nSteps to Reproduce: " + report.get("steps_to_reproduce");
                    }
                    if (!report.isNull("user_identifier")) {
                        description = description + "\nUser Identifier: " + report.get("user_identifier");
                    }
                    if (!report.isNull("metadata")) {
                        JSONObject metadata = report.getJSONObject("metadata");
                        JSONArray metadataNames = metadata.names();
                        for (int metadataNum = 0; metadataNum < metadata.length(); metadataNum++) {
                            String fieldName = metadataNames.getString(metadataNum);
                            description = description + "\n" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1)
                                    + ": " + metadata.get(fieldName);
                        }
                    }
                    issueInputParameters.setSummary(report.getString("description"))
                            .setDescription(description)
                            .setAssigneeId(user.getName())
                            .setReporterId(user.getName())
                            .setProjectId(project.getId())
                            .setIssueTypeId(req.getParameter("type"))
                            .setPriorityId(req.getParameter("priority"));
                    IssueService.CreateValidationResult result = issueService.validateCreate(user, issueInputParameters);
                    if (result.getErrorCollection().hasAnyErrors()) {
                        List<Issue> issues = getIssues();
                        context.put("issues", issues);
                        context.put("errors", result.getErrorCollection().getErrors());
                        resp.setContentType("text/html;charset=utf-8");
                        templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
                    } else {
                        IssueService.IssueResult issue = issueService.create(user, result);
                        JSONArray attachments = bugDetails.getJSONArray("attachments");
                        for (int attachmentNum = 0; attachmentNum < attachments.length(); attachmentNum++) {
                            JSONObject attachmentInfo = attachments.getJSONObject(attachmentNum);
                            File attachmentLoc = new File(attachmentInfo.getString("file_file_name"));
                            URL attachmentUrl = new URL("https:" + attachmentInfo.getString("file_url"));
                            FileUtils.copyURLToFile(attachmentUrl, attachmentLoc);
                            CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean(attachmentLoc,
                                    attachmentInfo.getString("file_file_name"),
                                    attachmentInfo.getString("file_content_type"),
                                    user, issue.getIssue(), false, false, null,
                                    myDate, true);
                            try {
                                attachmentManager.createAttachment(bean);
                            } catch (AttachmentException ex) {
                            }
                        }
                    }
                }
            }
        }
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