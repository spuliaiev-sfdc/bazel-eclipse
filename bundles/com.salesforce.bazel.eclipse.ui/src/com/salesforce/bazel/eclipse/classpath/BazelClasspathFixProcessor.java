/**
 * Copyright (c) 2021, Salesforce.com, Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */
package com.salesforce.bazel.eclipse.classpath;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.DefaultClasspathFixProcessor;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.swt.graphics.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.bazel.eclipse.component.ComponentContext;
import com.salesforce.bazel.sdk.index.CodeIndexEntry;
import com.salesforce.bazel.sdk.index.jvm.BazelJvmIndexClasspath;
import com.salesforce.bazel.sdk.index.jvm.JvmCodeIndex;

/**
 * Class to be implemented by contributors to the extension point
 * <code>org.eclipse.jdt.ui.classpathFixProcessors</code>.
 *
 * @since 3.4
 */
public class BazelClasspathFixProcessor extends DefaultClasspathFixProcessor {

    protected static class BazelClasspathFixProposal extends ClasspathFixProposal {

        public String fName;
        public Change fChange;
        public String fDescription;
        public int fRelevance;

        public BazelClasspathFixProposal(ClasspathFixProposal clone) {
            fName = clone.getDisplayString();
            fDescription = clone.getAdditionalProposalInfo();
            fRelevance = clone.getRelevance();

            try {
                fChange = clone.createChange(null);
            } catch (Exception anyE) {}
        }

        public BazelClasspathFixProposal(String name, Change change, String description, int relevance) {
            fName = name;
            fChange = change;
            fDescription = description;
            fRelevance = relevance;
        }

        @Override
        public Change createChange(IProgressMonitor monitor) {
            return fChange;
        }

        @Override
        public String getAdditionalProposalInfo() {
            return fDescription;
        }

        @Override
        public String getDisplayString() {
            return fName;
        }

        @Override
        public Image getImage() {
            return JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
        }

        @Override
        public int getRelevance() {
            return fRelevance;
        }
    }

    private static Logger LOG = LoggerFactory.getLogger(BazelClasspathFixProcessor.class);

    @Override
    public ClasspathFixProposal[] getFixImportProposals(IJavaProject javaProject, String missingType)
            throws CoreException {
        var iproject = javaProject.getProject();

        // super does some heavy lifting, it tracks down the missing type to the correct jar
        var proposals = super.getFixImportProposals(javaProject, missingType);

        if ((proposals.length == 0) || !ComponentContext.getInstance().getConfigurationManager().isGlobalClasspathSearchEnabled()) {
            // no point in trying to improve on the advice, since we dont have the search index
            return proposals;
        }

        // often Eclipse will see multiple paths to the same jar, which emits multiple proposals, just use the first
        var proposal = rewriteProposal(iproject, missingType, proposals[0]);

        return new ClasspathFixProposal[] { proposal };
    }

    /**
     * This method converts the raw file path to a jar file to the Bazel label that can be used to reference that
     * external dependency in a BUILD file. It does this by consulting the global type search index.
     */
    private ClasspathFixProposal rewriteProposal(IProject iproject, String missingType, ClasspathFixProposal proposal) {
        // Proposal display string will look like this:
        //    Add archive 'guava-23.0.jar - /private/var/tmp/xyz/external/maven/v1/https/repo1.maven.org/maven2/com/google/guava/guava/23.0'
        //    to build path of 'old-guava'
        var pText = proposal.getDisplayString();
        if (pText.startsWith("Add archive '")) {
            var endOfJar = pText.indexOf(" ", 14);
            var jarName = pText.substring(13, endOfJar);

            // get access to the underlying index data
            var searchContainer =
                    ComponentContext.getInstance().getGlobalSearchClasspathContainer();
            var indexCP = searchContainer.getIndexClasspath();
            var index = indexCP.getIndex(null);

            var indexEntry = index.fileDictionary.get(jarName);
            String bazelLabel = null;
            if (indexEntry != null) {
                if (indexEntry.singleLocation != null) {
                    bazelLabel = indexEntry.singleLocation.bazelLabel;
                } else if (indexEntry.multipleLocations.size() > 0) {
                    bazelLabel = indexEntry.multipleLocations.get(0).bazelLabel;
                }
                if (bazelLabel != null) {
                    LOG.debug("Found Bazel label {} for project {} and missingType {}", bazelLabel, iproject.getName(),
                        missingType);
                    var newProposal = new BazelClasspathFixProposal(proposal);
                    newProposal.fRelevance = 1;
                    newProposal.fName = "Bazel BUILD fix: add " + bazelLabel + " to the target in the ["
                            + iproject.getName() + "] project.";
                    proposal = newProposal;
                }
            }
        }
        return proposal;
    }
}