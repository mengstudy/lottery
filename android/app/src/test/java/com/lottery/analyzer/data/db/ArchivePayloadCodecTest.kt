package com.lottery.analyzer.data.db

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.lottery.analyzer.data.ai.AiAnalysisFactsBuilder
import com.lottery.analyzer.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class ArchivePayloadCodecTest {
    private val groups = listOf(listOf(1, 2, 3, 4, 5, 6) to listOf(1), listOf(7, 9) to emptyList())
    private fun facts(): SafeAiArchiveFacts {
        val draw = LotteryRecord("2026001", "2026-01-01", (1..6).toList(), listOf(1))
        val context = AiAnalysisFactsBuilder.prepare(LotteryType.SSQ, listOf(draw), "2026002", AiWindowOptions())
        return SafeAiArchiveFacts.from(AiRecommendationRequest(AiConfig(apiKey = "NEVER_STORE_THIS"),
            LotteryType.SSQ, context.analysis, "2026002", AiAnalysisMode.FULL_RECOMMEND, setOf(7, 9),
            topOtherBalls = listOf(1 to 3, 9 to 0), topOtherBlues = listOf(1 to 2), otherCompleteBets = groups,
            windowContext = context.windowContext))
    }

    @Test fun groupsUseExplicitVersionAndNamesAndRoundTripWithoutBusinessReflection() {
        val encoded = ArchivePayloadCodec.encodeGroups(groups)
        val root = JsonParser.parseString(encoded).asJsonObject
        assertEquals(2, root.get("payload_schema_version").asInt)
        assertEquals(setOf("reds", "blues"), root.getAsJsonArray("groups")[0].asJsonObject.keySet())
        assertEquals(groups, ArchivePayloadCodec.decodeGroups(encoded))
        assertEquals(emptyList<Pair<List<Int>, List<Int>>>(), ArchivePayloadCodec.decodeGroups("[]"))
    }

    @Test fun existingUnversionedFirstSecondGroupsAreReadable() {
        assertEquals(groups, ArchivePayloadCodec.decodeGroups(Gson().toJson(groups)))
        assertEquals(listOf(listOf(1, 33) to listOf(16)), ArchivePayloadCodec.decodeGroups("""[{"first":[1,33],"second":[16]}]"""))
    }

    @Test fun legacyFactsNormalizeToExactlyTheSameCompleteV2Facts() {
        val value = facts()
        val legacy = Gson().toJson(value)
        val current = ArchivePayloadCodec.encodeFacts(value)
        assertEquals(JsonParser.parseString(current), ArchivePayloadCodec.decodeFacts(legacy))
        assertEquals(JsonParser.parseString(current), ArchivePayloadCodec.decodeFacts(current))
        val root = JsonParser.parseString(current).asJsonObject
        assertEquals(Gson().toJsonTree(value.analysis), root.get("analysis"))
        assertEquals(Gson().toJsonTree(value.windowContext), root.get("windowContext"))
        assertEquals(setOf("number", "count"), root.getAsJsonArray("otherRedFrequency")[0].asJsonObject.keySet())
        assertFalse(current.contains("NEVER_STORE_THIS")); assertFalse(current.contains("apiKey"))
    }

    @Test fun legacyHashMatchesActualOldGsonBytesIncludingEscapingAndNullPlay() {
        val snapshot = CollectionSnapshot(LotteryType.SSQ, "2026002", "<原文>&='\"", groups, "来源<>&'")
        val oldHash = MessageDigest.getInstance("SHA-256").digest(Gson().toJson(snapshot).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(oldHash, ArchivePayloadCodec.legacyCollectionHash(snapshot))
        assertEquals(ArchivePayloadCodec.collectionHash(snapshot), ArchivePayloadCodec.collectionHash(snapshot.copy()))
        assertNotEquals(oldHash, ArchivePayloadCodec.collectionHash(snapshot))
    }

    @Test fun oldAndNewPromptEnvelopesAreReadable() {
        val prompt = AiPromptContent("system <>&", "用户内容")
        assertEquals(prompt, ArchivePayloadCodec.decodePrompt(Gson().toJson(prompt)))
        assertEquals(prompt, ArchivePayloadCodec.decodePrompt(ArchivePayloadCodec.encodePrompt(prompt)))
    }

    @Test fun unknownVersionsAndNonIntegerNumbersFailInsteadOfBeingGuessedOrTruncated() {
        invalid { ArchivePayloadCodec.decodeGroups("""{"payload_schema_version":99,"groups":[]}""") }
        invalid { ArchivePayloadCodec.decodeFacts("""{"payload_schema_version":99}""") }
        invalid { ArchivePayloadCodec.decodeGroups("""[{"first":[1.5],"second":[]}]""") }
        invalid { ArchivePayloadCodec.decodeGroups("""[{"first":["1"],"second":[]}]""") }
    }
    private fun invalid(block: () -> Unit) {
        try { block(); fail("Expected unsupported/corrupted archive") } catch (_: IllegalArgumentException) { }
    }
}
