/* ============================================================================
 *
 * FILE: WikiSite.java
 *
 *
 * ============================================================================
 */
package org.jvnet.hudson.plugins.wikipublisher;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import hudson.util.FormValidation;
import hudson.util.Secret;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sourceforge.jwbf.core.actions.HttpActionClient;
import net.sourceforge.jwbf.core.actions.util.ActionException;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import org.apache.http.impl.client.DefaultHttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * @author nthomas
 * 
 */
public class WikiSite implements Describable<WikiSite>
{
	private static final Logger LOGGER = Logger.getLogger(WikiSite.class
			.getName());
	/**
	 * Base URL of the wiki site
	 */
	private final URL url;

	/**
	 * Username to login to the site
	 */
	public final String username;
	/**
	 * Password of the user
	 */
	public final Secret password;
	/**
	 * Domain of the user. null if no domain
	 */
	public final String domain;
	/**
	 * flag to determine whether to use a secure ssl channel. Necessary so that
	 * if the site is not verified by a company like Verisign, then it will be
	 * rejected.
	 */
	public final boolean secureSSL;

	@DataBoundConstructor
	public WikiSite(URL url, final String username, final String password,
			final String domain, final boolean ssl)
	{
		LOGGER.log(Level.FINER, "ctor args: " + url + ", " + username + ", "
				+ password);

		this.url = url;
		this.username = hudson.Util.fixEmptyAndTrim(username);
		this.password = Secret.fromString(password);
		this.domain = domain;
		this.secureSSL = ssl;
	}

	public String getName()
	{
		if(this.getUrl() == null)
			return null;
		
		return this.getUrl().getHost();
	}
	public MediaWikiBot connect() throws ActionException
	{
		MediaWikiBot b = new MediaWikiBot(getUrl());
		if (!secureSSL)
		{
			// if not a verified site, then accept it anyway
			b.setConnection(new HttpActionClient(WebClientDevWrapper
					.wrapClient(new DefaultHttpClient()), getUrl()));
		}
		if(this.domain == null || this.domain.isEmpty())
			b.login(username, Secret.toString(password));
		else
			b.login(this.username, Secret.toString(password), this.domain);
		return b;
	}
	
	@Override
	public String toString()
	{
		return this.getUrl().toString();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.model.Describable#getDescriptor()
	 */
	public Descriptor<WikiSite> getDescriptor()
	{
		return (DescriptorImpl) Hudson.getInstance().getDescriptorOrDie(
				getClass());
	}

	/**
	 * @return the url
	 */
	public URL getUrl()
	{
		return url;
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<WikiSite>
	{
		public DescriptorImpl()
		{
			super(WikiSite.class);
		}

		@Override
		public String getDisplayName()
		{
			return "Wiki Site";
		}

		/**
		 * Checks if the user name and password are valid.
		 */
		public FormValidation doLoginCheck(@QueryParameter String url,
				@QueryParameter String username,
				@QueryParameter String password, @QueryParameter String domain,
				@QueryParameter boolean ssl) throws IOException
		{
			url = hudson.Util.fixEmpty(url);
			if (url == null)
			{// URL not entered yet
				return FormValidation.ok();
			}
			username = hudson.Util.fixEmpty(username);
			password = hudson.Util.fixEmpty(password);
			domain = hudson.Util.fixEmpty(domain);
			if (username == null || password == null)
			{
				return FormValidation.warning("Enter username and password");
			}
			WikiSite site = new WikiSite(new URL(url), username, password,
					domain, ssl);
			try
			{
				site.connect();
				return FormValidation.ok("SUCCESS");
			} catch (ActionException e)
			{
				LOGGER.log(Level.WARNING, "Failed to login to Wiki site at "
						+ url, e);
				return FormValidation.error(e,
						"Failed to login. Reason: " + e.getLocalizedMessage());
			}
		}

		/**
		 * Checks if the Confluence URL is accessible.
		 */
		public FormValidation doUrlCheck(@QueryParameter final String url)
				throws IOException, ServletException
		{
			final String newurl = hudson.Util.fixEmpty(url);

			return new FormValidation.URLCheck()
			{
				@Override
				protected FormValidation check() throws IOException,
						ServletException
				{

					if (newurl == null)
					{
						return FormValidation.error("Enter a URL");
					}

					try
					{
						URL url = new URL(newurl);
						return FormValidation.ok();
					} catch (MalformedURLException e)
					{
						LOGGER.log(Level.WARNING,
								"Unable to connect to " + url, e);
						return FormValidation.error(e.getLocalizedMessage());
					}
				}
			}.check();
		}
	}
}
