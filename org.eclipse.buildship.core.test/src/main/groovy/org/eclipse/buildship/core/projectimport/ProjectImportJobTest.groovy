package org.eclipse.buildship.core.projectimport

import com.gradleware.tooling.toolingclient.GradleDistribution

import com.google.common.collect.ImmutableList
import org.eclipse.buildship.core.CorePlugin
import org.eclipse.buildship.core.configuration.GradleProjectBuilder
import org.eclipse.buildship.core.configuration.GradleProjectNature
import org.eclipse.buildship.core.test.fixtures.LegacyEclipseSpockTestHelper;
import org.eclipse.buildship.core.util.gradle.GradleDistributionWrapper
import org.eclipse.buildship.core.util.progress.AsyncHandler
import org.eclipse.buildship.core.util.variable.ExpressionUtils;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IWorkspace
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ProjectImportJobTest extends Specification {

    @Rule
    TemporaryFolder tempFolder

    def cleanup() {
        CorePlugin.workspaceOperations().deleteAllProjects(null)
    }

    def "Project import job creates a new project in the workspace"(boolean projectDescriptorExists) {
        setup:
        def applyJavaPlugin = false
        File projectLocation = newProject(projectDescriptorExists, applyJavaPlugin)
        ProjectImportJob job = newProjectImportJob(projectLocation)

        when:
        job.schedule()
        job.join()

        then:
        CorePlugin.workspaceOperations().findProjectByName(projectLocation.name).present

        where:
        projectDescriptorExists << [false, true]
    }

    def "Project descriptors should be created iff they don't already exist"(boolean applyJavaPlugin, boolean projectDescriptorExists, String descriptorComment) {
        setup:
        File rootProject = newProject(projectDescriptorExists, applyJavaPlugin)
        ProjectImportJob job = newProjectImportJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        new File(rootProject, '.project').exists()
        new File(rootProject, '.classpath').exists() == applyJavaPlugin
        CorePlugin.workspaceOperations().findProjectInFolder(rootProject, null).get().getComment() == descriptorComment

        where:
        applyJavaPlugin | projectDescriptorExists | descriptorComment
        false           | false                   | 'Project simple-project created by Buildship.' // the comment from the generated descriptor
        false           | true                    | 'original'                                     // the comment from the original descriptor
        true            | false                   | 'Project simple-project created by Buildship.'
        true            | true                    | 'original'
    }

    def "Imported projects always have Gradle builder and nature"(boolean projectDescriptorExists) {
        setup:
        File rootProject = newProject(projectDescriptorExists, false)
        ProjectImportJob job = newProjectImportJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        def project = CorePlugin.workspaceOperations().findProjectByName(rootProject.name).get()
        GradleProjectNature.INSTANCE.isPresentOn(project)
        project.description.buildSpec.find { it.getBuilderName().equals(GradleProjectBuilder.INSTANCE.ID) }

        where:
        projectDescriptorExists << [false, true]
    }

    def "Imported parent projects have filters to hide the content of the children and the build folders"() {
        setup:
        File rootProject = newMultiProject()
        ProjectImportJob job = newProjectImportJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        def filters = CorePlugin.workspaceOperations().findProjectByName(rootProject.name).get().getFilters()
        filters.length == 3
        (filters[0].fileInfoMatcherDescription.arguments as String).endsWith('subproject')
        (filters[1].fileInfoMatcherDescription.arguments as String).endsWith('build')
        (filters[2].fileInfoMatcherDescription.arguments as String).endsWith('.gradle')
    }

    def "Importing a project twice won't result in duplicate filters"() {
        setup:
        def workspaceOperations = CorePlugin.workspaceOperations()
        File rootProject = newMultiProject()

        when:
        ProjectImportJob job = newProjectImportJob(rootProject)
        job.schedule()
        job.join()

        workspaceOperations.deleteAllProjects(null)

        job = newProjectImportJob(rootProject)
        job.schedule()
        job.join()

        then:
        def filters = workspaceOperations.findProjectByName(rootProject.name).get().getFilters()
        filters.length == 3
        (filters[0].fileInfoMatcherDescription.arguments as String).endsWith('subproject')
        (filters[1].fileInfoMatcherDescription.arguments as String).endsWith('build')
        (filters[2].fileInfoMatcherDescription.arguments as String).endsWith('.gradle')
    }

    def "Can import deleted project located in default location"() {
        setup:
        def workspaceOperations = CorePlugin.workspaceOperations()
        def workspaceRootLocation = LegacyEclipseSpockTestHelper.workspace.root.location.toFile()
        def location = new File('projectname', workspaceRootLocation)
        location.mkdirs()

        def project = workspaceOperations.createProject("projectname", location, ImmutableList.of(), ImmutableList.of(), new NullProgressMonitor())
        project.delete(false, true, new NullProgressMonitor())

        when:
        ProjectImportJob job = newProjectImportJob(new File(workspaceRootLocation, "projectname"))
        job.schedule()
        job.join()

        then:
        workspaceOperations.allProjects.size() == 1
    }

    def "Can import project located in workspace folder and with custom root name"() {
        setup:
        File rootProject = newProjectWithCustomNameInWorkspaceFolder()
        ProjectImportJob job = newProjectImportJob(rootProject)

        when:
        job.schedule()
        job.join()

        then:
        LegacyEclipseSpockTestHelper.workspace.root.projects.length == 1
        def project = LegacyEclipseSpockTestHelper.workspace.root.projects[0]
        def locationExpression = ExpressionUtils.encodeWorkspaceLocation(project)
        def decodedLocation = ExpressionUtils.decode(locationExpression)
        rootProject.equals(new File(decodedLocation))

        cleanup:
        rootProject.deleteDir()
    }

    def newProject(boolean projectDescriptorExists, boolean applyJavaPlugin) {
        def root = tempFolder.newFolder('simple-project')
        new File(root, 'build.gradle') << (applyJavaPlugin ? 'apply plugin: "java"' : '')
        new File(root, 'settings.gradle') << ''
        new File(root, 'src/main/java').mkdirs()

        if (projectDescriptorExists) {
            new File(root, '.project') << '''<?xml version="1.0" encoding="UTF-8"?>
                <projectDescription>
                  <name>simple-project</name>
                  <comment>original</comment>
                  <projects></projects>
                  <buildSpec></buildSpec>
                  <natures></natures>
                </projectDescription>'''
            if (applyJavaPlugin) {
                new File(root, '.classpath') << '''<?xml version="1.0" encoding="UTF-8"?>
                    <classpath>
                      <classpathentry kind="con" path="org.eclipse.jdt.launching.JRE_CONTAINER"/>
                      <classpathentry kind="src" path="src/main/java"/>
                      <classpathentry kind="output" path="bin"/>
                    </classpath>'''
            }
        }
        root
    }

    def newMultiProject() {
        def rootProject = tempFolder.newFolder('multi-project')
        new File(rootProject, 'build.gradle') << ''
        new File(rootProject, 'settings.gradle') << 'include "subproject"'
        def subProject = new File(rootProject, "subproject")
        subProject.mkdirs()
        new File(subProject, 'build.gradle') << ''
        rootProject
    }

    def newProjectWithCustomNameInWorkspaceFolder() {
        IWorkspace workspace = LegacyEclipseSpockTestHelper.workspace
        IPath rootLocation = workspace.root.location
        def root = new File(rootLocation.toFile(), 'Bug472223')
        root.mkdirs()
        new File(root, 'build.gradle') << ''
        new File(root, 'settings.gradle') << "rootProject.name = 'my-project-name-is-different-than-the-folder'"
        root
    }

    def newProjectImportJob(File location) {
        ProjectImportConfiguration configuration = new ProjectImportConfiguration()
        configuration.gradleDistribution = GradleDistributionWrapper.from(GradleDistribution.fromBuild())
        configuration.projectDir = location
        configuration.applyWorkingSets = true
        configuration.workingSets = []
        new ProjectImportJob(configuration, AsyncHandler.NO_OP)
    }

}
