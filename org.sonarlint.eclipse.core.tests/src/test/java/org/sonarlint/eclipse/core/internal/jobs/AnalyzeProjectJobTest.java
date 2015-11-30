/*
 * SonarLint for Eclipse
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.eclipse.core.internal.jobs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sonar.runner.api.Issue;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.SonarLintNature;
import org.sonarlint.eclipse.core.internal.markers.MarkerUtils;
import org.sonarlint.eclipse.core.internal.utils.StringUtils;
import org.sonarlint.eclipse.tests.common.SonarTestCase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class AnalyzeProjectJobTest extends SonarTestCase {

  public org.junit.rules.ExternalResource test = null;
  private IProject project;
  private static final List<String> errors = new ArrayList<>();

  @BeforeClass
  public static void prepare() throws Exception {

    SonarLintCorePlugin.getDefault().addLogListener(new LogListener() {

      @Override
      public void info(String msg) {
      }

      @Override
      public void error(String msg) {
        errors.add(msg);
      }

      @Override
      public void debug(String msg) {
      }
    });
  }

  @Before
  public void cleanup() throws Exception {
    errors.clear();
    project = importEclipseProject("reference");
    MarkerUtils.deleteIssuesMarkers(project);

    // Enable Sonar Nature
    SonarLintNature.enableNature(project);
  }

  @After
  public void checkErrorsInLog() throws Exception {
    project.delete(true, null);
    if (!errors.isEmpty()) {
      fail(StringUtils.joinSkipNull(errors, "\n"));
    }
  }

  private static AnalyzeProjectJob job(IProject project) {
    return new AnalyzeProjectJob(new AnalyzeProjectRequest(project, null));
  }

  @Test
  public void run_first_analysis_with_one_issue() throws Exception {
    AnalyzeProjectJob job = job(project);
    IFile file = project.getFile("src/Findbugs.java");
    job = spy(job);
    Map<IResource, List<Issue>> result = new HashMap<>();
    Issue issue1 = Issue.builder()
      .setRuleKey("foo:bar")
      .setSeverity("BLOCKER")
      .setMessage("Self assignment of field")
      .setStartLine(5)
      .setStartLineOffset(4)
      .setEndLine(5)
      .setEndLineOffset(14)
      .setComponentKey("my-fake-project:key:src/Findbugs.java")
      .build();
    result.put(file, Arrays.asList(issue1));
    doReturn(result).when(job).run(any(Properties.class), any(SonarRunnerFacade.class), eq(MONITOR));
    job.runInWorkspace(MONITOR);

    IMarker[] markers = file.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
    assertThat(markers).hasSize(1);
    assertThat(markers[0].getAttribute(IMarker.LINE_NUMBER)).isEqualTo(5);
    assertThat(markers[0].getAttribute(IMarker.CHAR_START)).isEqualTo(78);
    assertThat(markers[0].getAttribute(IMarker.CHAR_END)).isEqualTo(88);
    assertThat(markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CHECKSUM_ATTR)).isEqualTo("this.x=x".hashCode());
    String timestamp = (String) markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CREATION_DATE_ATTR);
    assertThat(timestamp).isNotNull();
  }

  @Test
  public void issue_tracking() throws Exception {
    AnalyzeProjectJob job = job(project);
    IFile file = project.getFile("src/Findbugs.java");
    job = spy(job);
    Map<IResource, List<Issue>> result = new HashMap<>();
    Issue issue1 = Issue.builder()
      .setRuleKey("foo:bar")
      .setSeverity("BLOCKER")
      .setMessage("Self assignment of field")
      .setStartLine(5)
      .setStartLineOffset(4)
      .setEndLine(5)
      .setEndLineOffset(14)
      .setComponentKey("my-fake-project:key:src/Findbugs.java")
      .build();
    result.put(file, Arrays.asList(issue1));
    doReturn(result).when(job).run(any(Properties.class), any(SonarRunnerFacade.class), eq(MONITOR));
    job.runInWorkspace(MONITOR);
    IMarker[] markers = file.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
    assertThat(markers).hasSize(1);
    String timestamp = (String) markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CREATION_DATE_ATTR);

    // Second execution same file, same issue
    job.runInWorkspace(MONITOR);

    markers = file.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
    assertThat(markers).hasSize(1);
    assertThat(markers[0].getAttribute(IMarker.LINE_NUMBER)).isEqualTo(5);
    assertThat(markers[0].getAttribute(IMarker.CHAR_START)).isEqualTo(78);
    assertThat(markers[0].getAttribute(IMarker.CHAR_END)).isEqualTo(88);
    assertThat(markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CHECKSUM_ATTR)).isEqualTo("this.x=x".hashCode());
    assertThat(markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CREATION_DATE_ATTR)).isEqualTo(timestamp);

    InputStream is = file.getContents();
    java.util.Scanner s = new java.util.Scanner(is, file.getCharset()).useDelimiter("\\A");
    String content = s.hasNext() ? s.next() : "";
    content = "\n\n" + content;
    file.setContents(new ByteArrayInputStream(content.getBytes(file.getCharset())), true, true, null);

    markers = file.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
    // Here marker was not notified of the file change
    assertThat(markers[0].getAttribute(IMarker.LINE_NUMBER)).isEqualTo(5);

    // Third execution with modified file content
    result = new HashMap<>();
    Issue issue1Updated = Issue.builder()
      .setRuleKey("foo:bar")
      .setSeverity("BLOCKER")
      .setMessage("Self assignment of field")
      .setStartLine(7)
      .setStartLineOffset(4)
      .setEndLine(7)
      .setEndLineOffset(14)
      .setComponentKey("my-fake-project:key:src/Findbugs.java")
      .build();
    result.put(file, Arrays.asList(issue1Updated));
    doReturn(result).when(job).run(any(Properties.class), any(SonarRunnerFacade.class), eq(MONITOR));
    job.runInWorkspace(MONITOR);

    markers = file.findMarkers(SonarLintCorePlugin.MARKER_ID, true, IResource.DEPTH_INFINITE);
    assertThat(markers).hasSize(1);
    assertThat(markers[0].getAttribute(IMarker.LINE_NUMBER)).isEqualTo(7);
    assertThat(markers[0].getAttribute(IMarker.CHAR_START)).isEqualTo(80);
    assertThat(markers[0].getAttribute(IMarker.CHAR_END)).isEqualTo(90);
    assertThat(markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CHECKSUM_ATTR)).isEqualTo("this.x=x".hashCode());
    assertThat(markers[0].getAttribute(MarkerUtils.SONAR_MARKER_CREATION_DATE_ATTR)).isEqualTo(timestamp);
  }

}
