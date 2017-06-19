package org.jetbrains.teamcity.rest

import junit.framework.TestCase.*
import org.junit.Before
import org.junit.Test

class BranchRuleTest {
    // +|-:logical branch name
    private val branchName = "BranchName"

    @Before
    fun setupLog4j() { setupLog4jDebug() }

    @Test
    fun test_branchRule_without_sign() {
        val branchRule = BranchRuleImpl("$branchName")

        assertEquals(branchRule.rule, "$branchName")
        assertTrue(branchRule.include)
        assertEquals(branchRule.name, branchName)
    }

    @Test
    fun test_branchRule_with_include_sign() {
        val branchRule = BranchRuleImpl("+:$branchName")

        assertEquals(branchRule.rule, "+:$branchName")
        assertTrue(branchRule.include)
        assertEquals(branchRule.name, branchName)
    }

    @Test
    fun test_branchRule_with_exclude_sign() {
        val branchRule = BranchRuleImpl("-:$branchName")

        assertEquals(branchRule.rule, "-:$branchName")
        assertFalse(branchRule.include)
        assertEquals(branchRule.name, branchName)
    }
}
