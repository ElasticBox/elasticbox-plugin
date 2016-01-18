package com.elasticbox.jenkins.repository.api;

import com.elasticbox.APIClient;
import com.elasticbox.jenkins.model.box.AbstractBox;
import com.elasticbox.jenkins.model.box.policy.PolicyBox;
import com.elasticbox.jenkins.model.box.script.ScriptBox;
import com.elasticbox.jenkins.model.error.ElasticBoxModelException;
import com.elasticbox.jenkins.repository.BoxRepository;
import com.elasticbox.jenkins.repository.api.criteria.CloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.repository.api.criteria.NoCloudFormationPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.repository.api.criteria.NoPolicyAndNoApplicationBoxes;
import com.elasticbox.jenkins.repository.api.criteria.NoPolicyBoxesJSONCriteria;
import com.elasticbox.jenkins.repository.api.factory.box.GenericBoxFactory;
import com.elasticbox.jenkins.repository.api.factory.box.PolicyBoxFactory;
import com.elasticbox.jenkins.repository.api.factory.box.ScriptBoxFactory;
import com.elasticbox.jenkins.repository.error.RepositoryException;
import com.elasticbox.jenkins.util.ClientCache;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Created by serna on 11/26/15.
 */
    public class BoxRepositoryAPIImpl implements BoxRepository {


    private APIClient client;

    public BoxRepositoryAPIImpl(APIClient client) {
        this.client = client;
    }

    public List<AbstractBox> getNoPolicyAndNoApplicationBoxes(String workspace) throws RepositoryException {
        try {
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            return  new NoPolicyAndNoApplicationBoxes().fits(boxesFromAPI);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RepositoryException("Error retrieving no policies and no application boxes from API, workspace: "+workspace);
        }

    }

    /**
     * Returns the boxes that are not policy boxes
     * @return
     */
    @Override
    public List<AbstractBox> getNoPolicyBoxes(String workspace) throws RepositoryException {
        try {
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            return new NoPolicyBoxesJSONCriteria().fits(boxesFromAPI);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RepositoryException("Error retrieving no policies boxes from API, workspace: "+workspace);
        }
    }

    /**e
     * Returns only the cloudformation template policy boxes
     * @return
     */
    @Override
    public List<PolicyBox> getCloudFormationPolicyBoxes(String workspace) throws RepositoryException {
        try{
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            List<PolicyBox> policyBoxes = new CloudFormationPolicyBoxesJSONCriteria().fits(boxesFromAPI);
            return policyBoxes;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RepositoryException("Error retrieving cloudformation policies boxes from API, workspace: "+workspace);
        }
    }

    /**
     * Returns the rest of policy boxes that are not cloud formation policy boxes
     * @return
     */
    @Override
    public List<PolicyBox> getNoCloudFormationPolicyBoxes(String workspace) throws RepositoryException {
        try{
            JSONArray boxesFromAPI = client.getAllBoxes(workspace);
            List<PolicyBox> policyBoxes = new NoCloudFormationPolicyBoxesJSONCriteria().fits(boxesFromAPI);
            return policyBoxes;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RepositoryException("Error retrieving no cloudformation policies boxes from API, workspace: "+workspace);
        }
    }

    @Override
    public AbstractBox getBox(String boxId) throws RepositoryException {
        try{
            JSONObject json = client.getBox(boxId);
            final AbstractBox box = new GenericBoxFactory().create(json);
            return box;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RepositoryException("Error retrieving box: "+boxId+" from API");
        } catch (ElasticBoxModelException e) {
            e.printStackTrace();
            throw new RepositoryException("Error converting box: "+boxId+" from JSON");
        }
    }




}