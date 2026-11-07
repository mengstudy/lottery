package com.lottery.analyzer.util

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticRetentionTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun oldCrashFilesAreBoundedWithoutDeletingUnrelatedFiles(){
        val dir=temporary.newFolder()
        repeat(25){i->java.io.File(dir,"crash_$i.log").apply{writeText("x".repeat(100));setLastModified(1000L+i)}}
        val unrelated=java.io.File(dir,"user-data.txt").apply{writeText("keep")}
        DiagnosticRetention.prune(dir,maxFiles=20,maxBytes=350)
        assertEquals(3,dir.listFiles()!!.count{it.extension=="log"});assertTrue(unrelated.exists())
        assertTrue(java.io.File(dir,"crash_24.log").exists())
    }
}
