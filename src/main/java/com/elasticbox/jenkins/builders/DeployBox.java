/*
 * ElasticBox Confidential
 * Copyright (c) 2014 All Right Reserved, ElasticBox Inc.
 *
 * NOTICE:  All information contained herein is, and remains the property
 * of ElasticBox. The intellectual and technical concepts contained herein are
 * proprietary and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law. Dissemination of this
 * information or reproduction of this material is strictly forbidden unless prior
 * written permission is obtained from ElasticBox.
 */

package com.elasticbox.jenkins.builders;

import com.elasticbox.Client;
import com.elasticbox.IProgressMonitor;
import com.elasticbox.jenkins.ElasticBoxCloud;
import com.elasticbox.jenkins.ElasticBoxItemProvider;
import com.elasticbox.jenkins.ElasticBoxSlaveHandler;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Project;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * @author Phong Nguyen Le
 */
public class DeployBox extends Builder implements IInstanceProvider {
    private final String id;
    private final String workspace;
    private final String box;
    private final String profile;
    private final String environment;
    private final int instances;
    private final String variables;
    
    private transient String instanceId;

    @DataBoundConstructor
    public DeployBox(String id, String workspace, String box, String profile, int instances, String environment, String variables) {
        super();
        assert id != null && id.startsWith(DeployBox.class.getName() + '-');
        this.id = id;
        this.workspace = workspace;
        this.box = box;
        this.profile = profile;
        this.instances = instances;
        this.environment = environment;
        this.variables = variables;
    }
    
    protected JSONArray resolveVariables(JSONArray variables, List<IInstanceProvider> instanceProviders) {
        for (Object json : variables) {
            JSONObject variable = (JSONObject) json;
            String value = variable.getString("value");
            if ("Binding".equals(variable.getString("type")) && value.startsWith("com.elasticbox.jenkins.builders.")) {
                for (IInstanceProvider instanceProvider : instanceProviders) {
                    if (value.equals(instanceProvider.getId())) {
                        variable.put("value", instanceProvider.getInstanceId());
                    }
                }                
            }
        }
        return variables;
    }    
    
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {       
        ElasticBoxCloud cloud = ElasticBoxCloud.getInstance();
        if (cloud == null) {
            throw new IOException("No ElasticBox cloud is configured.");
        }
        JSONArray jsonVariables = JSONArray.fromObject(variables);
        List<IInstanceProvider> instanceProviders = new ArrayList<IInstanceProvider>();
        for (Object builder : ((Project) build.getProject()).getBuilders()) {
            if (builder instanceof IInstanceProvider) {
                instanceProviders.add((IInstanceProvider) builder);
            }
        }
        for (Object json : jsonVariables) {
            JSONObject variable = (JSONObject) json;
            String value = variable.getString("value");
            if ("Binding".equals(variable.getString("type")) && value.startsWith("com.elasticbox.jenkins.builders.")) {
                for (IInstanceProvider instanceProvider : instanceProviders) {
                    if (value.equals(instanceProvider.getId())) {
                        variable.put("value", instanceProvider.getInstanceId());
                    }
                }                
            }
            
            if (variable.getString("scope").isEmpty()) {
                variable.remove("scope");
            }
        }        
        IProgressMonitor monitor = cloud.createClient().deploy(profile, workspace, environment, instances, jsonVariables);
        String instancePageUrl = Client.getPageUrl(cloud.getEndpointUrl(), monitor.getResourceUrl());
        listener.getLogger().println(MessageFormat.format("Deploying box instance {0}", instancePageUrl));
        listener.getLogger().println(MessageFormat.format("Waiting for the deployment of the box instance {0} to finish", instancePageUrl));
        try {
            monitor.waitForDone(ElasticBoxSlaveHandler.TIMEOUT_MINUTES);
            listener.getLogger().println(MessageFormat.format("The box instance {0} has been deployed successfully ", instancePageUrl));
            instanceId = Client.getResourceId(monitor.getResourceUrl());
            return true;
        } catch (IProgressMonitor.IncompleteException ex) {
            Logger.getLogger(DeployBox.class.getName()).log(Level.SEVERE, null, ex);
            listener.error("Failed to deploy box instance %s: %s", instancePageUrl, ex.getMessage());
            throw new AbortException(ex.getMessage());
        }
    }

    public String getId() {
        return id;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getBox() {
        return box;
    }

    public String getProfile() {
        return profile;
    }  

    public int getInstances() {
        return instances;
    }
    
    public String getEnvironment() {
        return environment;
    }        

    public String getVariables() {
        return variables;
    }

    public String getInstanceId() {
        return instanceId;
    }
    
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        private final ElasticBoxItemProvider itemProvider = new ElasticBoxItemProvider();

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "ElasticBox - Deploy Box";
        }

        @Override
        public Builder newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            if (formData.getString("profile").trim().length() == 0) {
                throw new FormException("Profile is required to launch a box in ElasticBox", "profile");
            }
            
            try {
                int instances = formData.getInt("instances");
                if (instances < 1) {
                    throw new FormException("Number of instances must be a positive number to launch a box in ElasticBox", "instances");
                }
            } catch (JSONException ex) {
                throw new FormException(ex.getMessage(), "instances");
            }
            
            if (formData.getString("environment").trim().length() == 0) {
                throw new FormException("Enviroment is required to launch a box in ElasticBox", "environment");
            }                        
            
            return super.newInstance(req, formData);
        }                

        public ListBoxModel doFillWorkspaceItems() {
            return itemProvider.getWorkspaces();
        }
        
        public ListBoxModel doFillBoxItems(@QueryParameter String workspace) {
            return itemProvider.getBoxes(workspace);
        }

        public ListBoxModel doFillProfileItems(@QueryParameter String workspace, @QueryParameter String box) {                
            return itemProvider.getProfiles(workspace, box);
        }
        
        public ElasticBoxItemProvider.JSONArrayResponse doGetBoxStack(@QueryParameter String profile) {
            return itemProvider.getProfileBoxStack(profile);
        }
        
        public ElasticBoxItemProvider.JSONArrayResponse doGetInstances(@QueryParameter String workspace, @QueryParameter String box) {
            return itemProvider.getInstancesAsJSONArrayResponse(workspace, box);
        }
    }
}