package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class MuteTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_assignment() {
        var hasAtLeastOneComment = false
        var hasAtLeastOneAssignee = false
        val mutes = publicInstance().mutes().limitResults(10).all()
        for (mute in mutes) {
            with(mute) {
                if(assignee != null) {
                    Assert.assertNotNull(assignee!!.id)
                    Assert.assertNotNull(assignee!!.username)
                    hasAtLeastOneAssignee = true
                }
                if (comment.isNotEmpty()) {
                    hasAtLeastOneComment = true
                }
            }
        }

        Assert.assertTrue("No mutes with non-empty comment found", hasAtLeastOneComment)
        Assert.assertTrue("No mutes with assignee found", hasAtLeastOneAssignee)
    }

    @Test
    fun test_mutedTests() {
        var hasAtLeastOneTest = false
        val mutes = publicInstance().mutes().limitResults(10).all()
        for (mute in mutes) {
            val mutedTests = mute.tests ?: continue
            mutedTests.forEach { test ->
                if(test != null) {
                    Assert.assertNotNull("Test id is not set for the muted test", test.id)
                    Assert.assertNotNull("Test name is not set for the muted test", test.name)
                    hasAtLeastOneTest = true
                }
            }
        }

        Assert.assertTrue("No mutes with referenced tests were found", hasAtLeastOneTest)
    }

    @Test
    fun test_reporter() {
        var hasAtLeastOneReporter = false
        val mutes = publicInstance().mutes().limitResults(10).all()
        for (mute in mutes) {
            if (mute.reporter != null) {
                hasAtLeastOneReporter = true
                Assert.assertNotNull("Reporter id is not set in the mute", mute.reporter?.id)
                Assert.assertNotNull("Reporter username is not set in the mute", mute.reporter?.username)
            }
        }

        Assert.assertTrue("No mutes with referenced reporter were found", hasAtLeastOneReporter)
    }

    @Test
    fun test_all() {
        val mutes = publicInstance().mutes().all().toList()
        Assert.assertTrue("Zero mutes were found on the instance", mutes.isNotEmpty())

        val mutesAsync = runBlocking { publicCoroutinesInstance().mutes().all().toList() }
        Assert.assertTrue("Zero mutes were found on the instance", mutesAsync.isNotEmpty())

        assertEqualsAnyOrder(mutes.map { it.id.stringId }, mutesAsync.map { it.id.stringId })
    }

    @Test
    fun test_forProject() {
        val filteredMutes = publicInstance().mutes().forProject(mutesProject).all()
        val allMutes = publicInstance().mutes().all()
        Assert.assertTrue("No filtered mutes were found, whereas expected", filteredMutes.count() > 0)
        Assert.assertTrue("Number of filtered mutes is more or same as all mutes", filteredMutes.count() < allMutes.count())
    }

}
