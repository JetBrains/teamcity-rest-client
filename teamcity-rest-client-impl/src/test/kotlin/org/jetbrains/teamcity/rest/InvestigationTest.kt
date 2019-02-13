package org.jetbrains.teamcity.rest

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
    fun test_forProject() {
        val filteredInvestigations = publicInstance().investigations().forProject(ProjectId("Kotlin")).all()
        val allInvestigations = publicInstance().investigations().all()
        Assert.assertTrue(filteredInvestigations.count() > 0)
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())
    }

    @Test
    fun test_withType() {
        val allInvestigations = publicInstance().investigations().all()

        var filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.TEST).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all{it.targetType == InvestigationTargetType.TEST})
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())

        filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_PROBLEM).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all{it.targetType == InvestigationTargetType.BUILD_PROBLEM})
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())

        filteredInvestigations = publicInstance().investigations().withTargetType(InvestigationTargetType.BUILD_CONFIGURATION).all()
        Assert.assertTrue(filteredInvestigations.any())
        Assert.assertTrue(filteredInvestigations.all{it.targetType == InvestigationTargetType.BUILD_CONFIGURATION})
        Assert.assertTrue(filteredInvestigations.count() < allInvestigations.count())
    }

    @Test
    fun test_all() {
        val investigations = publicInstance().investigations().all()
        Assert.assertTrue(investigations.count() > 0)
    }
}
