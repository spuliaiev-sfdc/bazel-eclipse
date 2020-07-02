package com.salesforce.bazel.eclipse.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.osgi.service.prefs.BackingStoreException;

import com.salesforce.bazel.sdk.command.BazelCommandManager;
import com.salesforce.bazel.sdk.model.BazelLabel;
import com.salesforce.bazel.sdk.model.BazelProject;
import com.salesforce.bazel.sdk.model.BazelProjectTargets;

public interface ProjectPreferencesManager {

	// TODO these are not project preferences, we should rename this class 
	String getBazelExecutablePath();
	void setBazelExecutablePathListener(BazelCommandManager bazelCommandManager);
	String getBazelWorkspacePath();
	void setBazelWorkspacePath(String bazelWorkspacePath);

	/**
	 * The label that identifies the Bazel package that represents this project. This will
	 * be the 'module' label when we start supporting multiple BUILD files in a single 'module'.
	 * Example:  //projects/libs/foo
	 * See https://github.com/salesforce/bazel-eclipse/issues/24
	 */
	String getBazelLabelForProject(BazelProject bazelProject);

	/**
	 * Returns a map that maps Bazel labels to their projects
	 */
	Map<BazelLabel, BazelProject> getBazelLabelToProjectMap(Collection<BazelProject> bazelProjects);

	/**
	 * List the Bazel targets the user has chosen to activate for this Eclipse project. Each project configured 
	 * for Bazel is configured to track certain targets and this function fetches this list from the project preferences.
	 * After initial import, this will be just the wildcard target (:*) which means all targets are activated. This
	 * is the safest choice as new targets that are added to the BUILD file will implicitly get picked up. But users
	 * may choose to be explicit if one or more targets in a BUILD file is not needed for development.
	 * <p>
	 * By contract, this method will return only one target if the there is a wildcard target, even if the user does
	 * funny things in their prefs file and sets multiple targets along with the wildcard target.
	 */
	BazelProjectTargets getConfiguredBazelTargets(BazelProject bazelProject, boolean addWildcardIfNoTargets);

	/**
	 * List of Bazel build flags for this Eclipse project, taken from the project configuration
	 */
	List<String> getBazelBuildFlagsForProject(BazelProject bazelProject);

	void addSettingsToProject(BazelProject bazelProject, String bazelWorkspaceRoot, String bazelProjectLabel,
			List<String> bazelTargets, List<String> bazelBuildFlags) throws BackingStoreException;

}