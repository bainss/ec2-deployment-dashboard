package de.codecentric.jenkins.dashboard;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.ComboBoxModel;
import hudson.util.ListBoxModel;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;

import de.codecentric.jenkins.dashboard.api.environment.ServerEnvironment;
import de.codecentric.jenkins.dashboard.ec2.AwsKeyCredentials;
import de.codecentric.jenkins.dashboard.ec2.AwsRegion;
import de.codecentric.jenkins.dashboard.ec2.EC2Connector;

public class Environment extends AbstractDescribableImpl<Environment> {

    private final static Logger LOGGER = Logger.getLogger(Environment.class.getName());

    @Extension
    public static final EnvironmentDescriptor DESCRIPTOR = new EnvironmentDescriptor();

    private String name;
    private EnvironmentType environmentType;
    private String buildJob;
    private String awsInstance;
    private String region;
    private String credentials;

    @DataBoundConstructor
    public Environment(@Nonnull final String name, @Nonnull final String credentials, @Nonnull final String region, @Nonnull final String environmentType, final String awsInstance, final String buildJob) {
        LOGGER.info("New environment created: " + credentials + ", " + region);
    	setName(name);
        setCredentials(credentials);
        setRegion(region);
        setEnvironmentType(environmentType);
        setAwsInstance(awsInstance);
        setBuildJob(buildJob);
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getEnvironmentType() {
        return environmentType.name();
    }

    public void setEnvironmentType(final String environmentType) {
        this.environmentType = EnvironmentType.valueOf(environmentType);
    }

    public String getAwsInstance() {
        return awsInstance;
    }

    public void setAwsInstance(final String awsInstance) {
        this.awsInstance = awsInstance;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(final String region) {
        this.region = region;
    }

    public String getCredentials() {
        return credentials;
    }

    public void setCredentials(final String credentials) {
        this.credentials = credentials;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
    public Descriptor getDescriptor() {
        return DESCRIPTOR;
    }

    public String getBuildJob() {
        return buildJob;
    }

    public void setBuildJob(final String buildJob) {
        this.buildJob = buildJob;
    }

    @Extension
    public static class EnvironmentDescriptor extends Descriptor<Environment> {
        public String getDisplayName() {
            return Messages.Environment_DisplayName();
        }

        public ListBoxModel doFillEnvironmentTypeItems() {
            ListBoxModel model = new ListBoxModel();

            for (EnvironmentType value : EnvironmentType.values()) {
                model.add(value.getDescription(), value.name());
            }

            return model;
        }

        public ListBoxModel doFillCredentialsItems() {
        	final ListBoxModel model = new ListBoxModel();
        	
        	DomainRequirement domain = new DomainRequirement();
        	for (AwsKeyCredentials credentials : CredentialsProvider.lookupCredentials(AwsKeyCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, domain)) {
        		model.add(credentials.getId());
        	}
        	return model;
        }
        
        public ListBoxModel doFillAwsInstanceItems(@QueryParameter String region, @QueryParameter String credentials) {
            final ListBoxModel model = new ListBoxModel();

            LOGGER.info("Looking for instances in " + region);
            if (StringUtils.isBlank(region)) {
            	LOGGER.info("Region is empty");
            	return model;
            }
            for (ServerEnvironment env : getEC2Instances(region, credentials)) {
                model.add(env.getInstanceId());
            }

            return model;
        }

        private List<ServerEnvironment> getEC2Instances(String region, String credentialsId) {
        	final DomainRequirement domain = new DomainRequirement();
        	final AwsKeyCredentials credentials = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(AwsKeyCredentials.class, Jenkins.getInstance(), null, domain), CredentialsMatchers.withId(credentialsId));
        	if (credentials == null) {
        		LOGGER.warning("No credentials found for ID='" + credentialsId + "'");
        		return Collections.<ServerEnvironment>emptyList();
        	}
        	final EC2Connector ec2 = new EC2Connector(new AmazonEC2Client(credentials.getAwsAuthCredentials()));
        	return ec2.getEnvironments(Region.getRegion(Regions.fromName(region)));
		}

		public ListBoxModel doFillRegionItems() {
           final ListBoxModel model = new ListBoxModel();

            for (AwsRegion value : AwsRegion.values()) {
                model.add(value.getName(), value.getIdentifier());
            }

            return model;
        }
               
        public ComboBoxModel doFillBuildJobItems() {
            ComboBoxModel model = new ComboBoxModel();

            for (String jobName : Jenkins.getInstance().getJobNames()) {
                model.add(jobName);
            }

            return model;
        }

    }
}
