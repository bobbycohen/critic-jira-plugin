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
//import com.google.gson.Gson;
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
//import java.util.logging.Logger;
//import java.util.logging.Level;

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
        //List<String> issueTypes = constantsManager.getAllIssueTypeIds();
        Collection<IssueType> issueTypes = constantsManager.getRegularIssueTypeObjects();
        //Collection<IssueType> issueTypes = constantsManager.getAllIssueTypeObjects();
        Map<String, Object> context = new HashMap<>();
        resp.setContentType("text/html;charset=utf-8");
        switch (action) {
            case "new":
                //ConstantsManager.Collection<IssueType> issueTypes = constantsManager.getAllIssueTypeObjects();
                //List<String> allIssueTypes = constantsManager.getAllIssueTypeIds();
                context.put("issueTypes", issueTypes);
                //context.put("issueTypes2", issueTypes2);
                templateRenderer.render(NEW_ISSUE_TEMPLATE, context, resp.getWriter());
                break;
            case "edit":
                IssueService.IssueResult issueResult = issueService.getIssue(authenticationContext.getLoggedInUser(),
                        req.getParameter("key"));
                context.put("issue", issueResult.getIssue());
                //ConstantsManager.Collection<IssueType> issueTypes = constantsManager.getAllIssueTypeObjects();
                //List<String> allIssueTypes = constantsManager.getAllIssueTypeIds();
                context.put("issueTypes", issueTypes);
                //context.put("issueTypes2", issueTypes2);
                templateRenderer.render(EDIT_ISSUE_TEMPLATE, context, resp.getWriter());
                break;
            default:
                List<Issue> issues = getIssues();
                context.put("issues", issues);
                //ConstantsManager.Collection<IssueType> issueTypes = constantsManager.getAllIssueTypeObjects();
                //List<String> allIssueTypes = constantsManager.getAllIssueTypeIds();
                context.put("issueTypes", issueTypes);
                //context.put("issueTypes2", issueTypes2);
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

    /**
     * Retrieve issue types using JQL query project="DEMO"
     *
     * @return Collection of IssueTypes
     */
    //private Collection<IssueType> getIssueTypes() {
//
//        ApplicationUser user = authenticationContext.getLoggedInUser();
//        JqlClauseBuilder jqlClauseBuilder = JqlQueryBuilder.newClauseBuilder();
//        Query query = jqlClauseBuilder.project("DEMO").buildQuery();
//        PagerFilter pagerFilter = PagerFilter.getUnlimitedFilter();
//
//        return
//    }

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
            //java.util.logging.Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            //java.util.logging.Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Exception ex) {
                    //java.util.logging.Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
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

    public void downloadToFile(File f, String url) {
        //URL downloadUrl = null;
        try {
            URL downloadUrl = new URL(url);
            FileUtils.copyURLToFile(downloadUrl, f);
        } catch (MalformedURLException ex) {
            //java.util.logging.Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            //java.util.logging.Logger.getLogger(getClass().getName()).log(Level.SEVERE, null, ex);
        }
        //downloadUrl = null;
        return;
    }

    private void handleIssueCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        ApplicationUser user = authenticationContext.getLoggedInUser();

        Map<String, Object> context = new HashMap<>();

        //URL url = new URL("https://critic.inventiv.io/api/v2/bug_reports?app_api_token=2PUeap7YiZKucEofuCHRJap5");
        //HttpURLConnection con = (HttpURLConnection) url.openConnection();
        //con.setRequestMethod("GET");
        //int status = con.getResponseCode();
        String data = getJSON("https://critic.inventiv.io/api/v2/bug_reports?app_api_token=2PUeap7YiZKucEofuCHRJap5", 4000);
        //CriticIssue issue = new Gson().fromJson(data, CriticIssue.class);
        JSONObject dataObj = new JSONObject(data);
        int count = dataObj.getInt("count");

        //String imageUrl = "https://dezov.s3.amazonaws.com/media/stick-figure-png5a0-4d0c-b092-85ebd63534c3.png";
        //URL imageUrl = new URL("https://dezov.s3.amazonaws.com/media/stick-figure-png5a0-4d0c-b092-85ebd63534c3.png");
        //File f = new File("image.png");
        //File[] f = null;
        //URL downloadUrl = new URL("https://dezov.s3.amazonaws.com/media/stick-figure-png5a0-4d0c-b092-85ebd63534c3.png");
        //downloadToFile(f, "https://s3.amazonaws.com/inventiv-critic-web-production/attachments/files/000/000/449/original/logcat6734813367933620968.txt?1555263267");
        //FileUtils.copyURLToFile(imageUrl, f);
        //imageUrl = null;
        //JSONArray array = obj1.getJSONArray("bug_reports");
        //JSONObject obj2 = array.getJSONObject(0);

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
        JSONArray bugReports = dataObj.getJSONArray("bug_reports");
        JSONObject reportObj = null;
        String description = null;
        JSONObject metadata = null;
        JSONArray metadataNames = null;
        String fieldName = null;
        String details = null;
        //String moreData2 = null;
        JSONObject bugDetails = null;
        JSONArray attachments = null;
        JSONObject attachment = null;
        //URL attachmentUrl = null;
        File f = null;
        CreateAttachmentParamsBean bean = null;
        Date myDate = new Date();
        //URL attachmentUrl = new URL("poo");
        //for (int i = 0; i < count; i++) {
        for (int i = 0; i < 2; i++) {
            //downloadToFile(f, "https://s3.amazonaws.com/inventiv-critic-web-production/attachments/files/000/000/449/original/logcat6734813367933620968.txt?1555263267");
            //attachmentUrl = null;
            reportObj = bugReports.getJSONObject(i);
            details = getJSON("https://critic.inventiv.io/api/v2/bug_reports/" + reportObj.getInt("id") + "?app_api_token=2PUeap7YiZKucEofuCHRJap5", 4000);
            bugDetails = new JSONObject(details);
            //moreData2 = getJSON("https://s3.amazonaws.com/inventiv-critic-web-production/attachments/files/000/000/414/original/logcat5459445908258442240.txt?1554921565", 4000);
            issueInputParameters = issueService.newIssueInputParameters();
            description = "ID: " + reportObj.getInt("id")
                          + "\nDescription: " + reportObj.getString("description")
                          + "\nCreated at: " + reportObj.getString("created_at")
                          + "\nUpdated at: " + reportObj.getString("updated_at");
            if (!reportObj.isNull("steps_to_reproduce")) {
                description = description + "\nSteps to Reproduce: " + reportObj.get("steps_to_reproduce");
            }
            if (!reportObj.isNull("user_identifier")) {
                description = description + "\nUser Identifier: " + reportObj.get("user_identifier");
            }
            if (!reportObj.isNull("metadata")) {
                metadata = reportObj.getJSONObject("metadata");
                metadataNames = metadata.names();
                for (int j = 0; j < metadata.length(); j++) {
                    fieldName = metadataNames.getString(j);
                    description = description + "\n" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1)
                                  + ": " + metadata.get(fieldName);
                }
            }
            //f = new File[bugDetails.getJSONArray("attachments").length()];
            //for (int k = 0; k < bugDetails.getJSONArray("attachments").length(); k++) {
            //    f[k] = new File(bugDetails.getJSONArray("attachments").getJSONObject(k).getString("file_file_name"));
            //    downloadToFile(f[k], "https:" + bugDetails.getJSONArray("attachments").getJSONObject(k).getString("file_url"));
            //}
            //downloadToFile(f, "https://s3.amazonaws.com/inventiv-critic-web-production/attachments/files/000/000/449/original/logcat6734813367933620968.txt?1555263267");
            description = description + "\n\n" + details;
            //List<Issue> myIssues = getIssues();
            //Issue issue = myIssues.get(0);

            //CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean(f, "stick-figure-png5a0-4d0c-b092-85ebd63534c3.png", "image/png", user, issue, false, false, null, myDate, true);
            //description = issue.getSummary();
            //try {
            //    attachmentManager.createAttachment(bean);
            //} catch (AttachmentException ex) {
            //    description = ex.getMessage();
            //}

            issueInputParameters.setSummary(reportObj.getString("description"))
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
                //List<Issue> issues = getIssues();
                //Issue issue = issues.get(0);
                IssueService.IssueResult issue = issueService.create(user, result);
                attachments = bugDetails.getJSONArray("attachments");

                for (int j = 0; j < attachments.length(); j++) {
                    attachment = attachments.getJSONObject(j);
                    //attachmentUrl2 = new URL("poo");
                    //f = null;
                    //f = new File(attachment.getString("file_file_name"));
                    //downloadToFile(f, "https:" + attachment.getString("file_file_url"));
                    f = new File(attachment.getString("file_file_name"));
                    URL newUrl = new URL("https:" + attachment.getString("file_url"));
                    FileUtils.copyURLToFile(newUrl, f);
                    //FileUtils.copyURLToFile(attachmentUrl, f);
                    bean = new CreateAttachmentParamsBean(f, attachment.getString("file_file_name"),
                                                          attachment.getString("file_content_type"),
                                                          user, (Issue) issue.getIssue(), false, false, null,
                                                          myDate, true);
                    try {
                        attachmentManager.createAttachment(bean);
                    } catch (AttachmentException ex) {
                        description = ex.getMessage();
                    }
                }
                //issueService.create(user, result);
                //JSONObject moreDataObj = new JSONObject(moreData);
                //JSONArray attachments = moreDataObj.getJSONArray("attachments");
                //Map<String, Object> imgProps = toMap(attachments.getJSONObject(0));
                //Date myDate = new Date();
                //CreateAttachmentParamsBean bean = new CreateAttachmentParamsBean(f, "stick-figure-png5a0-4d0c-b092-85ebd63534c3.png", "image/png", user, issue, false, false, null, myDate, true);
                //attachmentManager.createAttachment(bean);
            }
        }
        resp.sendRedirect("issuecrud");

        //IssueInputParameters issueInputParameters = issueService.newIssueInputParameters();
        //issueInputParameters.setSummary(req.getParameter("summary"))
        //        .setDescription(req.getParameter("description") + "\nlinkhere\n" + data + "number" + count + "array" + obj2)
        //        .setAssigneeId(user.getName())
        //        .setReporterId(user.getName())
        //        .setProjectId(project.getId())
        //        .setIssueTypeId(req.getParameter("type"))
        //        .setPriorityId(req.getParameter("priority"));

        //IssueService.CreateValidationResult result = issueService.validateCreate(user, issueInputParameters);

        //if (result.getErrorCollection().hasAnyErrors()) {
        //    List<Issue> issues = getIssues();
        //    context.put("issues", issues);
        //    context.put("errors", result.getErrorCollection().getErrors());
        //    resp.setContentType("text/html;charset=utf-8");
        //    templateRenderer.render(LIST_ISSUES_TEMPLATE, context, resp.getWriter());
        //} else {
        //    issueService.create(user, result);
        //    resp.sendRedirect("issuecrud");
        //}
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