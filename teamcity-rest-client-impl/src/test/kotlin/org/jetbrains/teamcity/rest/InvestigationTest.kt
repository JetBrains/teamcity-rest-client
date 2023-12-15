package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class InvestigationTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_limit() {
        val investigations = publicInstance().investigations().limitResults(3).all()
        Assert.assertEquals(investigations.count(), 3)
        investigations.forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun test_assignee() {
        var hasAtLeastOneAssigneeName = false
        var hasAtLeastOneReporterName = false
        val investigations = publicInstance().investigations().limitResults(10).all().toList()
        for (investigation in investigations) {
            Assert.assertNotNull(investigation.assignee.id)
            Assert.assertNotNull(investigation.assignee.username)
            if (investigation.assignee.name != null) {
                hasAtLeastOneAssigneeName = true
            }

            if (investigation.reporter?.name != null) {
                hasAtLeastOneReporterName = true
            }
        }
        Assert.assertTrue(hasAtLeastOneAssigneeName)
        Assert.assertTrue(hasAtLeastOneReporterName)

        runBlocking {
            var hasAtLeastOneAssigneeNameAsync = false
            var hasAtLeastOneReporterNameAsync = false
            val investigationsAsync = publicCoroutinesInstance().investigations().limitResults(10).all().toList()
            for (investigation in investigationsAsync) {
                val assignee = publicCoroutinesInstance().user(investigation.assignee)
                Assert.assertNotNull(assignee.getUsername())
                if (assignee.getName() != null) {
                    hasAtLeastOneAssigneeNameAsync = true
                }
                val reporter = investigation.reporter?.let { publicCoroutinesInstance().user(it) }
                Assert.assertNotNull(reporter?.getUsername())
                if (reporter?.getName() != null) {
                    hasAtLeastOneReporterNameAsync = true
                }
            }
            Assert.assertTrue(hasAtLeastOneAssigneeNameAsync)
            Assert.assertTrue(hasAtLeastOneReporterNameAsync)
            assertEqualsAnyOrder(investigations.map { it.id.stringId }, investigationsAsync.map { it.id.stringId })
        }
    }

    @Test
    fun test_reporter() {
        var hasAtLeastOneReporter = false
        val investigations = publicInstance().investigations().limitResults(10).all()
        for (investigation in investigations) {
            if (investigation.reporter != null) {
                hasAtLeastOneReporter = true
                Assert.assertNotNull(investigation.reporter?.id)
                Assert.assertNotNull(investigation.reporter?.username)
            }
        }

        Assert.assertTrue(hasAtLeastOneReporter)
    }

    @Test
    fun test_forProject() {
        val filteredInvestigations = publicInstance().investigations().forProject(ProjectId("ProjectForSidebarCounters")).all()
        val allInvestigations = publicInstance().investigations().all()
        Assert.assertTrue(filteredInvestigations.count() > 0)
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())
    }

    @Test
    fun test_assignmentProjectForTestInvestigations() {
        val investigations = publicInstance().investigations().withTargetType(InvestigationTargetType.TEST).all()
        Assert.assertTrue(investigations.all { inv -> inv.scope is InvestigationScope.InProject })
    }

    @Test
    fun test_assignmentProjectForBuildProblemsInvestigations() {
        val investigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_PROBLEM).all()
        Assert.assertTrue(investigations.all { inv -> inv.scope is InvestigationScope.InProject })
    }

    @Test
    fun test_assignmentProjectForBuildType() {
        val investigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_CONFIGURATION).all()
        Assert.assertTrue(investigations.all { inv -> inv.scope is InvestigationScope.InBuildConfiguration })
    }

    @Test
    fun test_withType() {
        val allInvestigations = publicInstance().investigations().all()

        var filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.TEST).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all { it.targetType == InvestigationTargetType.TEST })
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())

        filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_PROBLEM).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all { it.targetType == InvestigationTargetType.BUILD_PROBLEM })
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())

        filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_CONFIGURATION).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all { it.targetType == InvestigationTargetType.BUILD_CONFIGURATION })
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())
    }

    @Test
    fun test_all() {
        val investigations = publicInstance().investigations().all()
        Assert.assertTrue(investigations.count() > 0)
    }
}
