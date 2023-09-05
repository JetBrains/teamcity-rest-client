package org.jetbrains.teamcity.rest.parsing

import com.google.gson.Gson
import org.jetbrains.teamcity.rest.coroutines.BuildBean
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildBeanParsingTest {
    @Test
    fun `default branch`() {
        val build = Gson().fromJson("""
                {
                  "id" : 35288681,
                  "buildTypeId" : "ijplatform_master_Idea_Installers",
                  "number" : "183.2940",
                  "status" : "SUCCESS",
                  "state" : "finished",
                  "branchName" : "master",
                  "defaultBranch" : true
                }
        """.trimIndent(), BuildBean::class.java)!!
        assertTrue(build.defaultBranch!!)
    }

    @Test
    fun `personal build`() {
        val build = Gson().fromJson("""
                {
                  "id" : 12345678,
                  "buildTypeId" : "ijplatform_master_Idea_Installers",
                  "number" : "193.666",
                  "status" : "SUCCESS",
                  "state" : "finished",
                  "personal" : true,
                  "branchName" : "master",
                  "defaultBranch" : true
                }
        """.trimIndent(), BuildBean::class.java)!!
        assertTrue(build.personal!!)
    }
}