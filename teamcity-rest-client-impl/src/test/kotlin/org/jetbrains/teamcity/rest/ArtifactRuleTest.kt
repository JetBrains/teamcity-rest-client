package org.jetbrains.teamcity.rest

import junit.framework.TestCase.*
import org.jetbrains.teamcity.rest.async.ArtifactRuleImpl
import org.junit.Before
import org.junit.Test

class ArtifactRuleTest {
    // [+:|-:]SourcePath[!ArchivePath][=>DestinationPath]
    private val sourcePath = "SourcePath"
    private val archivePath = "ArchivePath"
    private val destinationPath = "DestinationPath"

    @Before
    fun setupLog4j() { setupLog4jDebug() }

    @Test
    fun test_include_parameter() {
        var artifactRule = ArtifactRuleImpl("$sourcePath=>$destinationPath")
        assertTrue(artifactRule.include)

        artifactRule = ArtifactRuleImpl("+:$sourcePath")
        assertTrue(artifactRule.include)

        artifactRule = ArtifactRuleImpl("-:$sourcePath!$archivePath")
        assertFalse(artifactRule.include)
    }

    @Test
    fun test_sourcePath_parameter() {
        var artifactRule = ArtifactRuleImpl("-:$sourcePath")
        assertEquals(artifactRule.sourcePath, sourcePath)

        artifactRule = ArtifactRuleImpl("+:$sourcePath!$archivePath")
        assertEquals(artifactRule.sourcePath, sourcePath)

        artifactRule = ArtifactRuleImpl("+:$sourcePath=>$destinationPath")
        assertEquals(artifactRule.sourcePath, sourcePath)

        artifactRule = ArtifactRuleImpl("$sourcePath!$archivePath=>$destinationPath")
        assertEquals(artifactRule.sourcePath, sourcePath)
    }

    @Test
    fun test_archivePath_parameter() {
        var artifactRule = ArtifactRuleImpl("-:$sourcePath")
        assertNull(artifactRule.archivePath)

        artifactRule = ArtifactRuleImpl("$sourcePath!$archivePath")
        assertEquals(artifactRule.archivePath, archivePath)

        artifactRule = ArtifactRuleImpl("$sourcePath=>$destinationPath")
        assertNull(artifactRule.archivePath)

        artifactRule = ArtifactRuleImpl("+:$sourcePath!$archivePath=>$destinationPath")
        assertEquals(artifactRule.archivePath, archivePath)
    }

    @Test
    fun test_destinationPath_parameter() {
        var artifactRule = ArtifactRuleImpl("+:$sourcePath")
        assertNull(artifactRule.destinationPath)

        artifactRule = ArtifactRuleImpl("-:$sourcePath!$archivePath")
        assertNull(artifactRule.destinationPath)

        artifactRule = ArtifactRuleImpl("-:$sourcePath=>$destinationPath")
        assertEquals(artifactRule.destinationPath, destinationPath)

        artifactRule = ArtifactRuleImpl("$sourcePath!$archivePath=>$destinationPath")
        assertEquals(artifactRule.destinationPath, destinationPath)
    }

}

