package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.GeminiModels
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class JarvisRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserProfile_andObserveProfile_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = JarvisRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile("Alice", ALICE_EMAIL, null)
            val profile = repo.observeUserProfile(uid).first { it != null }
            assertNotNull(profile)
            assertEquals("Alice", profile!!.displayName)
            assertEquals(
                GeminiModels.normalizeFreeTierModel(profile.preferredModel),
                profile.preferredModel
            )

            repo.updateProfileSettings(preferredModel = GeminiModels.FLASH_LITE, speechRate = 1.25)
            val updated = repo.observeUserProfile(uid).first { it?.preferredModel == GeminiModels.FLASH_LITE }
            assertEquals(1.25, updated!!.speechRate, 0.01)

            repo.updateProfileSettings(preferredModel = GeminiModels.FLASH)
            val reset = repo.observeUserProfile(uid).first { it?.preferredModel == GeminiModels.FLASH }
            assertEquals(GeminiModels.FLASH, reset!!.preferredModel)
        }
    }

    @Test
    fun createConversationAndMessages_realtimeObservation_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = JarvisRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            val convId = repo.createConversation("Physics Chat", GeminiModels.FLASH)
            assertTrue(convId.isNotBlank())

            val msgId = repo.addMessage(
                conversationId = convId,
                role = "user",
                text = "Explain relativity",
                clientOrder = 1000L
            )
            assertTrue(msgId.isNotBlank())

            val convs = repo.observeConversations(uid).first { list -> list.any { it.id == convId } }
            assertTrue(convs.any { it.id == convId && it.title == "Physics Chat" })

            val msgs = repo.observeMessages(uid, convId).first { list -> list.any { it.id == msgId } }
            assertEquals(1, msgs.size)
            assertEquals("Explain relativity", msgs.first().text)
        }
    }

    @Test
    fun addAndDeleteMemory_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = JarvisRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            val memId = repo.addMemory("preference", "Prefers Kotlin over Java")
            val memories = repo.observeMemories(uid).first { list -> list.any { it.id == memId } }
            assertTrue(memories.any { it.id == memId && it.category == "preference" })

            repo.deleteMemory(memId)
            val afterDelete = repo.observeMemories(uid).first { list -> list.none { it.id == memId } }
            assertTrue(afterDelete.none { it.id == memId })
        }
    }

    @Test
    fun crossUserAccess_observeConversations_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = JarvisRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createConversation("Alice Secret", GeminiModels.FLASH)
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = JarvisRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeConversations(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's conversations")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                firestoreException!!.code
            )
        }
    }

    @Test
    fun unauthenticatedUser_observeMemories_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = JarvisRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMemories("some_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(
                FirebaseFirestoreException.Code.PERMISSION_DENIED,
                firestoreException!!.code
            )
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice@jarvistest.com"
        const val BOB_EMAIL = "bob@jarvistest.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
