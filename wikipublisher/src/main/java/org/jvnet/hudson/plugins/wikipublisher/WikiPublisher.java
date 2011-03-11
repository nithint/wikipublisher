/* ============================================================================
 *
 * FILE: WikiPublisher.java
 *
 * MODULE DESCRIPTION:
 * See class description
 *
 * Copyright (C) 2011 by
 * OCEUSNETWORKS INC
 * USA
 *
 * The program may be used and/or copied only with the written
 * permission from Oceus Networks Inc, or in accordance with
 * the terms and conditions stipulated in the agreement/contract
 * under which the program has been supplied.
 *
 * All rights reserved
 *
 * ============================================================================
 */
package org.jvnet.hudson.plugins.wikipublisher;

import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.json.JSONObject;
import net.sourceforge.jwbf.core.actions.util.ActionException;
import net.sourceforge.jwbf.core.actions.util.ProcessException;
import net.sourceforge.jwbf.core.contentRep.Article;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author nthomas
 * 
 */
public class WikiPublisher extends Notifier
{
	private static final Logger LOGGER = Logger.getLogger(WikiPublisher.class
			.getName());

	@Extension
	public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();

	public static final String QUERYSTRING_F = "?title=%s";
	private final String siteName;
	private final String pageName;

	@DataBoundConstructor
	public WikiPublisher(String siteName, final String pageName)
	{
		this.siteName = siteName;
		this.pageName = pageName;

		LOGGER.log(Level.FINER, "Data-bound: siteName={0}, pageName={1}",
				new Object[] { siteName, pageName });

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.tasks.BuildStep#getRequiredMonitorService()
	 */
	public BuildStepMonitor getRequiredMonitorService()
	{
		return BuildStepMonitor.BUILD;
	}

	@Override
	public DescriptorImpl getDescriptor()
	{
		return DESCRIPTOR;
	}

	/**
	 * @return the siteName
	 */
	public String getSiteName()
	{
		return siteName;
	}

	/**
	 * @return the pageName
	 */
	public String getPageName()
	{
		return pageName;
	}
	
	public WikiSite getSite()
	{
		WikiSite site = this.getDescriptor().getSiteByName(this.getSiteName());
		return site;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
			BuildListener listener) throws InterruptedException, IOException
	{
		WikiSite site = this.getSite();
		if (site == null)
		{
			// don't publish
			// continue build
			log(listener, "no wiki site setup to publish to");
			return true;
		}

		if (!Result.SUCCESS.equals(build.getResult()))
		{
			// Don't process for unsuccessful builds
			log(listener, "Not publishing results to wiki since build status is not SUCCESS ("
					+ build.getResult().toString() + ").");
			return true;
		}

		String text = getWikiFormattedBuildOutput(build);
		try
		{
			MediaWikiBot bot = site.connect();
			Article article = bot.readContent(this.getPageName());
			article.addText(text);
			article.save();
			log(listener, "Published build results to " + site.getUrl().toString() + 
					String.format(QUERYSTRING_F, this.getPageName()));
		} catch (ActionException e)
		{
			log(listener,
					"Error publishing to wiki for build "
							+ build.getDisplayName());
			log(listener, e.getLocalizedMessage());
			return false;
		} catch (ProcessException e)
		{
			log(listener, e.getLocalizedMessage());
			return false;
		}
		return true;
	}

	private String getWikiFormattedBuildOutput(AbstractBuild<?, ?> build)
	{
		if (build == null)
			return null;

		StringBuilder str = new StringBuilder(1024);
		// add section title
		str.append("\n\n== " + "[" + this.getBuildUrl(build) + " "
				+ build.getDisplayName() + "] == \n");
		// add upstream projects
		str.append("=== Upstream Builds ===\n");
		for (Map.Entry<AbstractProject, Integer> e : build.getUpstreamBuilds()
				.entrySet())
		{
			Run upstrBuild = e.getKey().getBuildByNumber(e.getValue());
			str.append("* " + e.getKey().getName() + "&nbsp;["
					+ this.getBuildUrl(upstrBuild) + " "
					+ upstrBuild.getDisplayName() + "]\n");
		}
		return str.toString();
	}

    /**
     * Returns the absolute URL to the build, if rootUrl has been configured.
     * If not, returns the build number.
     *
     * @param build a Jenkins run(build).
     * @return the absolute URL for this build, or the a string containing the
     *         build number.
     */
    private String getBuildUrl(Run<?, ?> build) {
        Hudson hudson = Hudson.getInstance();
        String rootUrl = hudson.getRootUrl();
        if (rootUrl == null) {
            return "Build " + build.getDisplayName();
        } else {
            return hudson.getRootUrl() + build.getUrl();
        }
    }  
    
	/**
	 * Log helper
	 * 
	 * @param listener
	 * @param message
	 */
	protected void log(BuildListener listener, String message)
	{
		listener.getLogger().println("[wikipublisher] " + message);
	}

	/**
	 * Descriptor for {@link WikiPublisher}. Used as a singleton. The class is
	 * marked as public so that it can be accessed from views.
	 */
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Publisher>
	{
		private static final Logger LOGGER = Logger
				.getLogger(DescriptorImpl.class.getName());

		private final List<WikiSite> sites = new ArrayList<WikiSite>();

		public DescriptorImpl()
		{
			super(WikiPublisher.class);
			load();
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName()
		{
			return "Publish build results with links to a Wiki";
		}

		public List<WikiSite> getSites()
		{
			LOGGER.log(Level.FINEST, "getSites: " + sites);
			return this.sites;
		}

		public WikiSite getSiteByName(String siteName)
		{
			for (WikiSite site : sites)
			{
				if (site.getName().equals(siteName))
				{
					return site;
				}
			}
			return null;
		}

		public void setSites(List<WikiSite> sites)
		{
			LOGGER.log(Level.FINER, "+setSites: " + this.sites);
			this.sites.clear();
			this.sites.addAll(sites);
			LOGGER.log(Level.FINER, "-setSites: " + this.sites);
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject json)
				throws FormException
		{
			LOGGER.log(Level.FINE, "Saving configuration from global! json: "
					+ json.toString());
			this.setSites(req.bindJSONToList(WikiSite.class, json.get("sites")));
			save();
			return super.configure(req, json);
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> arg0)
		{
			LOGGER.log(Level.FINEST, "in publisher, sites: " + sites);
			return sites != null && sites.size() > 0;
		}

		/**
		 * Check that the page exists. If not, create it. Also checks if the
		 * logged in user has permission to edit the page.
		 * 
		 * @param siteName
		 *            - wiki site
		 * @param pageName
		 *            - page on the wiki site to edit
		 * @return
		 */
		public FormValidation doPageNameCheck(
				@QueryParameter final String siteName,
				@QueryParameter final String pageName)
		{
			WikiSite site = this.getSiteByName(siteName);
			if (hudson.Util.fixEmptyAndTrim(pageName) == null)
				return FormValidation.ok();

			if (site == null)
				return FormValidation.error("Unknown site: " + siteName);

			try
			{
				// note that if the page doesn't exist, then it
				// will be created
				MediaWikiBot bot = site.connect();
				Article article = bot.readContent(pageName);
				article.save();
				return FormValidation.ok();
			} catch (ActionException e)
			{
				return FormValidation.error(e, e.getLocalizedMessage());
			} catch (ProcessException e)
			{
				return FormValidation.error(e, e.getLocalizedMessage());
			}
		}
	}
}
